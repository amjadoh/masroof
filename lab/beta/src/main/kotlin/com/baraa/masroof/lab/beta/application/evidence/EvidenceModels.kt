package com.baraa.masroof.lab.beta.application.evidence

import com.baraa.masroof.lab.bank.MessageInput
import com.baraa.masroof.lab.beta.application.UserBankSelection
import com.baraa.masroof.lab.finance.value.BankId
import java.time.Instant

enum class EvidenceOrigin { EXPLICIT_SAMPLE, EXPLICIT_HISTORY }
enum class ReceiptTimeSource { PROVIDER_TIMESTAMP, USER_SUPPLIED, DEVICE_FALLBACK }
enum class RouteKind { MATCHED, AMBIGUOUS, SUSPECTED, UNRECOGNIZED }
data class EvidenceRoute(val kind: RouteKind, val candidates: Set<BankId>, val routerVersion: String) {
    init {
        require(routerVersion.isNotBlank())
        require(when (kind) {
            RouteKind.MATCHED -> candidates.size == 1
            RouteKind.AMBIGUOUS -> candidates.size > 1
            else -> candidates.isEmpty()
        })
    }
}

/** Explicit messages only. No inbox query, receiver or permission is hidden in this request. */
data class EvidenceRequest(val namespace: String, val origin: EvidenceOrigin, val historyBanks: Set<BankId> = emptySet()) {
    init {
        require(namespace.isNotBlank())
        require((origin == EvidenceOrigin.EXPLICIT_HISTORY) == historyBanks.isNotEmpty())
    }
}

data class EvidenceMessage(val providerId: String, val sender: String, val body: String,
    val receivedAt: Instant, val timeSource: ReceiptTimeSource) {
    init { require(providerId.isNotBlank() && sender.isNotBlank()) }
    override fun toString() = "EvidenceMessage(redacted)"
}

data class CapturedEvidence(val id: String, val request: EvidenceRequest, val message: EvidenceMessage,
    val capturedAt: Instant, val route: EvidenceRoute, val selections: List<UserBankSelection>) {
    fun input() = MessageInput(id, message.sender, message.body, message.receivedAt)
    override fun toString() = "CapturedEvidence(redacted)"
}

enum class EvidenceDisposition { REVIEW, COMPARISON_ONLY, INTERPRETED }
enum class EvidenceProcessingState { RETRY, REVIEW, COMPARISON_ONLY, INTERPRETED }
enum class EvidenceStage { CAPTURED, INTERPRETATION, SAVE_OUTCOME }
enum class CaptureState { INSERTED, EXISTING, CONFLICT }
enum class ScopeRejection { UNRECOGNIZED, NOT_ENABLED, OUTSIDE_REQUESTED_HISTORY, UNPROVEN_HISTORY_SCOPE }
data class EvidenceSummary(val id: String, val disposition: EvidenceDisposition?, val retryStage: EvidenceStage?,
    val attempts: Int, val manualRevision: Long, val route: EvidenceRoute?, val reasonCodes: Set<String>,
    val manualDecision: EvidenceReviewDecision? = null) {
    /** Retry always wins over a retained previous outcome. This is never financial completion. */
    val processingState: EvidenceProcessingState get() = when {
        retryStage != null -> EvidenceProcessingState.RETRY
        disposition == EvidenceDisposition.REVIEW -> EvidenceProcessingState.REVIEW
        disposition == EvidenceDisposition.COMPARISON_ONLY -> EvidenceProcessingState.COMPARISON_ONLY
        disposition == EvidenceDisposition.INTERPRETED -> EvidenceProcessingState.INTERPRETED
        else -> error("Missing durable evidence outcome")
    }
}
sealed interface EvidenceResult {
    data class Stored(val summary: EvidenceSummary) : EvidenceResult
    data class NotCaptured(val reason: ScopeRejection) : EvidenceResult
    data object IdentityConflict : EvidenceResult
}

/** Review acknowledgement only, never a financial override or a guessed bank choice. */
enum class EvidenceReviewDecision { KEEP_FOR_REVIEW, ACKNOWLEDGE_COMPARISON }

/** Safe metadata only in summaries. Full archival interpretation JSON stays inside Beta storage. */
data class EvidenceComparison(val disposition: EvidenceDisposition, val reasons: Set<String>,
    val baselineJson: String, val candidateJson: String) {
    init { require(reasons.all { it.matches(Regex("[A-Z0-9_]{1,64}")) }) }
    override fun toString() = "EvidenceComparison(redacted)"
}

interface BetaEvidenceStore {
    suspend fun find(id: String): CapturedEvidence?
    /** Atomically inserts immutable raw evidence and CAPTURED retry marker. */
    suspend fun capture(evidence: CapturedEvidence): CaptureState
    suspend fun summary(id: String): EvidenceSummary
    /** Durable retry before interpretation; previous outcome/manual decision are retained. */
    suspend fun begin(id: String, at: Instant)
    /** Atomic replacement of comparison + retry clear. Never modifies manual decision. */
    suspend fun complete(id: String, route: EvidenceRoute, comparison: EvidenceComparison, at: Instant)
    suspend fun failure(id: String, stage: EvidenceStage, at: Instant)
    suspend fun pending(): List<String>
    suspend fun decide(id: String, expectedRevision: Long, decision: EvidenceReviewDecision, at: Instant): Boolean
}

fun interface EvidenceRouter { fun route(input: EvidenceMessage): EvidenceRoute }
fun interface EvidenceComparer { fun compare(input: MessageInput, route: EvidenceRoute): EvidenceComparison }
