package com.baraa.masroof.lab.beta.application.corrections

import androidx.room.withTransaction
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.Environment

/** Bound to persisted financial facts only. Unaccepted comparison candidates never enter this queue. */
data class BetaReviewToken(val key: PostingKey, val interpretation: Long, val correction: Long,
    val transition: Long, val evidence: Set<String>, val ledgerRevision: PostingRevision?)
data class BetaReviewDetail(val token: BetaReviewToken, val observation: CorrectionObservation,
    val current: BetaCorrectionView?, val stored: StoredFinancialPosting?)
data class BetaReviewItem(val key: PostingKey, val activity: BetaLedgerActivity?, val reasons: Set<ReviewReason>)

class BetaReviewWorkflow(private val database: BetaDatabase) {
    init { require(database.openHelper.databaseName == BetaDatabase.NAME) }

    /** Stable cursor, bounded union: suppressed/review-only rows remain reachable without fake postings. */
    suspend fun page(afterId: String = "", limit: Int = 25): List<BetaReviewItem> {
        require(limit in 1..100)
        return database.withTransaction {
            val ids = mutableListOf<String>()
            database.openHelper.writableDatabase.query(
                "SELECT transactionId FROM beta_ledger_current WHERE environment='BETA' AND transactionId > ? UNION SELECT transactionId FROM beta_correction_current WHERE environment='BETA' AND transactionId > ? ORDER BY transactionId LIMIT ?",
                arrayOf(afterId, afterId, limit)).use { cursor -> while (cursor.moveToNext()) ids += cursor.getString(0) }
            ids.map { id -> val key = PostingKey(Environment.BETA, id); val view = RoomBetaCorrectionLifecycle(database).read(key)
                if (adoptionHeld(key)) BetaReviewItem(key, BetaLedgerActivity.REVIEW, setOf(ReviewReason.INCOMPLETE_WRITE))
                else BetaReviewItem(key, view?.activity, view?.reasons.orEmpty()) }
        }
    }

    suspend fun detail(key: PostingKey): BetaReviewDetail? = database.withTransaction {
        require(key.environment == Environment.BETA)
        if (adoptionHeld(key)) return@withTransaction null
        val current = RoomBetaCorrectionLifecycle(database).read(key)
        val stored = RoomBetaFinancialLedger(database).readAudit(key)
        val observation = current?.overlay?.latestObservation ?: stored?.classification?.input?.let {
            CorrectionObservation(it.copy(revision = PostingRevision(0, it.revision.interpretation), authority = CorrectionAuthority.NONE), ObservationSafety.ORDINARY)
        } ?: return@withTransaction null
        BetaReviewDetail(BetaReviewToken(key, observation.input.revision.interpretation,
            current?.overlay?.correctionRevision ?: 0, current?.transitionRevision ?: 0,
            java.util.Collections.unmodifiableSet(LinkedHashSet(observation.input.evidenceIds)), stored?.command?.revision), observation, current, stored)
    }

    /** Re-read and write in one Room transaction: the UI cannot substitute an interpretation. */
    suspend fun save(token: BetaReviewToken, decisionId: String, decision: FinancialDecision,
        metadata: Map<String, String> = emptyMap()): BetaCorrectionResult {
        val frozenToken = token.copy(evidence = java.util.Collections.unmodifiableSet(LinkedHashSet(token.evidence)))
        val frozenMetadata = java.util.Collections.unmodifiableMap(LinkedHashMap(metadata))
        return saveFrozen(frozenToken, decisionId, decision, frozenMetadata)
    }

    private suspend fun saveFrozen(token: BetaReviewToken, decisionId: String, decision: FinancialDecision,
        metadata: Map<String, String>): BetaCorrectionResult = database.withTransaction {
        require(token.key.environment == Environment.BETA)
        if (adoptionHeld(token.key)) return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.STALE)
        database.correctionDao().decision("BETA", decisionId)?.let { audit ->
            val operation = BetaCorrectionCodec.decodeOperation(audit.operation)
            val request = requireNotNull(operation.request)
            if (request.key != token.key || request.expectedInterpretation != token.interpretation ||
                request.expectedCorrectionRevision != token.correction || request.expectedEvidenceIds != token.evidence ||
                request.decision != decision || request.metadata != metadata)
                return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.CONFLICT)
            return@withTransaction RoomBetaCorrectionLifecycle(database).submit(operation.observation, request)
        }
        val detail = detail(token.key) ?: return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.IDENTITY)
        if (detail.token != token) return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.STALE)
        val registry = BetaAssetRegistry(database).all().associateBy { it.id }
        val selected = detail.current?.overlay?.let { CorrectionSnapshots.export(it).financial }
            ?: detail.stored?.classification?.input ?: detail.observation.input
        val inputs = listOf(selected) + detail.stored?.observations.orEmpty().map { it.input }
        val expected = detail.observation.input.assets + inputs.flatMap { it.assets.entries }.associate { it.toPair() }
        val relevant = inputs.flatMap { listOfNotNull((it.source as? com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Owned)?.id,
            (it.destination as? com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Owned)?.id, it.financingLoan) }.toMutableSet()
        (decision as? FinancialDecision.EditFacts)?.let { edit ->
            listOfNotNull((edit.source as? com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Owned)?.id,
                (edit.destination as? com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Owned)?.id).forEach(relevant::add)
        }
        var frontier = relevant.toList()
        while (frontier.isNotEmpty()) {
            frontier = frontier.mapNotNull { expected[it]?.parentId }.filter { relevant.add(it) }
        }
        if (relevant.any { expected[it] == null || registry[it] != expected[it] })
            return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.STALE)
        val revision = Math.addExact(maxOf(token.correction, token.ledgerRevision?.correction ?: 0), 1)
        RoomBetaCorrectionLifecycle(database).submit(detail.observation, CorrectionRequest(decisionId, token.key, revision,
            token.interpretation, token.evidence, decision, metadata, token.correction))
    }

    /** Optional v7 capability: never resurrect an old ordinary ledger snapshot after held reparse.
     * Schema 6 remains standalone; unknown statuses and incompatible capability schemas fail closed.
     */
    private fun table(name: String): Boolean = database.openHelper.writableDatabase.query(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }

    private fun adoptionHeld(key: PostingKey): Boolean {
        val version = database.openHelper.writableDatabase.query("PRAGMA user_version").use { check(it.moveToFirst()); it.getInt(0) }
        if (version !in setOf(6, 7)) return true
        if (version == 7 && (!table("beta_financial_adoption") || !table("beta_financial_approval_retry"))) return true
        if (!table("beta_financial_adoption")) return false
        val sql = database.openHelper.writableDatabase
        val row = sql.query("SELECT evidenceId,status FROM beta_financial_adoption WHERE transactionId=?", arrayOf(key.transactionId)).use {
            if (!it.moveToFirst()) null else it.getString(0) to it.getString(1)
        } ?: return false
        if (row.second != "ACTIVE") return true
        if (table("beta_evidence_retry") && sql.query("SELECT 1 FROM beta_evidence_retry WHERE rawId=?", arrayOf(row.first)).use { it.moveToFirst() }) return true
        if (table("beta_financial_approval_retry") && sql.query("SELECT 1 FROM beta_financial_approval_retry WHERE evidenceId=?", arrayOf(row.first)).use { it.moveToFirst() }) return true
        return false
    }
}
