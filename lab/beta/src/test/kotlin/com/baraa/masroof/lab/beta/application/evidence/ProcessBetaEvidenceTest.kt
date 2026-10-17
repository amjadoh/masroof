package com.baraa.masroof.lab.beta.application.evidence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.value.BankId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProcessBetaEvidenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BetaDatabase
    private val bank = BankId("BANK_ALJAZIRA")
    private val other = BankId("EXAMPLE_BANK")
    private val now = Instant.parse("2026-10-16T12:34:56.123456Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val request = EvidenceRequest("explicit-fixture", EvidenceOrigin.EXPLICIT_SAMPLE)
    private fun message(id: String = "provider-1", body: String = "private body") =
        EvidenceMessage(id, "AlJazira", body, now.minusSeconds(100), ReceiptTimeSource.PROVIDER_TIMESTAMP)
    private val matched = EvidenceRoute(RouteKind.MATCHED, setOf(bank), "test-full-router-v1")
    private var calls = 0
    private var selectionRows = listOf(UserBankSelection(bank, BankSelectionState.ENABLED))
    private val selectionStore = object : BetaBankSelectionStore {
        override suspend fun all() = selectionRows
        override suspend fun save(selection: UserBankSelection) { selectionRows = listOf(selection) }
    }
    private fun workflow(store: BetaEvidenceStore = RoomBetaEvidenceStore(db), route: EvidenceRoute = matched,
        compare: EvidenceComparer = EvidenceComparer { _, _ ->
            calls++
            EvidenceComparison(EvidenceDisposition.COMPARISON_ONLY, emptySet(), "{\"baseline\":1}", "{\"candidate\":1}")
        }) = ProcessBetaEvidence(store, selectionStore, EvidenceRouter { route }, compare, clock)
    private fun id(provider: String = "provider-1") = ProcessBetaEvidence.identity(request.namespace, provider)
    private fun stored(result: EvidenceResult) = (result as EvidenceResult.Stored).summary
    private fun reopen() {
        db.close()
        db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
    }
    @Before fun open() {
        BetaContainer.resetOffline(context)
        db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
    }
    @After fun close() { db.close(); BetaContainer.resetOffline(context) }

    @Test fun captureCommitsImmutableRawAndRetryBeforeDerivationAndDuplicatesDoNotCompareTwice() = runBlocking {
        val store = RoomBetaEvidenceStore(db)
        val comparer = EvidenceComparer { input, _ ->
            calls++
            runBlocking {
                assertEquals(input.body, store.find(input.evidenceId)!!.message.body)
                assertEquals(EvidenceStage.INTERPRETATION, store.summary(input.evidenceId).retryStage)
            }
            EvidenceComparison(EvidenceDisposition.COMPARISON_ONLY, emptySet(), "{}", "{}")
        }
        val w = workflow(compare = comparer)
        val summary = stored(w.importBatch(request, listOf(message())).single())
        assertNull(summary.retryStage)
        assertEquals(EvidenceDisposition.COMPARISON_ONLY, summary.disposition)
        assertEquals(now, store.find(id())!!.capturedAt)
        assertEquals(now.minusSeconds(100), store.find(id())!!.message.receivedAt)
        assertEquals(ReceiptTimeSource.PROVIDER_TIMESTAMP, store.find(id())!!.message.timeSource)
        w.importBatch(request, listOf(message()))
        assertEquals(1, calls)
        assertEquals(1, store.summary(id()).attempts)
    }

    @Test fun sameProviderIdentityWithChangedPayloadIsConflictAndNeverTouchesDecisionOrRaw() = runBlocking {
        val w = workflow()
        w.importBatch(request, listOf(message()))
        assertTrue(w.decide(id(), 0, EvidenceReviewDecision.KEEP_FOR_REVIEW))
        val before = db.evidenceDao().raw(id())
        listOf(message(body = "changed"), message().copy(sender = "different"),
            message().copy(receivedAt = now), message().copy(timeSource = ReceiptTimeSource.USER_SUPPLIED)).forEach {
            assertEquals(EvidenceResult.IdentityConflict, w.importBatch(request, listOf(it)).single())
        }
        assertEquals(before, db.evidenceDao().raw(id()))
        assertEquals(1, RoomBetaEvidenceStore(db).summary(id()).manualRevision)
        assertEquals(1, calls)
    }

    @Test fun repeatedIdenticalMessagesWithDifferentProviderIdsAreSeparateEvidenceNotBodyDeduplicated() = runBlocking {
        val results = workflow().importBatch(request, listOf(message("one"), message("two")))
        assertEquals(2, results.map { stored(it).id }.distinct().size)
        assertEquals(2, calls)
        assertNotEquals(ProcessBetaEvidence.identity("a:1", "x"), ProcessBetaEvidence.identity("a", "1:x"))
    }

    @Test fun fullCatalogCollisionIsDurableReviewWithAllCandidatesDespiteOnlyOneEnabled() = runBlocking {
        val ambiguous = EvidenceRoute(RouteKind.AMBIGUOUS, setOf(bank, other), "full")
        val summary = stored(workflow(route = ambiguous).importBatch(request, listOf(message())).single())
        assertEquals(EvidenceDisposition.REVIEW, summary.disposition)
        assertEquals(setOf(bank, other), summary.route!!.candidates)
        assertTrue("ROUTE_AMBIGUOUS" in summary.reasonCodes)
        assertEquals(0, calls)
        assertNull(summary.retryStage)
    }

    @Test fun historyScopeDoesNotUseSelectionsToResolveCollisionOrCaptureOutsideScope() = runBlocking {
        val history = request.copy(origin = EvidenceOrigin.EXPLICIT_HISTORY, historyBanks = setOf(other))
        val ambiguous = EvidenceRoute(RouteKind.AMBIGUOUS, setOf(bank, other), "full")
        assertEquals(EvidenceResult.NotCaptured(ScopeRejection.OUTSIDE_REQUESTED_HISTORY),
            workflow(route = ambiguous).importBatch(history, listOf(message())).single())
        selectionRows = listOf(UserBankSelection(bank, BankSelectionState.DISABLED))
        assertEquals(EvidenceResult.NotCaptured(ScopeRejection.NOT_ENABLED),
            workflow(route = ambiguous).importBatch(request, listOf(message())).single())
        assertNull(RoomBetaEvidenceStore(db).find(id()))
    }

    @Test fun suspectedSamplesAreReviewableButUnprovenHistoryAndUnknownAreNotCaptured() = runBlocking {
        val suspected = EvidenceRoute(RouteKind.SUSPECTED, emptySet(), "full")
        assertEquals(EvidenceResult.NotCaptured(ScopeRejection.UNPROVEN_HISTORY_SCOPE),
            workflow(route = suspected).importBatch(request.copy(origin = EvidenceOrigin.EXPLICIT_HISTORY,
                historyBanks = setOf(bank)), listOf(message())).single())
        assertEquals(EvidenceResult.NotCaptured(ScopeRejection.UNRECOGNIZED),
            workflow(route = EvidenceRoute(RouteKind.UNRECOGNIZED, emptySet(), "full"))
                .importBatch(request, listOf(message())).single())
        val summary = stored(workflow(route = suspected).importBatch(request, listOf(message())).single())
        assertEquals(EvidenceDisposition.REVIEW, summary.disposition)
        assertEquals(0, calls)
    }

    @Test fun disabledAndUnrequestedMatchedBanksDoNotCreateEvidenceOrInvokeEngine() = runBlocking {
        selectionRows = listOf(UserBankSelection(bank, BankSelectionState.DISABLED))
        assertEquals(EvidenceResult.NotCaptured(ScopeRejection.NOT_ENABLED),
            workflow().importBatch(request, listOf(message())).single())
        selectionRows = listOf(UserBankSelection(bank, BankSelectionState.ENABLED))
        assertEquals(EvidenceResult.NotCaptured(ScopeRejection.OUTSIDE_REQUESTED_HISTORY),
            workflow().importBatch(request.copy(origin = EvidenceOrigin.EXPLICIT_HISTORY, historyBanks = setOf(other)),
                listOf(message())).single())
        assertEquals(0, calls)
        assertNull(db.evidenceDao().raw(id()))
    }

    @Test fun exceptionKeepsSafeDurableRetryAndRestartRetriesSameRawWithoutDuplication() = runBlocking {
        val bad = workflow(compare = EvidenceComparer { _, _ -> error("private body should never escape") })
        val result = stored(bad.importBatch(request, listOf(message())).single())
        assertNull(result.disposition)
        assertEquals(EvidenceStage.INTERPRETATION, result.retryStage)
        assertEquals(setOf("INTERPRETATION_FAILED"), result.reasonCodes)
        reopen()
        val retried = workflow().retryPending().single()
        assertNull(retried.retryStage)
        assertEquals(2, retried.attempts)
        assertEquals("private body", RoomBetaEvidenceStore(db).find(id())!!.message.body)
    }

    @Test fun databaseReopenImmediatelyAfterCaptureLeavesRecoverableMarker() = runBlocking {
        val raw = CapturedEvidence(id(), request, message(), now, matched, selectionRows)
        RoomBetaEvidenceStore(db).capture(raw)
        reopen()
        assertEquals(EvidenceStage.CAPTURED, RoomBetaEvidenceStore(db).summary(id()).retryStage)
        assertEquals(EvidenceDisposition.COMPARISON_ONLY, workflow().retryPending().single().disposition)
        assertNull(RoomBetaEvidenceStore(db).summary(id()).retryStage)
    }

    @Test fun cancellationDuringInterpretationPropagatesAndRetainsMarkerForRestart() = runBlocking {
        try {
            workflow(compare = EvidenceComparer { _, _ -> throw CancellationException("cancel") })
                .importBatch(request, listOf(message()))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        reopen()
        assertEquals(EvidenceStage.INTERPRETATION, RoomBetaEvidenceStore(db).summary(id()).retryStage)
        workflow().retryPending()
        assertNull(RoomBetaEvidenceStore(db).summary(id()).retryStage)
    }

    @Test fun outcomeSqlFailureRollsBackAndCannotClearRetryOrOverwriteManualDecision() = runBlocking {
        val w = workflow()
        w.importBatch(request, listOf(message()))
        w.decide(id(), 0, EvidenceReviewDecision.KEEP_FOR_REVIEW)
        val firstOutcome = db.evidenceDao().outcome(id())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_outcome BEFORE INSERT ON beta_evidence_outcome BEGIN SELECT RAISE(ABORT,'injected'); END")
        val failed = w.reprocess(id())
        assertEquals(EvidenceStage.SAVE_OUTCOME, failed.retryStage)
        assertEquals(EvidenceDisposition.COMPARISON_ONLY, failed.disposition)
        assertEquals(EvidenceProcessingState.RETRY, failed.processingState)
        assertTrue("SAVE_OUTCOME_FAILED" in failed.reasonCodes)
        assertEquals(firstOutcome, db.evidenceDao().outcome(id()))
        assertEquals(1, failed.manualRevision)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_outcome")
        reopen()
        val recovered = workflow().retryPending().single()
        assertNull(recovered.retryStage)
        assertEquals(EvidenceProcessingState.COMPARISON_ONLY, recovered.processingState)
        assertEquals(1, RoomBetaEvidenceStore(db).summary(id()).manualRevision)
    }

    @Test fun failureClearingRetryRollsBackAnAlreadyWrittenComparison() = runBlocking {
        val w = workflow()
        w.importBatch(request, listOf(message()))
        val first = db.evidenceDao().outcome(id())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_clear BEFORE DELETE ON beta_evidence_retry BEGIN SELECT RAISE(ABORT,'injected'); END")
        val failed = w.reprocess(id())
        assertEquals(EvidenceStage.SAVE_OUTCOME, failed.retryStage)
        assertEquals(first, db.evidenceDao().outcome(id()))
        assertNotNull(db.evidenceDao().retry(id()))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_clear")
        reopen()
        assertNull(workflow().retryPending().single().retryStage)
    }

    @Test fun foreignKeysRejectAnOutcomeWithoutCapturedRaw() = runBlocking {
        try {
            db.evidenceDao().retry(BetaEvidenceRetryEntity("missing", EvidenceStage.CAPTURED.name, 0, "PENDING", now.toString()))
            fail("Foreign key must reject orphan retry")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertTrue(RoomBetaEvidenceStore(db).pending().isEmpty())
    }

    @Test fun captureTransactionFailureDoesNotLeaveAnOrphanRawRow() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_capture BEFORE INSERT ON beta_evidence_retry BEGIN SELECT RAISE(ABORT,'injected'); END")
        try { workflow().importBatch(request, listOf(message())); fail("Capture must fail") } catch (_: android.database.sqlite.SQLiteException) { }
        assertNull(db.evidenceDao().raw(id()))
        assertEquals(0, calls)
    }

    @Test fun committedComparisonSurvivesCancellationAfterWriteAndDuplicateImportDoesNotRerun() = runBlocking {
        val delegate = RoomBetaEvidenceStore(db)
        val interrupted = object : BetaEvidenceStore by delegate {
            override suspend fun complete(id: String, route: EvidenceRoute, comparison: EvidenceComparison, at: Instant) {
                delegate.complete(id, route, comparison, at)
                throw CancellationException("after durable comparison")
            }
        }
        try { workflow(store = interrupted).importBatch(request, listOf(message())); fail("Cancellation must propagate") }
        catch (_: CancellationException) { }
        reopen()
        workflow().importBatch(request, listOf(message()))
        assertEquals(1, calls)
        assertNull(RoomBetaEvidenceStore(db).summary(id()).retryStage)
    }

    @Test fun reparseAfterDisableKeepsCaptureScopeAndVersionedManualDecisionAcrossRestart() = runBlocking {
        val w = workflow()
        w.importBatch(request, listOf(message()))
        val capture = db.evidenceDao().raw(id())
        assertTrue(w.decide(id(), 0, EvidenceReviewDecision.ACKNOWLEDGE_COMPARISON))
        assertFalse(w.decide(id(), 0, EvidenceReviewDecision.KEEP_FOR_REVIEW))
        selectionRows = listOf(UserBankSelection(bank, BankSelectionState.DISABLED))
        reopen()
        val summary = workflow().reprocess(id())
        assertNull(summary.retryStage)
        assertEquals(1, summary.manualRevision)
        assertEquals(EvidenceReviewDecision.ACKNOWLEDGE_COMPARISON, summary.manualDecision)
        assertEquals(capture, db.evidenceDao().raw(id()))
        assertEquals(EvidenceReviewDecision.ACKNOWLEDGE_COMPARISON.name, db.evidenceDao().decision(id())!!.decision)
    }

    @Test fun fullLabRouterPreservesNormalizedMatchedSuspectedAndUnknown() {
        val router = LabEvidenceRouter()
        assertEquals(RouteKind.MATCHED, router.route(message().copy(sender = " Al-Jazira-AD ")).kind)
        assertEquals(RouteKind.SUSPECTED, router.route(message().copy(sender = "AlJaziraSomething")).kind)
        assertEquals(RouteKind.UNRECOGNIZED, router.route(message().copy(sender = "UnknownSender")).kind)
    }

    @Test fun restoreFreshStores() {
        db.close()
        val production = context.getDatabasePath(BetaEnvironment.PRODUCTION_DATABASE_NAME)
        production.parentFile?.mkdirs()
        production.writeText("production-canary")
        val sample = message(body = "POS Purchase\nCard: 1234\nAmount: 42 SAR")
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            val summary = stored(beta.importEvidence(request, listOf(sample)).single())
            beta.decideEvidence(summary.id, 0, EvidenceReviewDecision.KEEP_FOR_REVIEW)
            beta.backup()
            beta.decideEvidence(summary.id, 1, EvidenceReviewDecision.ACKNOWLEDGE_COMPARISON)
            assertTrue(beta.restore())
            assertEquals(1, beta.evidenceSummary(summary.id).manualRevision)
            assertNull(beta.reprocessEvidence(summary.id).retryStage)
            beta.reset()
            beta.setBankEnabled(bank, true)
            assertEquals(0, stored(beta.importEvidence(request, listOf(sample)).single()).manualRevision)
        }
        assertEquals("production-canary", production.readText())
    }
}
