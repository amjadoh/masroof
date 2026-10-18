package com.baraa.masroof.lab.beta.application.evidence

import com.baraa.masroof.lab.beta.application.BankSelectionState
import com.baraa.masroof.lab.beta.application.BetaBankSelectionStore
import java.security.MessageDigest
import java.time.Clock
import kotlinx.coroutines.CancellationException

/** Durable single-interpretation intake. No ownership, reconciliation or ledger writes. */
class ProcessBetaEvidence(
    private val store: BetaEvidenceStore,
    private val selections: BetaBankSelectionStore,
    private val router: EvidenceRouter,
    private val comparer: EvidenceComparer,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun importBatch(request: EvidenceRequest, messages: List<EvidenceMessage>): List<EvidenceResult> {
        val frozenRequest = request.copy(historyBanks = request.historyBanks.toSet())
        val frozenMessages = messages.toList()
        val selected = selections.all().toList()
        return frozenMessages.map { message ->
            val id = identity(request.namespace, message.providerId)
            val existing = store.find(id)
            if (existing != null && (existing.message != message || existing.request.origin != frozenRequest.origin)) {
                EvidenceResult.IdentityConflict
            } else {
                val routed = router.route(message) // Full catalog first, never selection-filtered.
                val route = routed.copy(candidates = routed.candidates.toSet())
                val enabledCandidates = route.candidates.filter { bank ->
                    selected.any { it.bankId == bank && it.state == BankSelectionState.ENABLED }
                }
                val rejection = when {
                    route.kind == RouteKind.UNRECOGNIZED -> ScopeRejection.UNRECOGNIZED
                    route.kind == RouteKind.SUSPECTED -> if (request.origin == EvidenceOrigin.EXPLICIT_HISTORY)
                        ScopeRejection.UNPROVEN_HISTORY_SCOPE else null
                    enabledCandidates.isEmpty() -> ScopeRejection.NOT_ENABLED
                    request.origin == EvidenceOrigin.EXPLICIT_HISTORY && enabledCandidates.none { it in frozenRequest.historyBanks } -> ScopeRejection.OUTSIDE_REQUESTED_HISTORY
                    else -> null
                }
                if (rejection != null) EvidenceResult.NotCaptured(rejection)
                else if (existing != null) {
                    // Replaying saved evidence is intake too: scope precedes any implicit retry.
                    if (store.summary(id).retryStage != null) process(existing)
                    EvidenceResult.Stored(store.summary(id))
                } else {
                    val evidence = CapturedEvidence(id, frozenRequest, message, clock.instant(), route, selected)
                    when (store.capture(evidence)) {
                        CaptureState.CONFLICT -> EvidenceResult.IdentityConflict
                        else -> {
                            if (store.summary(id).retryStage != null) process(requireNotNull(store.find(id)))
                            EvidenceResult.Stored(store.summary(id))
                        }
                    }
                }
            }
        }
    }

    /** Saved evidence can be compared again after disabling a bank; its capture snapshot stays immutable. */
    suspend fun reprocess(id: String): EvidenceSummary {
        process(requireNotNull(store.find(id)) { "Unknown Beta evidence" })
        return store.summary(id)
    }

    suspend fun retryPending(limit: Int = 100): List<EvidenceSummary> {
        require(limit in 1..1000)
        return store.pending().take(limit).map { reprocess(it) }
    }

    suspend fun decide(id: String, expectedRevision: Long, decision: EvidenceReviewDecision): Boolean {
        require(expectedRevision >= 0)
        return store.decide(id, expectedRevision, decision, clock.instant())
    }

    private suspend fun process(evidence: CapturedEvidence) {
        var stage = EvidenceStage.INTERPRETATION
        try {
            store.begin(evidence.id, clock.instant()) // Committed before any derivation.
            val routed = router.route(evidence.message)
            val route = routed.copy(candidates = routed.candidates.toSet())
            val comparison = if (route.kind == RouteKind.MATCHED) comparer.compare(evidence.input(), route)
            else EvidenceComparison(EvidenceDisposition.REVIEW, setOf("ROUTE_${route.kind.name}"), "{}", "{}")
            stage = EvidenceStage.SAVE_OUTCOME
            store.complete(evidence.id, route, comparison.copy(reasons = comparison.reasons.toSet()), clock.instant())
        } catch (cancelled: CancellationException) {
            // The already committed marker survives cancellation/process death; do not swallow cancellation.
            throw cancelled
        } catch (_: Exception) {
            store.failure(evidence.id, stage, clock.instant()) // Safe fixed code; never exception/SMS text.
        }
    }

    companion object {
        fun identity(namespace: String, providerId: String): String {
            require(namespace.isNotBlank() && providerId.isNotBlank())
            val bytes = "${namespace.length}:$namespace${providerId.length}:$providerId".toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }
}
