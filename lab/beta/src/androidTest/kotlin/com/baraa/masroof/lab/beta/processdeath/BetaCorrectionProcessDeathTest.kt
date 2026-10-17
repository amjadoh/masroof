package com.baraa.masroof.lab.beta.processdeath

import android.content.Context
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.corrections.*
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** External script kills the actual isolated synthetic process on each side of COMMIT. */
@RunWith(AndroidJUnit4::class)
class BetaCorrectionProcessDeathTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val asset = OwnedAsset(AssetId(Environment.BETA, BankId("BANK_ALJAZIRA"), "correction-account"), AssetKind.ACCOUNT, "1234")
    private val original = FinancialClassificationInput(PostingKey(Environment.BETA, "correction-transaction"), PostingRevision(5, 0),
        FinancialType.EXPENSE, Money.of("42.35", Currency.USD), TransactionTime(Instant.parse("2026-10-17T12:00:00.123456789Z"), ZoneId.of("Asia/Riyadh")),
        FinancialEndpoint.Owned(asset.id), FinancialEndpoint.External, mapOf(asset.id to asset), setOf("correction-evidence"), false,
        CorrectionAuthority.USER_FINANCIAL_TYPE, appliedFx = AppliedFx(BigDecimal("3.750000012345678900"), "confirmed-rate"))
    private val observation = CorrectionObservation(original.copy(revision = PostingRevision(0, 1), authority = CorrectionAuthority.NONE,
        amount = Money.of("99", Currency.USD), appliedFx = AppliedFx(BigDecimal("3.99"), "reparsed-rate")), ObservationSafety.ORDINARY)
    private val suppress = CorrectionRequest("suppression-decision", original.key, 6, 1, original.evidenceIds, FinancialDecision.SuppressAsNonFinancial)
    private fun markPid() { check(context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString("beta_correction_arm_pid", Process.myPid().toString()).commit()) }
    private fun assertNewProcess() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("betaResume"))
        assertNotEquals(context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE).getString("beta_correction_arm_pid", null), Process.myPid().toString())
    }
    private fun seed() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta -> beta.registerAsset(asset); assertTrue(beta.postFinancial(original) is FinancialPostingResult.Applied) }
    }

    @Test fun armBeforeSuppressionCommit() {
        seed(); markPid()
        val entered = CountDownLatch(1)
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        Thread {
            runBlocking { RoomBetaCorrectionLifecycle(db) {
                entered.countDown(); CountDownLatch(1).await()
            }.submit(observation, suppress) }
        }.apply { isDaemon = true; start() }
        assertTrue(entered.await(15, TimeUnit.SECONDS))
    }

    @Test fun resumeBeforeSuppressionCommit() {
        assertNewProcess()
        BetaContainer(context).use { beta ->
            assertNull(beta.correction(original.key)); assertTrue(beta.correctionHistory(original.key).isEmpty())
            assertEquals(original, beta.financialPosting(original.key)!!.classification.input)
            assertEquals(1, beta.financialHistory(original.key).size)
            val result = beta.submitCorrection(observation, suppress) as BetaCorrectionResult.Applied
            assertEquals(BetaLedgerActivity.SUPPRESSED, result.current.activity)
            assertNull(beta.financialPosting(original.key)); assertNotNull(beta.financialAuditPosting(original.key))
            assertTrue(beta.submitCorrection(observation, suppress) is BetaCorrectionResult.AlreadyApplied)
            assertEquals(1, beta.correctionHistory(original.key).size)
        }
    }

    @Test fun armAfterSuppressionCommit() {
        seed()
        BetaContainer(context).use { beta ->
            assertEquals(BetaLedgerActivity.SUPPRESSED, (beta.submitCorrection(observation, suppress) as BetaCorrectionResult.Applied).current.activity)
            beta.backup()
        }
        markPid()
    }

    @Test fun resumeAfterSuppressionCommit() {
        assertNewProcess()
        BetaContainer(context).use { beta ->
            assertEquals(BetaLedgerActivity.SUPPRESSED, beta.correction(original.key)!!.activity)
            assertNull(beta.financialPosting(original.key)); assertEquals(original, beta.financialAuditPosting(original.key)!!.classification.input)
            assertTrue(beta.postFinancial(original.copy(revision = PostingRevision(999, 0))) is FinancialPostingResult.Review)
            assertTrue(beta.submitCorrection(observation, suppress) is BetaCorrectionResult.AlreadyApplied)
            val restore = CorrectionRequest("restoration-decision", original.key, 7, 1, original.evidenceIds,
                FinancialDecision.ResolveFinancialType(FinancialType.EXPENSE), expectedCorrectionRevision = 6)
            val restored = (beta.submitCorrection(observation, restore) as BetaCorrectionResult.Applied).current
            assertEquals(BetaLedgerActivity.ACTIVE, restored.activity)
            assertEquals(original.amount, restored.posting!!.command.amount); assertEquals(original.appliedFx, restored.posting.command.appliedFx)
            assertEquals(original.time, restored.posting.command.time)
            assertEquals(2, beta.correctionHistory(original.key).size)
            assertTrue(beta.restore())
            assertNull(beta.financialPosting(original.key)); assertEquals(BetaLedgerActivity.SUPPRESSED, beta.correction(original.key)!!.activity)
            assertEquals(1, beta.correctionHistory(original.key).size)
        }
    }
}
