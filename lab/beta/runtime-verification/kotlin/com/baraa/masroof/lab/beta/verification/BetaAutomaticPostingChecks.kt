package com.baraa.masroof.lab.beta.verification

import android.content.Context
import androidx.room.Room
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.data.BetaDatabase
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import org.junit.Assert.*
import java.time.Instant

object BetaAutomaticPostingChecks {
    private val bank = BankId("BANK_ALJAZIRA")
    private val owner = OwnedAsset(AssetId(Environment.BETA, bank, "owned"), AssetKind.ACCOUNT, "1234")
    private val at = Instant.parse("2026-10-18T12:00:00Z")
    private val cash = "Cash withdrawal\nFrom account: 1234\nAmount: 42 SAR\nDate: 2026-10-18 12:00"
    private fun capture(beta: BetaContainer, namespace: String = "automatic", body: String = cash): String =
        (beta.importEvidence(EvidenceRequest(namespace, EvidenceOrigin.EXPLICIT_SAMPLE), listOf(
            EvidenceMessage("one", "AlJazira", body, at, ReceiptTimeSource.USER_SUPPLIED))).single() as EvidenceResult.Stored).summary.id
    private fun key(id: String) = PostingKey(Environment.BETA, "evidence:$id")
    private inline fun isolated(context: Context, block: (BetaContainer) -> Unit) {
        BetaContainer.resetOffline(context)
        try { BetaContainer(context).use { beta -> beta.acknowledgeStart(); beta.setBankEnabled(bank, true); block(beta) } }
        finally { BetaContainer.resetOffline(context) }
    }

    fun confirmedOwnershipPostsOnceAcrossReopenAndNewCapture(context: Context) {
        lateinit var id: String
        isolated(context) { beta ->
            id = capture(beta)
            beta.processAutomaticEvidence()
            assertNull(beta.financialPosting(key(id)))
            assertEquals(1, beta.evidenceInboxCounts().needsOwnership)
            assertEquals(0, beta.evidenceInboxCounts().needsReview)
            beta.registerAsset(owner)
            beta.processAutomaticEvidence()
            val row = requireNotNull(beta.financialPosting(key(id)))
            assertEquals(Money.of("42", Currency.SAR), row.command.amount)
            assertEquals(CorrectionAuthority.NONE, row.classification.input.authority)
            assertEquals(FinancialType.CASH_WITHDRAWAL, row.classification.effectiveType)
            assertEquals(PostingRevision(0, 1), row.command.revision)
            assertEquals(0, beta.evidenceInboxCounts().needsOwnership)
            assertTrue(beta.evidencePage(needsReviewOnly = true).isEmpty())
            beta.processAutomaticEvidence()
            assertEquals(1, beta.financialHistory(key(id)).size)
            // Reopen without resetting the checkpoint or granting approval again.
            BetaContainer(context).use { reopened -> reopened.processAutomaticEvidence(); assertEquals(1, reopened.financialHistory(key(id)).size) }
            val next = capture(beta, "new-import", cash.replace("42 SAR", "43 SAR").replace("12:00", "12:01"))
            beta.processAutomaticEvidence()
            assertEquals(Money.of("43", Currency.SAR), requireNotNull(beta.financialPosting(key(next))).command.amount)
            assertEquals(1, beta.financialHistory(key(id)).size)
        }
    }

    fun ambiguousOwnershipAndUnsafeMessagesStayUnposted(context: Context) = isolated(context) { beta ->
        beta.registerAsset(owner); beta.registerAsset(owner.copy(id = owner.id.copy(localId = "collision")))
        val ambiguous = capture(beta)
        val unknown = capture(beta, "unknown", "Synthetic unclear banking message")
        val otp = capture(beta, "security", "OTP: 1234. Do not share this code.")
        val incomplete = capture(beta, "partial", "Cash withdrawal\nFrom account: 1234\nDate: 2026-10-18 12:00")
        beta.processAutomaticEvidence()
        for (id in listOf(ambiguous, unknown, otp, incomplete)) assertNull(beta.financialPosting(key(id)))
        assertEquals("AMBIGUOUS_OWNER", beta.evidenceSummary(ambiguous).financialReviewReason)
        assertTrue(beta.evidencePage(needsReviewOnly = true).any { it.id == ambiguous })
        assertNull(beta.evidenceSummary(otp).financialReviewReason)
    }

    fun explicitManualChoiceWinsAndNeverBecomesAutomatic(context: Context) = isolated(context) { beta ->
        beta.registerAsset(owner)
        val id = capture(beta)
        val preview = requireNotNull(beta.financialApprovalPreview(id))
        val request = BetaFinancialApproval(id, preview.item.archiveRevision, null, FinancialType.FEE, owner.id, null,
            expectedRegistryRevision = preview.registryRevision)
        assertTrue(beta.approveEvidenceFinancial(request) is BetaFinancialAdoptionResult.Posted)
        beta.processAutomaticEvidence()
        val row = requireNotNull(beta.financialPosting(key(id)))
        assertEquals(FinancialType.FEE, row.classification.effectiveType)
        assertEquals(CorrectionAuthority.USER_FINANCIAL_TYPE, row.classification.input.authority)
        assertEquals(1, beta.financialHistory(key(id)).size)
    }

    fun pendingManualRequestRemainsVisibleAndBlocksAutomaticPosting(context: Context) = isolated(context) { beta ->
        beta.registerAsset(owner)
        val id = capture(beta)
        val preview = requireNotNull(beta.financialApprovalPreview(id))
        val request = BetaFinancialApproval(id, preview.item.archiveRevision, null, FinancialType.FEE, owner.id, null,
            expectedRegistryRevision = "stale-registry")
        assertTrue(beta.approveEvidenceFinancial(request) is BetaFinancialAdoptionResult.Review)
        beta.processAutomaticEvidence()
        assertNull(beta.financialPosting(key(id)))
        assertEquals(request, beta.financialApprovalPreview(id)?.pending)
        assertEquals(1, beta.evidenceInboxCounts().needsReview)
        assertEquals(0, beta.evidenceInboxCounts().needsOwnership)
        assertEquals(id, beta.evidencePage(needsReviewOnly = true).single().id)
        assertEquals("FINANCIAL_APPROVAL_PENDING", beta.evidenceSummary(id).financialReviewReason)
    }

    fun duplicateCapturedMessagesStayInReview(context: Context) = isolated(context) { beta ->
        beta.registerAsset(owner)
        val first = capture(beta, "first")
        val duplicate = capture(beta, "second")
        beta.processAutomaticEvidence()
        for (id in listOf(first, duplicate)) {
            assertNull(beta.financialPosting(key(id)))
            assertEquals("POSSIBLE_DUPLICATE", beta.evidenceSummary(id).financialReviewReason)
        }
        assertEquals(2, beta.evidenceInboxCounts().needsReview)
    }

    fun interruptedSweepRemainsRetryableAndAtomic(context: Context) = isolated(context) { beta ->
        beta.registerAsset(owner)
        val id = capture(beta)
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        try {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_auto BEFORE INSERT ON beta_ledger_current BEGIN SELECT RAISE(ABORT,'synthetic'); END")
            assertThrows(Exception::class.java) { beta.processAutomaticEvidence() }
            assertNull(beta.financialPosting(key(id)))
            assertTrue(beta.financialHistory(key(id)).isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_auto")
            beta.processAutomaticEvidence()
            assertNotNull(beta.financialPosting(key(id)))
            assertEquals(1, beta.financialHistory(key(id)).size)
        } finally { db.close() }
    }
}
