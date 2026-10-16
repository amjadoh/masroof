package com.baraa.masroof.lab.beta.data

import androidx.room.withTransaction
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.value.BankId
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Created for the current database per operation; never retained across restore/reset. */
internal class RoomBetaEvidenceStore(private val database: BetaDatabase) : BetaEvidenceStore {
    private val dao get() = database.evidenceDao()
    override suspend fun find(id: String) = dao.raw(id)?.toEvidence()

    override suspend fun capture(evidence: CapturedEvidence): CaptureState = database.withTransaction {
        val row = evidence.toEntity()
        val existing = dao.raw(row.id)
        if (existing != null) {
            if (existing.toEvidence().message == evidence.message && existing.origin == row.origin &&
                existing.namespace == row.namespace && existing.providerId == row.providerId) CaptureState.EXISTING
            else CaptureState.CONFLICT
        } else {
            check(dao.insertRaw(row) != -1L) { "Evidence identity collision" }
            dao.retry(BetaEvidenceRetryEntity(row.id, EvidenceStage.CAPTURED.name, 0, "CAPTURED_PENDING", row.capturedAt))
            CaptureState.INSERTED
        }
    }

    override suspend fun summary(id: String): EvidenceSummary = database.withTransaction {
        val raw = requireNotNull(dao.raw(id)) { "Unknown Beta evidence" }
        val outcome = dao.outcome(id)
        val retry = dao.retry(id)
        val decision = dao.decision(id)
        EvidenceSummary(id, outcome?.disposition?.let(EvidenceDisposition::valueOf),
            retry?.stage?.let(EvidenceStage::valueOf), retry?.attempts ?: outcome?.attempts ?: 0,
            decision?.revision ?: 0, decodeRoute(outcome?.route ?: raw.captureRoute),
            outcome?.reasons?.let(::strings)?.toSet().orEmpty() + listOfNotNull(retry?.code),
            decision?.decision?.let(EvidenceReviewDecision::valueOf))
    }

    override suspend fun begin(id: String, at: Instant) = database.withTransaction {
        requireNotNull(dao.raw(id)) { "Unknown Beta evidence" }
        val attempts = (dao.retry(id)?.attempts ?: dao.outcome(id)?.attempts ?: 0) + 1
        dao.retry(BetaEvidenceRetryEntity(id, EvidenceStage.INTERPRETATION.name, attempts, "INTERPRETATION_PENDING", at.toString()))
    }

    override suspend fun complete(id: String, route: EvidenceRoute, comparison: EvidenceComparison, at: Instant) = database.withTransaction {
        requireNotNull(dao.raw(id)) { "Unknown Beta evidence" }
        val pending = requireNotNull(dao.retry(id)) { "Missing durable attempt" }
        dao.outcome(BetaEvidenceOutcomeEntity(id, comparison.disposition.name, encodeRoute(route),
            JSONArray(comparison.reasons.sorted()).toString(), comparison.baselineJson, comparison.candidateJson,
            at.toString(), pending.attempts))
        dao.clearRetry(id)
    }

    override suspend fun failure(id: String, stage: EvidenceStage, at: Instant) = database.withTransaction {
        val pending = requireNotNull(dao.retry(id)) { "Missing durable attempt" }
        dao.retry(pending.copy(stage = stage.name, code = "${stage.name}_FAILED", updatedAt = at.toString()))
    }
    override suspend fun pending() = dao.pending()
    override suspend fun decide(id: String, expectedRevision: Long, decision: EvidenceReviewDecision, at: Instant) = database.withTransaction {
        requireNotNull(dao.raw(id)) { "Unknown Beta evidence" }
        if ((dao.decision(id)?.revision ?: 0L) != expectedRevision) false
        else {
            dao.decision(BetaEvidenceDecisionEntity(id, expectedRevision + 1, decision.name, at.toString()))
            true
        }
    }

    private fun CapturedEvidence.toEntity() = BetaRawEvidenceEntity(id, request.namespace, message.providerId,
        request.origin.name, message.sender, message.body, message.receivedAt.toString(), message.timeSource.name,
        capturedAt.toString(), encodeRoute(route), JSONArray(selections.map {
            JSONObject().put("bank", it.bankId.value).put("state", it.state.name)
        }).toString(), JSONArray(request.historyBanks.map { it.value }.sorted()).toString())

    private fun BetaRawEvidenceEntity.toEvidence() = CapturedEvidence(id,
        EvidenceRequest(namespace, EvidenceOrigin.valueOf(origin), strings(historyBanks).map(::BankId).toSet()),
        EvidenceMessage(providerId, sender, body, Instant.parse(receivedAt), ReceiptTimeSource.valueOf(timeSource)),
        Instant.parse(capturedAt), decodeRoute(captureRoute), JSONArray(captureSelections).let { array ->
            (0 until array.length()).map { index -> array.getJSONObject(index).let {
                UserBankSelection(BankId(it.getString("bank")), BankSelectionState.valueOf(it.getString("state")))
            } }
        })

    private fun encodeRoute(route: EvidenceRoute) = JSONObject().put("kind", route.kind.name)
        .put("routerVersion", route.routerVersion).put("candidates", JSONArray(route.candidates.map { it.value }.sorted())).toString()
    private fun decodeRoute(value: String) = JSONObject(value).let {
        EvidenceRoute(RouteKind.valueOf(it.getString("kind")), strings(it.getJSONArray("candidates").toString()).map(::BankId).toSet(),
            it.getString("routerVersion"))
    }
    private fun strings(value: String): List<String> = JSONArray(value).let { array ->
        (0 until array.length()).map(array::getString)
    }
}
