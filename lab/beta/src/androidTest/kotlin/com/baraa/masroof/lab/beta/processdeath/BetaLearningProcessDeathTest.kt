package com.baraa.masroof.lab.beta.processdeath

import android.content.Context
import android.os.Process
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.data.BetaDatabase
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.value.BankId
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The host kills this isolated synthetic test APK before/after the Room label COMMIT. */
@RunWith(AndroidJUnit4::class)
class BetaLearningProcessDeathTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val request = EvidenceRequest("m7-death", EvidenceOrigin.EXPLICIT_SAMPLE)
    private val message = EvidenceMessage("one", "AlJazira", "Credit card purchase\nCard: 1234\nAmount: 42 SAR\nDate: 2026-10-18 12:00",
        Instant.parse("2026-10-18T12:00:00Z"), ReceiptTimeSource.USER_SUPPLIED)
    private val id = ProcessBetaEvidence.identity(request.namespace, message.providerId)
    private fun pid() = context.getSharedPreferences("m7_test_process_probe", Context.MODE_PRIVATE)
    private fun mark() { check(pid().edit().putInt("pid", Process.myPid()).commit()) }
    private fun resumed() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("betaResume"))
        assertNotEquals(pid().getInt("pid", -1), Process.myPid())
    }
    private fun seed(): BetaLabelDraft {
        BetaContainer.resetOffline(context)
        return BetaContainer(context).use { beta ->
            beta.setBankEnabled(BankId("BANK_ALJAZIRA"), true)
            beta.importEvidence(request, listOf(message))
            input(requireNotNull(beta.interpretationReview(id)))
        }
    }
    private fun input(detail: BetaInterpretationReview): BetaLabelDraft {
        val start = detail.body.indexOf("42 SAR")
        return BetaLabelDraft(detail.token, "m7-explicit-decision", BetaLabelField.AMOUNT, "43", start, start + 2, true)
    }
    @Test fun armBeforeLabelCommit() {
        val draft = seed(); mark()
        val entered = CountDownLatch(1)
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        Thread {
            runBlocking { db.withTransaction {
                BetaInterpretationReviewWorkflow(db).save(draft)
                entered.countDown(); CountDownLatch(1).await()
            } }
        }.apply { isDaemon = true; start() }
        assertTrue(entered.await(15, TimeUnit.SECONDS))
    }
    @Test fun resumeBeforeLabelCommit() {
        resumed()
        BetaContainer(context).use { beta ->
            val detail = requireNotNull(beta.interpretationReview(id))
            assertTrue(detail.labels.isEmpty())
            val draft = input(detail)
            val first = beta.saveInterpretationLabel(draft)
            assertEquals(first, beta.saveInterpretationLabel(draft))
            assertEquals(1, beta.interpretationReview(id)!!.labels.size)
            assertEquals("42.00", beta.interpretationReview(id)!!.observed[BetaLabelField.AMOUNT])
        }
    }
    @Test fun armAfterLabelCommit() {
        val draft = seed()
        BetaContainer(context).use { beta -> beta.saveInterpretationLabel(draft); beta.backup() }
        mark()
    }
    @Test fun resumeAfterLabelCommit() {
        resumed()
        BetaContainer(context).use { beta ->
            val detail = requireNotNull(beta.interpretationReview(id))
            val label = detail.labels.single()
            assertEquals("42", label.originalText); assertEquals("43", label.value)
            val retry = input(detail).copy(token = detail.token.copy(labelRevision = 0))
            assertEquals(label, beta.saveInterpretationLabel(retry))
            beta.saveInterpretationLabel(input(detail).copy(decisionId = "second", value = "44"))
            assertTrue(beta.restore())
            assertEquals(listOf(label), beta.interpretationReview(id)!!.labels)
            assertEquals("42.00", beta.interpretationReview(id)!!.observed[BetaLabelField.AMOUNT])
            assertTrue(beta.assets().isEmpty())
        }
    }
}
