package com.baraa.masroof.lab.beta.verification

import android.content.Context
import androidx.room.Room
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.beta.application.BankSelectionState
import com.baraa.masroof.lab.beta.application.UserBankSelection
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.value.BankId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*

/** Real Beta storage/default engine on isolated JVM/emulator fixtures; no inbox access. */
object BetaBankScopeChecks {
    fun pauseResumeAndRestoreKeepHistory(context: Context) {
        BetaContainer.resetOffline(context)
        try {
            val bank = BankId("BANK_ALJAZIRA")
            val at = Instant.parse("2026-10-18T12:00:00Z")
            val message = EvidenceMessage("m4-captured", "AlJazira",
                "Credit card purchase\nCard: 1234\nAmount: 42 SAR\nMerchant: Synthetic Shop\nDate: 2026-10-18 12:00",
                at, ReceiptTimeSource.USER_SUPPLIED)
            val request = EvidenceRequest("m4-synthetic-history", EvidenceOrigin.EXPLICIT_HISTORY, setOf(bank))
            val id = ProcessBetaEvidence.identity(request.namespace, message.providerId)
            val canary = context.getDatabasePath(BetaEnvironment.PRODUCTION_DATABASE_NAME)
            canary.parentFile?.mkdirs(); canary.writeText("synthetic-m4-protected")
            val protected = canary.readBytes()
            val history = BetaContainer(context).use { beta ->
                beta.acknowledgeStart(); assertTrue(beta.bankChoices().all { it.state == null })
                beta.setBankEnabled(bank, true); beta.putMarker("m4", "retained")
                beta.startHistory(7, at.toEpochMilli())
            }
            // Simulate death after durable capture, before interpretation. No parser prediction is seeded.
            withDatabase(context) { db -> runBlocking {
                val store = RoomBetaEvidenceStore(db)
                assertEquals(CaptureState.INSERTED, store.capture(CapturedEvidence(id, request, message, at,
                    LabEvidenceRouter().route(message), listOf(UserBankSelection(bank, BankSelectionState.ENABLED)))))
                assertTrue(store.decide(id, 0, EvidenceReviewDecision.KEEP_FOR_REVIEW, at))
            } }
            BetaContainer(context).use { beta -> beta.setBankEnabled(bank, false); beta.backup() }
            val before = snapshot(context, id)
            BetaContainer(context).use { beta ->
                assertEquals(BankSelectionState.DISABLED, beta.bankChoices().single().state)
                assertEquals(history, beta.historySession())
                assertEquals(EvidenceResult.NotCaptured(ScopeRejection.NOT_ENABLED), beta.importEvidence(request, listOf(message)).single())
                assertEquals(0, beta.evidenceSummary(id).attempts)
            }
            assertEquals(before, snapshot(context, id))
            BetaContainer(context).use { beta ->
                beta.setBankEnabled(bank, true)
                val result = (beta.importEvidence(request, listOf(message)).single() as EvidenceResult.Stored).summary
                assertEquals(EvidenceProcessingState.INTERPRETED, result.processingState)
                assertEquals(1, result.attempts); assertEquals(1, result.manualRevision)
                assertEquals(EvidenceReviewDecision.KEEP_FOR_REVIEW, result.manualDecision)
                val duplicate = (beta.importEvidence(request, listOf(message)).single() as EvidenceResult.Stored).summary
                assertEquals(result, duplicate)
                assertEquals(history, beta.historySession()); assertEquals(1, beta.markerCount())
                assertTrue(beta.assets().isEmpty())
                assertNull(beta.financialPosting(RoomBetaFinancialAdoption.key(id)))
            }
            withDatabase(context) { db -> runBlocking {
                val outcome = requireNotNull(db.evidenceDao().outcome(id))
                assertEquals("{}", outcome.baselineJson)
                assertEquals("BANK_ENGINE", JSONObject(outcome.candidateJson).getString("authority"))
            } }
            BetaContainer(context).use { beta -> assertTrue(beta.restore()) }
            BetaContainer(context).use { beta ->
                assertEquals(BankSelectionState.DISABLED, beta.bankChoices().single().state)
                assertEquals(history, beta.historySession()); assertEquals(1, beta.markerCount())
                assertEquals(EvidenceStage.CAPTURED, beta.evidenceSummary(id).retryStage)
            }
            assertEquals(before, snapshot(context, id))
            assertArrayEquals(protected, canary.readBytes())
        } finally { BetaContainer.resetOffline(context) }
    }

    private fun snapshot(context: Context, id: String): List<Any?> = withDatabase(context) { db -> runBlocking {
        listOf(db.evidenceDao().raw(id), db.evidenceDao().outcome(id), db.evidenceDao().retry(id), db.evidenceDao().decision(id))
    } }
    private fun <T> withDatabase(context: Context, block: (BetaDatabase) -> T): T {
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        return try { block(db) } finally { db.close() }
    }
}
