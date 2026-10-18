package com.baraa.masroof.lab.beta.processdeath

import android.content.Context
import android.os.Process
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real independent archive + approval + ledger + derived facts across an actual UID kill. */
@RunWith(AndroidJUnit4::class)
class BetaFinancialApprovalProcessDeathTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val asset = OwnedAsset(AssetId(Environment.BETA, BankId("BANK_ALJAZIRA"), "approval-account"), AssetKind.ACCOUNT, "1234")
    private val request = EvidenceRequest("m8-death", EvidenceOrigin.EXPLICIT_SAMPLE)
    private val message = EvidenceMessage("one", "AlJazira", "Bill payment\nFrom account: 1234\nBiller: Synthetic Utilities\nAmount: SAR 50\nDate: 2026-10-18 13:00",
        Instant.parse("2026-10-18T12:00:00Z"), ReceiptTimeSource.USER_SUPPLIED)
    private val id = ProcessBetaEvidence.identity(request.namespace, message.providerId)
    private val key get() = RoomBetaFinancialAdoption.key(id)
    private fun probe() = context.getSharedPreferences("m8_test_process_probe", Context.MODE_PRIVATE)
    private fun mark() { check(probe().edit().putInt("pid", Process.myPid()).commit()) }
    private fun resumed() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("betaResume"))
        assertNotEquals(probe().getInt("pid", -1), Process.myPid())
    }
    private fun draft(beta: BetaContainer) = requireNotNull(betaApprovalDraft(beta.financialApprovalPreview(id)!!,
        FinancialType.BILL_PAYMENT.name, "asset:${asset.id.localId}", "external", null))
    private fun seed(): BetaFinancialApproval {
        BetaContainer.resetOffline(context)
        return BetaContainer(context).use { beta ->
            beta.registerAsset(asset); beta.setBankEnabled(asset.id.bank, true)
            beta.importEvidence(request, listOf(message)); draft(beta)
        }
    }
    @Test fun armBeforeApprovalCommit() {
        val approval = seed(); mark()
        val entered = CountDownLatch(1)
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        Thread {
            runBlocking { db.withTransaction {
                assertTrue(RoomBetaFinancialAdoption(db).approve(approval) is BetaFinancialAdoptionResult.Posted)
                entered.countDown(); CountDownLatch(1).await()
            } }
        }.apply { isDaemon = true; start() }
        assertTrue(entered.await(15, TimeUnit.SECONDS))
    }
    @Test fun resumeBeforeApprovalCommit() {
        resumed()
        BetaContainer(context).use { beta ->
            assertNull(beta.financialPosting(key)); assertTrue(beta.financialHistory(key).isEmpty())
            assertNull(beta.financialApprovalPreview(id)!!.pending)
            val approval = draft(beta)
            assertTrue((beta.approveEvidenceFinancial(approval) as BetaFinancialAdoptionResult.Posted).result is FinancialPostingResult.Applied)
            assertEquals(1, beta.financialHistory(key).size)
        }
    }
    @Test fun armAfterApprovalCommitBeforeAcknowledgement() {
        val approval = seed()
        BetaContainer(context).use { beta -> assertTrue(beta.approveEvidenceFinancial(approval) is BetaFinancialAdoptionResult.Posted) }
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        try { runBlocking {
            val adopted = db.financialAdoptionDao().evidence(id)!!
            // Represents the exact durable intent still present after financial COMMIT, before acknowledgement.
            db.financialAdoptionDao().retry(BetaFinancialApprovalRetryEntity(id, adopted.approvalRequest))
            assertNotNull(db.dashboardFactsDao().evidence(id))
        } } finally { db.close() }
        mark()
    }
    @Test fun resumeAfterApprovalCommitBeforeAcknowledgement() {
        resumed()
        BetaContainer(context).use { beta ->
            assertNull(beta.financialPosting(key))
            assertEquals(1, beta.financialHistory(key).size)
            assertTrue((beta.retryFinancialApproval(id) as BetaFinancialAdoptionResult.Posted).result is FinancialPostingResult.AlreadyApplied)
            assertNull(beta.financialApprovalPreview(id)!!.pending)
            assertEquals(Money.of("50", Currency.SAR), beta.financialPosting(key)!!.command.amount)
            assertEquals(1, beta.financialHistory(key).size)
            beta.backup()
        }
    }
}
