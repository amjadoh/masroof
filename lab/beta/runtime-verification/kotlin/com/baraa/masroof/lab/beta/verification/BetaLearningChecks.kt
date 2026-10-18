package com.baraa.masroof.lab.beta.verification

import android.content.Context
import androidx.room.Room
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.value.BankId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

/** Only isolated synthetic application storage, shared by JVM and Android verification. */
object BetaLearningChecks {
    private val bank = BankId("BANK_ALJAZIRA")
    private val request = EvidenceRequest("m7-synthetic", EvidenceOrigin.EXPLICIT_SAMPLE)
    private val message = EvidenceMessage("m7", "AlJazira",
        "Credit card purchase\nCard: 1234\nAmount: 42 SAR\nMerchant: Synthetic Shop\nDate: 2026-10-18 12:00",
        Instant.parse("2026-10-18T12:00:00Z"), ReceiptTimeSource.USER_SUPPLIED)

    private fun capture(beta: BetaContainer): String {
        beta.setBankEnabled(bank, true)
        val summary = (beta.importEvidence(request, listOf(message)).single() as EvidenceResult.Stored).summary
        assertEquals(EvidenceProcessingState.INTERPRETED, summary.processingState)
        return summary.id
    }
    private fun draft(detail: BetaInterpretationReview, decision: String = "human-amount"): BetaLabelDraft {
        val start = detail.body.indexOf("42 SAR")
        return BetaLabelDraft(detail.token, decision, BetaLabelField.AMOUNT, "43", start, start + 2, true)
    }
    private fun <T> database(context: Context, block: (BetaDatabase) -> T): T {
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        return try { block(db) } finally { db.close() }
    }
    private fun authority(context: Context, id: String) = database(context) { db -> runBlocking {
        val raw = requireNotNull(db.evidenceDao().raw(id))
        val outcome = requireNotNull(db.evidenceDao().outcome(id))
        val count = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM beta_ledger_current").use {
            assertTrue(it.moveToFirst()); it.getInt(0)
        }
        Triple(raw, outcome, count)
    } }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected invalid/stale label rejection") } catch (_: IllegalArgumentException) { }
    }

    fun labelsSurvive(context: Context) {
        BetaContainer.resetOffline(context)
        try {
            val canary = context.getDatabasePath(BetaEnvironment.PRODUCTION_DATABASE_NAME)
            canary.parentFile?.mkdirs(); canary.writeText("synthetic-m7-protected")
            val protected = canary.readBytes()
            lateinit var id: String
            lateinit var first: BetaTrustedLabel
            BetaContainer(context).use { beta ->
                id = capture(beta)
                val detail = requireNotNull(beta.interpretationReview(id))
                assertTrue(detail.labels.isEmpty()) // Rule observations are not human labels.
                assertTrue(detail.spans.isNotEmpty())
                assertEquals("42.00", detail.observed[BetaLabelField.AMOUNT])
                val before = authority(context, id)
                val input = draft(detail)
                rejected { beta.saveInterpretationLabel(input.copy(confirmed = false)) }
                first = beta.saveInterpretationLabel(input)
                assertEquals("42", first.originalText); assertEquals("43", first.value)
                assertEquals(first, beta.saveInterpretationLabel(input))
                rejected { beta.saveInterpretationLabel(input.copy(value = "44")) }
                rejected { beta.saveInterpretationLabel(input.copy(decisionId = "stale")) }
                assertEquals(before, authority(context, id))
                assertTrue(beta.assets().isEmpty())
                beta.backup()
                val updated = requireNotNull(beta.interpretationReview(id))
                beta.saveInterpretationLabel(draft(updated, "second").copy(value = "44"))
                assertEquals(2, beta.interpretationReview(id)!!.labels.size)
            }
            BetaContainer(context).use { beta ->
                assertEquals(2, beta.interpretationReview(id)!!.labels.size)
                assertTrue(beta.restore())
                assertEquals(listOf(first), beta.interpretationReview(id)!!.labels)
                assertEquals("42.00", beta.interpretationReview(id)!!.observed[BetaLabelField.AMOUNT])
            }
            assertArrayEquals(protected, canary.readBytes())
        } finally { BetaContainer.resetOffline(context) }
    }

    fun reparseDoesNotPromote(context: Context) {
        BetaContainer.resetOffline(context)
        try { BetaContainer(context).use { beta ->
            val id = capture(beta)
            val detail = requireNotNull(beta.interpretationReview(id))
            val input = draft(detail)
            val saved = beta.saveInterpretationLabel(input)
            val stale = draft(beta.interpretationReview(id)!!, "stale-after-reparse")
            database(context) { db -> runBlocking { RoomBetaEvidenceStore(db).begin(id, Instant.EPOCH) } }
            assertNull(beta.interpretationReview(id))
            rejected { beta.saveInterpretationLabel(stale) }
            beta.reprocessEvidence(id)
            val current = requireNotNull(beta.interpretationReview(id))
            assertEquals(listOf(saved), current.labels)
            assertNotEquals(saved.attempt, current.token.attempt)
            rejected { beta.saveInterpretationLabel(stale) }
            assertEquals(saved, beta.saveInterpretationLabel(input)) // Retry of an existing operation never rewrites it.
            assertEquals("42.00", current.observed[BetaLabelField.AMOUNT])
            assertEquals(0, authority(context, id).third)
            assertTrue(beta.assets().isEmpty())
            beta.setBankEnabled(bank, false)
            // Explicit review of already saved messages is independent of enabling more inbox intake.
            beta.saveInterpretationLabel(draft(current, "paused-explicit").copy(value = "45"))
        } } finally { BetaContainer.resetOffline(context) }
    }

    fun unsafeLabelsRejected(context: Context) {
        BetaContainer.resetOffline(context)
        try { BetaContainer(context).use { beta ->
            val id = capture(beta)
            val detail = requireNotNull(beta.interpretationReview(id))
            val input = draft(detail)
            listOf(input.copy(start = -1), input.copy(end = detail.body.length + 1), input.copy(end = input.start),
                input.copy(value = "0"), input.copy(value = "-1"), input.copy(value = "NaN"),
                input.copy(field = BetaLabelField.CURRENCY, value = "OTHER"),
                input.copy(field = BetaLabelField.CARD, value = "123456789"),
                input.copy(field = BetaLabelField.FAMILY, value = "arbitrary"),
                input.copy(token = detail.token.copy(archiveHash = "forged")),
                input.copy(token = detail.token.copy(evidenceId = "unknown"))).forEach { invalid ->
                rejected { beta.saveInterpretationLabel(invalid) }
            }
            assertTrue(beta.interpretationReview(id)!!.labels.isEmpty())
            val arabic = beta.saveInterpretationLabel(input.copy(decisionId = "arabic-decimal", value = "٤٣٫٢٥"))
            assertEquals("43.25", arabic.value)
            val otp = message.copy(providerId = "security", body = "OTP: 5555\nDo not share this code")
            val otpId = (beta.importEvidence(request, listOf(otp)).single() as EvidenceResult.Stored).summary.id
            val security = requireNotNull(beta.interpretationReview(otpId))
            assertTrue(security.labels.isEmpty())
            val start = security.body.indexOf("5555")
            beta.saveInterpretationLabel(BetaLabelDraft(security.token, "explicit-otp", BetaLabelField.FAMILY, "OTP", start, start + 4, true))
            assertEquals(EvidenceProcessingState.INTERPRETED, beta.evidenceSummary(otpId).processingState)
            assertEquals("OTP", beta.interpretationReview(otpId)!!.observed[BetaLabelField.FAMILY])
            val annotated = requireNotNull(beta.interpretationReview(otpId))
            // Even a human's incorrect financial annotation cannot override the security interpretation.
            beta.saveInterpretationLabel(BetaLabelDraft(annotated.token, "human-not-authority", BetaLabelField.FAMILY, "PURCHASE", start, start + 4, true))
            assertEquals("OTP", beta.interpretationReview(otpId)!!.observed[BetaLabelField.FAMILY])
            assertEquals(0, authority(context, otpId).third)
            val emoji = message.copy(providerId = "utf16", body = message.body + "\n😀")
            val emojiId = (beta.importEvidence(request, listOf(emoji)).single() as EvidenceResult.Stored).summary.id
            val emojiDetail = requireNotNull(beta.interpretationReview(emojiId))
            val emojiDraft = draft(emojiDetail, "split-utf16")
            rejected { beta.saveInterpretationLabel(emojiDraft.copy(start = emojiDetail.body.length - 1, end = emojiDetail.body.length)) }
            rejected { beta.saveInterpretationLabel(emojiDraft.copy(start = emojiDetail.body.length - 2, end = emojiDetail.body.length - 1)) }
            assertTrue(beta.interpretationReview(emojiId)!!.labels.isEmpty())
        } } finally { BetaContainer.resetOffline(context) }
    }
    fun expandedRolesKeepEvidenceAndMoneyIndependent(context: Context) {
        BetaContainer.resetOffline(context)
        try { BetaContainer(context).use { beta ->
            val id = capture(beta)
            val originalAuthority = authority(context, id)
            val values = listOf(BetaLabelField.DIRECTION to "OUTGOING", BetaLabelField.EXECUTION_STATE to "EXECUTED",
                BetaLabelField.AVAILABLE_BALANCE to "٠", BetaLabelField.OUTSTANDING_BALANCE to "١٢٣٫٤٥")
            values.forEachIndexed { index, (field, value) ->
                val detail = requireNotNull(beta.interpretationReview(id))
                val saved = beta.saveInterpretationLabel(draft(detail, "expanded-$index").copy(field = field, value = value))
                assertNotNull(saved.tokenStart); assertNotNull(saved.tokenEnd)
                assertTrue(saved.tokenEnd!! > saved.tokenStart!!)
                assertEquals("bank-source-tokens-v1", saved.tokenizerVersion)
                assertEquals(if (field in setOf(BetaLabelField.DIRECTION, BetaLabelField.EXECUTION_STATE))
                    "HUMAN_CONFIRMED" else "HUMAN_CORRECTED", saved.humanProvenance)
                assertEquals("42", saved.originalText)
            }
            assertEquals("0", beta.interpretationReview(id)!!.labels[2].value)
            assertEquals("123.45", beta.interpretationReview(id)!!.labels[3].value)
            val current = requireNotNull(beta.interpretationReview(id))
            rejected { beta.saveInterpretationLabel(draft(current, "bad-state").copy(field = BetaLabelField.EXECUTION_STATE, value = "SUCCESS")) }
            rejected { beta.saveInterpretationLabel(draft(current, "bad-direction").copy(field = BetaLabelField.DIRECTION, value = "EXECUTED")) }
            assertEquals(originalAuthority, authority(context, id))
            beta.backup()
            assertTrue(beta.restore())
            assertEquals(4, beta.interpretationReview(id)!!.labels.size)
        } } finally { BetaContainer.resetOffline(context) }
    }

}
