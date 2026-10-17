package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.corrections.BetaCorrectionResult
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.dashboard.producer.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.time.*
import java.util.concurrent.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomBetaDashboardSourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BetaDatabase
    private val sql = CopyOnWriteArrayList<String>()
    private val bank = BankId("BANK_ALJAZIRA")
    private val asset = OwnedAsset(AssetId(Environment.BETA, bank, "account"), AssetKind.ACCOUNT, "1234")
    private val now = Instant.parse("2026-10-17T12:00:00Z")
    private val zone = ZoneId.of("Asia/Riyadh")
    private val query = DashboardQuery(Environment.BETA, now.minusSeconds(3600), now.plusSeconds(3600), zone, Currency.SAR)
    private val clock = Clock.fixed(now.plusSeconds(7200), ZoneOffset.UTC)
    private fun input(id: String = "transaction", time: Instant = now, revision: PostingRevision = PostingRevision(0, 0)) =
        FinancialClassificationInput(PostingKey(Environment.BETA, id), revision, FinancialType.EXPENSE, Money.of("42.35", Currency.SAR),
            TransactionTime(time, zone), FinancialEndpoint.Owned(asset.id), FinancialEndpoint.External,
            mapOf(asset.id to asset), setOf("evidence-$id"), true)
    private fun openDatabase(observer: (String) -> Unit = {}) = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME)
        .setQueryCallback({ statement, _ -> sql += statement; observer(statement) }, Executor { it.run() }).build()
    @Before fun open() = runBlocking {
        BetaContainer.resetOffline(context); db = openDatabase(); BetaAssetRegistry(db).register(asset)
    }
    @After fun close() { db.close(); BetaContainer.resetOffline(context) }
    private fun source(periods: DashboardPeriods = DashboardPeriods(), limits: BetaDashboardReadLimits = BetaDashboardReadLimits()) =
        RoomBetaDashboardSource(db, periods, limits, clock)
    private suspend fun post(value: FinancialClassificationInput) {
        assertTrue(RoomBetaFinancialLedger(db).post(value) is FinancialPostingResult.Applied)
    }
    private fun decision(value: FinancialClassificationInput, revision: Long, choice: FinancialDecision, metadata: Map<String, String> = emptyMap()) =
        CorrectionRequest("decision-${value.key.transactionId}-$revision", value.key, revision, value.revision.interpretation,
            value.evidenceIds, choice, metadata, revision - 1)
    private suspend fun correct(value: FinancialClassificationInput, revision: Long, choice: FinancialDecision,
        metadata: Map<String, String> = emptyMap()) = RoomBetaCorrectionLifecycle(db).submit(
            CorrectionObservation(value, ObservationSafety.ORDINARY), decision(value, revision, choice, metadata),
        ).also { assertTrue(it is BetaCorrectionResult.Applied) }
    private fun dataSelects() = sql.filter { it.trimStart().startsWith("SELECT", true) &&
        listOf("beta_asset", "beta_ledger_current", "beta_financial_adoption", "beta_evidence_retry", "beta_raw_evidence").any { table -> it.contains(table) } }
    private fun baselineReadQueries() = if (BetaDatabase.VERSION >= 7) 6 else 5
    private fun ensureAdoptionTables() {
        db.openHelper.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS beta_financial_adoption (" +
            "evidenceId TEXT PRIMARY KEY NOT NULL,transactionId TEXT NOT NULL,bankId TEXT NOT NULL,archiveFingerprint TEXT NOT NULL," +
            "status TEXT NOT NULL,reason TEXT NOT NULL,approvedInput TEXT NOT NULL,correction INTEGER NOT NULL,interpretation INTEGER NOT NULL," +
            "approvalRequest TEXT NOT NULL,observationRevision INTEGER NOT NULL)")
        db.openHelper.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS beta_financial_approval_retry (evidenceId TEXT PRIMARY KEY NOT NULL,request TEXT NOT NULL)")
    }
    private suspend fun rawFixture(id: String) {
        db.evidenceDao().insertRaw(BetaRawEvidenceEntity(id, "dashboard-fixture", id, "EXPLICIT_SAMPLE", "fixture-bank", "synthetic body never read",
            now.toString(), "USER_SUPPLIED", now.toString(), "{}", "[]", "[]"))
    }
    private suspend fun adoptionFixture(id: String, transaction: String, status: String) {
        ensureAdoptionTables(); rawFixture(id)
        db.openHelper.writableDatabase.execSQL("INSERT INTO beta_financial_adoption " +
            "(evidenceId,transactionId,bankId,archiveFingerprint,status,reason,approvedInput,correction,interpretation,approvalRequest,observationRevision) " +
            "VALUES (?,?,?,?,?,'SOURCE_CONTRACT_FIXTURE','',0,0,'',0)", arrayOf(id, transaction, bank.value, "fixture", status))
    }

    @Test fun readsRealCurrentRowsWithHalfOpenScopeAndIndependentSalaryWindow() = runBlocking {
        post(input("before", query.from.minusNanos(1))); post(input("from", query.from))
        post(input("inside")); post(input("until", query.until))
        val selected = source().load(query)
        assertEquals(listOf("from", "inside"), selected.rows.map { it.posting.command.key.transactionId })
        val salary = DisplayPeriod(query.from.minusSeconds(100), query.until.plusSeconds(100), zone, PeriodKind.SALARY, null)
        val wider = source(DashboardPeriods(salary = salary)).load(query)
        assertEquals(4, wider.rows.size)
        val result = BetaDashboardBuilder.build(wider)
        assertEquals(2, result.snapshot.transactions.size)
        assertEquals(BigDecimal("84.70"), result.snapshot.fleetFlow!!.externalOut.amount)
        assertFalse(DashboardSection.RUNTIME_SOURCE in result.unavailableSections)
        assertNull(result.snapshot.analysis); assertNull(result.snapshot.commitments)
    }

    @Test fun readsAreStableReadOnlyAndDoNotClaimAbsentObservationFeeOrReviewCoverage() = runBlocking {
        post(input())
        sql.clear()
        val first = source().load(query); val second = source().load(query)
        assertEquals(first, second)
        assertEquals(2 * baselineReadQueries(), dataSelects().size)
        assertTrue(sql.none { statement -> statement.contains("beta_") &&
            listOf("INSERT", "UPDATE", "DELETE").any { statement.trimStart().startsWith(it, true) } })
        assertTrue(sql.none { it.contains("r.body") || it.contains("beta_ledger_history") })
        assertFalse(first.coverage.completeFeeFacts); assertNull(first.coverage.reviewRequiredCount)
        val produced = BetaDashboardBuilder.build(first)
        assertNull(produced.financialSummary)
        assertNull(produced.snapshot.accounts.single().balance)
        assertNull(produced.snapshot.accounts.single().balanceAsOf)
        assertEquals(1, RoomBetaFinancialLedger(db).history(input().key).size)
        try { (first.rows as MutableList<*>).clear(); fail("Snapshot must be frozen") } catch (_: UnsupportedOperationException) { }
    }

    @Test fun sourceRevisionIncludesExactMoneyRegistryAndCorrectionMetadataAcrossReopen() = runBlocking {
        val value = input(); post(value)
        val original = source().load(query)
        correct(value, 1, FinancialDecision.Keep, mapOf("merchant" to "Confirmed store", "counterparty" to "Known recipient"))
        val corrected = source().load(query)
        assertNotEquals(original.revision.token, corrected.revision.token)
        assertEquals("Confirmed store", BetaDashboardBuilder.build(corrected).snapshot.transactions.single().merchant)
        assertEquals("Known recipient", corrected.postingFacts.single().counterparty)
        db.close(); db = openDatabase()
        assertEquals(corrected, source().load(query))
        assertEquals(2, RoomBetaFinancialLedger(db).history(value.key).size)
        BetaAssetRegistry(db).register(asset.copy(id = asset.id.copy(localId = "new-account")))
        assertNotEquals(corrected.revision.token, source().load(query).revision.token)
    }

    @Test fun suppressionAndRestorationChangeVisibilityAtomicallyWithoutReadingAuditRows() = runBlocking {
        val value = input(); post(value)
        val before = source().load(query)
        correct(value, 1, FinancialDecision.SuppressAsNonFinancial)
        sql.clear()
        val suppressed = source().load(query)
        assertTrue(suppressed.rows.isEmpty()); assertNotEquals(before.revision.token, suppressed.revision.token)
        assertEquals(BigDecimal("0.00"), BetaDashboardBuilder.build(suppressed).snapshot.fleetFlow!!.externalOut.amount)
        assertTrue(sql.none { it.contains("beta_ledger_history") || it.contains("beta_correction_audit") })
        db.close(); db = openDatabase()
        assertEquals(suppressed, source().load(query))
        correct(value, 2, FinancialDecision.ResolveFinancialType(FinancialType.EXPENSE))
        val restored = source().load(query)
        assertEquals(1, restored.rows.size)
        assertEquals(BigDecimal("42.35"), BetaDashboardBuilder.build(restored).snapshot.fleetFlow!!.externalOut.amount)
    }

    @Test fun securityRebaseHidesCurrentFinancialSnapshotAndChangesRevision() = runBlocking {
        val value = input(); correct(value, 1, FinancialDecision.ResolveFinancialType(FinancialType.EXPENSE))
        val before = source().load(query)
        val newer = value.copy(revision = PostingRevision(0, 1))
        assertTrue(RoomBetaCorrectionLifecycle(db).rebase(CorrectionObservation(newer, ObservationSafety.SECURITY), 1) is BetaCorrectionResult.Applied)
        val held = source().load(query)
        assertTrue(held.rows.isEmpty()); assertNotEquals(before.revision.token, held.revision.token)
        assertFalse(held.coverage.completeLedger)
        assertNull(BetaDashboardBuilder.build(held).snapshot.fleetFlow)
        assertNotNull(RoomBetaFinancialLedger(db).readAudit(value.key))
    }

    @Test fun registryMutationIsExplicitlyIncompleteInsteadOfCrashingOrReturningKnownZero() = runBlocking {
        post(input())
        assertEquals(1, source().load(query).rows.size)
        val row = db.assetDao().get("BETA", bank.value, asset.id.localId)!!
        assertEquals(1, db.assetDao().update(row.copy(last4 = "9999")))
        val changed = source().load(query)
        assertFalse(changed.coverage.completeLedger); assertTrue(changed.rows.isEmpty())
        val result = BetaDashboardBuilder.build(changed)
        assertTrue(DashboardSection.CURRENT_LEDGER in result.unavailableSections)
        assertNull(result.snapshot.accounts.single().flow)
        assertNull(result.snapshot.totals!!.accountOutflow)
    }

    @Test fun unrelatedRegistryEditDoesNotBlockAValidFinancialPostingOrPureProjection() = runBlocking {
        val unrelated = asset.copy(id = asset.id.copy(localId = "unrelated"), last4 = "5678")
        BetaAssetRegistry(db).register(unrelated)
        post(input().copy(assets = mapOf(asset.id to asset, unrelated.id to unrelated)))
        val row = db.assetDao().get("BETA", bank.value, unrelated.id.localId)!!
        db.assetDao().update(row.copy(last4 = "9999"))
        val batch = source().load(query)
        assertTrue(batch.coverage.completeLedger); assertEquals(1, batch.rows.size)
        assertEquals(BigDecimal("42.35"), BetaDashboardBuilder.build(batch).snapshot.fleetFlow!!.externalOut.amount)
    }

    @Test fun requiredCardParentEditInvalidatesFinancialCoverageEvenIfCardItselfIsUnchanged() = runBlocking {
        val card = OwnedAsset(asset.id.copy(localId = "debit-card"), AssetKind.DEBIT_CARD, "5678", asset.id)
        BetaAssetRegistry(db).register(card)
        post(input().copy(source = FinancialEndpoint.Owned(card.id), assets = mapOf(asset.id to asset, card.id to card)))
        val parent = db.assetDao().get("BETA", bank.value, asset.id.localId)!!
        db.assetDao().update(parent.copy(last4 = "9999"))
        val batch = source().load(query)
        assertFalse(batch.coverage.completeLedger); assertTrue(batch.rows.isEmpty())
        assertNull(BetaDashboardBuilder.build(batch).snapshot.fleetFlow)
    }

    @Test fun optionalAdoptionGateHidesHeldRowsAndUnknownStatesFailClosed() = runBlocking {
        post(input())
        // Adjacent-schema capability contract. The owning M7 PR supplies the real v6→v7 migration.
        adoptionFixture("evidence-transaction", "transaction", "ACTIVE")
        val active = source().load(query)
        assertEquals(1, active.rows.size)
        db.openHelper.writableDatabase.execSQL("UPDATE beta_financial_adoption SET status='REVIEW'")
        sql.clear()
        val held = source().load(query)
        assertEquals(6, dataSelects().size)
        assertTrue(held.rows.isEmpty()); assertFalse(held.coverage.completeLedger)
        assertNull(BetaDashboardBuilder.build(held).snapshot.fleetFlow)
        assertNotEquals(active.revision.token, held.revision.token)
        db.openHelper.writableDatabase.execSQL("UPDATE beta_financial_adoption SET status='UNKNOWN_FUTURE_STATE'")
        try { source().load(query); fail() } catch (_: IllegalStateException) { }
    }

    @Test fun missingMandatoryV7GateFailsClosed() = runBlocking {
        if (BetaDatabase.VERSION >= 7) db.openHelper.writableDatabase.execSQL("DROP TABLE beta_financial_approval_retry")
        db.openHelper.writableDatabase.version = 7
        try { source().load(query); fail() } catch (_: IllegalStateException) { }
        finally { db.openHelper.writableDatabase.version = BetaDatabase.VERSION }
    }

    @Test fun approvalRetryOrHeldAdoptionWithoutAnyLedgerRowNeverProducesKnownFinancialZero() = runBlocking {
        ensureAdoptionTables(); rawFixture("not-posted")
        db.openHelper.writableDatabase.execSQL("INSERT INTO beta_financial_approval_retry VALUES ('not-posted','durable request')")
        val retry = source().load(query)
        assertFalse(retry.coverage.completeLedger); assertNull(BetaDashboardBuilder.build(retry).snapshot.fleetFlow)
        db.openHelper.writableDatabase.execSQL("DELETE FROM beta_financial_approval_retry")
        adoptionFixture("not-posted", "no-financial-row", "REVIEW")
        val held = source().load(query)
        assertFalse(held.coverage.completeLedger); assertNull(BetaDashboardBuilder.build(held).snapshot.fleetFlow)
    }

    @Test fun foreignSmsRateWithoutPersistedFeeCoverageKeepsFinancialTotalsUnknown() = runBlocking {
        post(input().copy(amount = Money.of("10", Currency.USD), appliedFx = AppliedFx(BigDecimal("3.75"), "SMS")))
        val output = BetaDashboardBuilder.build(source().load(query))
        assertNull(output.snapshot.fleetFlow)
        assertNull(output.snapshot.totals!!.accountOutflow)
        assertEquals(BigDecimal("37.50"), output.snapshot.transactions.single().sarEquivalent!!.amount)
        assertNull(output.snapshot.transactions.single().internationalFee)
    }

    @Test fun currentAndRegistryLimitsRefusePartialResultsEvenWhenOverflowIsOutsideSelectedPeriod() = runBlocking {
        post(input("first")); post(input("outside", query.from.minusSeconds(100)))
        try { source(limits = BetaDashboardReadLimits(currentRows = 1)).load(query); fail() }
        catch (expected: BetaDashboardReadLimitExceeded) { assertEquals("current ledger rows", expected.collection) }
        BetaAssetRegistry(db).register(asset.copy(id = asset.id.copy(localId = "second")))
        try { source(limits = BetaDashboardReadLimits(assets = 1)).load(query); fail() }
        catch (expected: BetaDashboardReadLimitExceeded) { assertEquals("confirmed assets", expected.collection) }
    }

    @Test fun corruptActivePayloadFailsClosedButSuppressedAuditPayloadDoesNotReenterFinancialRead() = runBlocking {
        val value = input(); post(value)
        val stored = db.ledgerDao().current("BETA", value.key.transactionId)!!
        db.ledgerDao().writeCurrent(stored.copy(payload = "corrupt"))
        try { source().load(query); fail() } catch (_: IllegalStateException) { }
        db.ledgerDao().writeCurrent(stored)
        correct(value, 1, FinancialDecision.SuppressAsNonFinancial)
        db.ledgerDao().writeCurrent(stored.copy(payload = "corrupt audit snapshot"))
        assertTrue(source().load(query).rows.isEmpty())
    }

    @Test fun untrustedActiveCorrectionRevisionMismatchCannotPublishStaleLedger() = runBlocking {
        val value = input(); correct(value, 1, FinancialDecision.ResolveFinancialType(FinancialType.EXPENSE))
        val stored = db.ledgerDao().current("BETA", value.key.transactionId)!!
        db.ledgerDao().writeCurrent(stored.copy(correction = 0))
        try { source().load(query); fail() } catch (_: IllegalStateException) { }
    }

    @Test fun fixedQueryCountIgnoresLongAppendOnlyHistoryAndDoesNotTouchRawEvidence() = runBlocking {
        repeat(80) { post(input("transaction-$it")) }
        val row = db.ledgerDao().current("BETA", "transaction-0")!!
        repeat(600) { index -> db.ledgerDao().appendHistory(BetaLedgerHistoryEntity("BETA", row.transactionId, 99, index.toLong(), "unused archive payload")) }
        sql.clear()
        val batch = source().load(query)
        assertEquals(80, batch.rows.size); assertEquals(baselineReadQueries(), dataSelects().size)
        assertTrue(sql.none { it.contains("beta_ledger_history") || it.contains("r.body") || it.contains("beta_evidence_outcome") })
        val plans = db.query("EXPLAIN QUERY PLAN SELECT l.* FROM beta_ledger_current l LEFT JOIN beta_correction_current c " +
            "ON c.environment=l.environment AND c.transactionId=l.transactionId WHERE l.environment='BETA' ORDER BY l.transactionId LIMIT 10001", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }
        }
        assertTrue(plans.toString(), plans.any { it.contains("SEARCH") && it.contains("USING") && !it.contains("AUTOMATIC") })
        assertTrue(plans.none { it.contains("SCAN beta_ledger_history") || it.contains("TEMP B-TREE") })
    }

    @Test fun rejectsOtherEnvironmentsBeforeAnyDatabaseRead() = runBlocking {
        sql.clear()
        try { source().load(query.copy(environment = Environment.MIGRATION_REHEARSAL)); fail() } catch (_: IllegalArgumentException) { }
        assertTrue(dataSelects().isEmpty())
    }

    @Test fun completedComparisonIsNotFinancialCompletionAndAcknowledgementDoesNotCreateKnownZero() = runBlocking {
        val store = RoomBetaEvidenceStore(db)
        val route = EvidenceRoute(RouteKind.MATCHED, setOf(bank), "fixture-router-v1")
        val captured = CapturedEvidence("not-yet-approved", EvidenceRequest("fixture", EvidenceOrigin.EXPLICIT_SAMPLE),
            EvidenceMessage("provider", "fixture-bank", "synthetic unused body", now, ReceiptTimeSource.USER_SUPPLIED), now, route, emptyList())
        assertEquals(CaptureState.INSERTED, store.capture(captured))
        store.begin(captured.id, now)
        store.complete(captured.id, route, EvidenceComparison(EvidenceDisposition.COMPARISON_ONLY, emptySet(), "{}", "{}"), now)
        assertTrue(store.decide(captured.id, 0, EvidenceReviewDecision.ACKNOWLEDGE_COMPARISON, now))
        val batch = source().load(query)
        assertFalse(batch.coverage.completeLedger)
        assertNull(BetaDashboardBuilder.build(batch).snapshot.fleetFlow)
        assertNull(BetaDashboardBuilder.build(batch).snapshot.totals!!.accountOutflow)
    }

    @Test fun explicitNonFinancialSuppressionBeforeFirstPostingDischargesOnlyItsExactRawEvidence() = runBlocking {
        val store = RoomBetaEvidenceStore(db)
        val route = EvidenceRoute(RouteKind.MATCHED, setOf(bank), "fixture-router-v1")
        val value = input().copy(evidenceIds = setOf("suppressed-raw"))
        val captured = CapturedEvidence("suppressed-raw", EvidenceRequest("fixture", EvidenceOrigin.EXPLICIT_SAMPLE),
            EvidenceMessage("provider", "fixture-bank", "synthetic unused body", now, ReceiptTimeSource.USER_SUPPLIED), now, route, emptyList())
        store.capture(captured); store.begin(captured.id, now)
        store.complete(captured.id, route, EvidenceComparison(EvidenceDisposition.COMPARISON_ONLY, emptySet(), "{}", "{}"), now)
        correct(value, 1, FinancialDecision.SuppressAsNonFinancial)
        val batch = source().load(query)
        assertTrue(batch.coverage.completeLedger); assertTrue(batch.rows.isEmpty())
        assertEquals(BigDecimal("0.00"), BetaDashboardBuilder.build(batch).snapshot.fleetFlow!!.externalOut.amount)
        val correction = db.correctionDao().current("BETA", value.key.transactionId)!!
        db.correctionDao().current(correction.copy(payload = "corrupt suppressed decision"))
        try { source().load(query); fail() } catch (_: org.json.JSONException) { }
    }
}
