package com.baraa.masroof.lab.beta.processdeath

import android.content.Context
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.application.history.*
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.value.BankId
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Invoked only by the external phase script; fixtures never read the device inbox. */
@RunWith(AndroidJUnit4::class)
class BetaHistoryProcessDeathTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bank = BankId("BANK_ALJAZIRA")
    private val request = EvidenceRequest(HistorySession.NAMESPACE, EvidenceOrigin.EXPLICIT_HISTORY, setOf(bank))
    private val message = EvidenceMessage("424242", "AlJazira", "synthetic lifecycle evidence", Instant.ofEpochMilli(500), ReceiptTimeSource.PROVIDER_TIMESTAMP)
    private val id get() = ProcessBetaEvidence.identity(request.namespace, message.providerId)
    private val prefs get() = context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE)
    private fun markPid() { check(prefs.edit().putString("beta_history_arm_pid", Process.myPid().toString()).commit()) }
    private fun assertNewProcess() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("betaResume"))
        assertNotEquals(prefs.getString("beta_history_arm_pid", null), Process.myPid().toString())
    }
    @Test fun armDuringComparison() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { it.setBankEnabled(bank, true); it.startHistory(7, 1000) }
        markPid()
        val entered = CountDownLatch(1)
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        Thread {
            runBlocking {
                ProcessBetaEvidence(RoomBetaEvidenceStore(db), RoomBetaBankSelectionStore(db),
                    EvidenceRouter { EvidenceRoute(RouteKind.MATCHED, setOf(bank), "fixture") }, EvidenceComparer { _, _ ->
                        entered.countDown()
                        CountDownLatch(1).await() // External force-stop kills this actual process at this seam.
                        error("unreachable")
                    }).importBatch(request, listOf(message))
            }
        }.apply { isDaemon = true; start() }
        assertTrue(entered.await(15, TimeUnit.SECONDS))
        runBlocking { assertEquals(EvidenceStage.INTERPRETATION, RoomBetaEvidenceStore(db).summary(id).retryStage) }
        // Do not close this database while derivation is parked. The process will be killed.
    }
    @Test fun resumeDuringComparison() {
        assertNewProcess()
        BetaContainer(context).use { beta ->
            assertEquals(0, beta.historySession()!!.scanned)
            assertNotNull(beta.evidenceSummary(id).retryStage)
            beta.retryEvidence()
            val after = beta.evidenceSummary(id)
            assertNull(after.retryStage)
            val count = beta.evidencePage().size
            beta.importEvidence(request, listOf(message))
            assertEquals(count, beta.evidencePage().size)
            assertEquals(after.attempts, beta.evidenceSummary(id).attempts)
        }
    }
    @Test fun armAfterComparisonBeforeCheckpoint() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            beta.startHistory(7, 1000)
            beta.importEvidence(request, listOf(message))
            assertNull(beta.evidenceSummary(id).retryStage)
            assertTrue(beta.decideEvidence(id, 0, EvidenceReviewDecision.KEEP_FOR_REVIEW))
        }
        markPid()
    }
    @Test fun resumeAfterComparisonBeforeCheckpoint() {
        assertNewProcess()
        BetaContainer(context).use { beta ->
            assertEquals(0, beta.historySession()!!.scanned)
            val before = beta.evidenceSummary(id)
            val store = PreferenceHistorySessionStore(prefs)
            BetaHistoryImport(store, { _, _ -> listOf(HistoryRow(424242, 500, message.sender, message.body)) }, beta::importEvidence).step()
            assertEquals(1, beta.historySession()!!.scanned)
            assertEquals(before, beta.evidenceSummary(id))
            assertEquals(1, beta.evidencePage().size)
            assertEquals(EvidenceReviewDecision.KEEP_FOR_REVIEW, beta.evidenceSummary(id).manualDecision)
        }
    }
}
