package com.baraa.masroof.application.interpretation

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.ingestion.BankSmsCaptureResult
import com.baraa.masroof.application.sms.LiveSmsProcessingHarness
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ProcessingRetryMode
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.sms.time.InstantClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ModelRefreshWorkflowTest {
    private lateinit var harness: LiveSmsProcessingHarness
    private val now = Instant.parse("2026-08-11T12:00:00Z")

    @Before fun setUp() {
        harness = LiveSmsProcessingHarness(ApplicationProvider.getApplicationContext<Context>())
    }

    @After fun tearDown() { harness.close() }

    private fun workflow(
        mayRefresh: suspend (String) -> Boolean = { !harness.ftRepo.isRawSmsLinked(it) },
        recover: suspend () -> Unit = { harness.derivedRecovery().recoverPending() },
        transaction: suspend (suspend () -> Unit) -> Unit = { harness.db.withTransaction(it) },
    ) = ModelRefreshWorkflow(
        reviews = harness.reviewRepo,
        parsed = harness.parsedRepo,
        raw = harness.rawRepo,
        retries = harness.processingRetryRepo,
        processor = harness.processStored(),
        mayRefresh = mayRefresh,
        needsRefresh = { it.event.messageFamily == MessageFamily.UNKNOWN },
        inTransaction = transaction,
        recoverDerived = recover,
        clock = InstantClock { now },
    )

    private suspend fun seedOldInterpretation(): String {
        val raw = LiveSmsProcessingHarness.liveSms()
        val captured = harness.capture.capture(raw) as BankSmsCaptureResult.Captured
        harness.processStored().reparseAndStore(captured.rawSms)
        val current = requireNotNull(harness.parsedRepo.findByRawSmsId(raw.id))
        harness.parsedRepo.save(current.event.copy(
            messageFamily = MessageFamily.UNKNOWN,
            parseStatus = ParseStatus.REVIEW_REQUIRED,
            amount = null,
        ), current.details)
        harness.reviewRepo.upsertRequired(raw.id, ReviewKind.NEEDS_REVIEW, listOf("unknown_message_family"), now)
        return raw.id
    }

    @Test fun interruptionAfterParse_resumesDerivedWorkWithoutReparsingOrDuplicatePosting() = runBlocking {
        val id = seedOldInterpretation()
        val interruption = runCatching {
            workflow(recover = { throw CancellationException("process stopped before derived pass") }).refresh()
        }.exceptionOrNull()
        assertTrue(interruption is CancellationException)
        assertEquals(ParseStatus.SUCCESS, harness.parsedRepo.findByRawSmsId(id)!!.event.parseStatus)
        assertTrue(harness.ftRepo.listAll().isEmpty())
        assertEquals(listOf(id), harness.processingRetryRepo.listRetryableRawSmsIds(ProcessingRetryMode.HISTORICAL_BATCH))
        val parseCalls = harness.parseCalls.get()

        // A new workflow instance sees a current interpretation, but must still finish its durable work.
        assertTrue(workflow().refresh())
        assertEquals(parseCalls, harness.parseCalls.get())
        assertEquals(1, harness.ftRepo.listAll().size)
        assertTrue(harness.reviewRepo.listRequired().isEmpty())
        assertTrue(harness.processingRetryRepo.listRetryableRawSmsIds().isEmpty())
        assertTrue(workflow().refresh())
        assertEquals(1, harness.ftRepo.listAll().size)
    }

    @Test fun failedReconciliation_keepsMarkerUntilSuccessfulRetry() = runBlocking {
        val id = seedOldInterpretation()
        harness.nonthrowingSaveConflicts.set(Int.MAX_VALUE)
        assertNotNull(runCatching { workflow().refresh() }.exceptionOrNull())
        assertEquals(listOf(id), harness.processingRetryRepo.listRetryableRawSmsIds())
        assertTrue(harness.ftRepo.listAll().isEmpty())
        harness.nonthrowingSaveConflicts.set(0)
        assertTrue(workflow().refresh())
        assertEquals(1, harness.ftRepo.listAll().size)
        assertTrue(harness.processingRetryRepo.listRetryableRawSmsIds().isEmpty())
    }

    @Test fun failedCommit_rollsBackBothTheParseAndItsRetryMarker() = runBlocking {
        val id = seedOldInterpretation()
        val failure = runCatching {
            workflow(transaction = { mutation -> harness.db.withTransaction {
                mutation()
                throw IOException("commit interrupted")
            } }).refresh()
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(MessageFamily.UNKNOWN, harness.parsedRepo.findByRawSmsId(id)!!.event.messageFamily)
        assertTrue(harness.processingRetryRepo.listRetryableRawSmsIds().isEmpty())
        assertTrue(harness.ftRepo.listAll().isEmpty())
        assertTrue(workflow().refresh())
        assertEquals(1, harness.ftRepo.listAll().size)
    }

    @Test fun concurrentHumanDecision_preventsReparseAndRetryMarker() = runBlocking {
        seedOldInterpretation()
        val parseCalls = harness.parseCalls.get()
        var checks = 0
        assertTrue(workflow(mayRefresh = { ++checks == 1 }).refresh())
        assertEquals(2, checks)
        assertEquals(parseCalls, harness.parseCalls.get())
        assertTrue(harness.processingRetryRepo.listRetryableRawSmsIds().isEmpty())
        assertTrue(harness.ftRepo.listAll().isEmpty())
    }
}
