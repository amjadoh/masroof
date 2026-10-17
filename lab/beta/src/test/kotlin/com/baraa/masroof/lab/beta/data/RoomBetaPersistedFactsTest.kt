package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.corrections.BetaCorrectionResult
import com.baraa.masroof.lab.beta.application.corrections.BetaReviewWorkflow
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.application.interpretation.LegacyInterpretationAdapter
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.dashboard.producer.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class RoomBetaPersistedFactsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BetaDatabase
    private val bank = BankId("BANK_ALJAZIRA")
    private val account = OwnedAsset(AssetId(Environment.BETA, bank, "account"), AssetKind.ACCOUNT, "1234")
    private val debit = OwnedAsset(AssetId(Environment.BETA, bank, "mada"), AssetKind.DEBIT_CARD, "5678", account.id)
    private val facility = OwnedAsset(AssetId(Environment.BETA, bank, "facility"), AssetKind.CREDIT_FACILITY)
    private val credit = OwnedAsset(AssetId(Environment.BETA, bank, "credit"), AssetKind.CREDIT_CARD, "7271", facility.id, CreditCardRole.PRIMARY)
    private val at = Instant.parse("2026-08-06T17:22:00Z")
    private val zone = ZoneId.of("Asia/Riyadh")
    private val query = DashboardQuery(Environment.BETA, at.minusSeconds(3600), at.plusSeconds(7200), zone, Currency.SAR)
    private fun openDb() = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
    @Before fun open() = runBlocking {
        BetaContainer.resetOffline(context); db = openDb()
        db.bankSelectionDao().upsert(BetaBankSelectionEntity(bank.value, "ENABLED"))
        for (asset in listOf(account, debit, facility, credit)) BetaAssetRegistry(db).register(asset)
    }
    @After fun close() { db.close(); BetaContainer.resetOffline(context) }
    private fun source() = RoomBetaDashboardSource(db, DashboardPeriods(salary = DisplayPeriod(query.from, query.until, zone, PeriodKind.SALARY, null)),
        clock = Clock.fixed(at.plusSeconds(10800), ZoneOffset.UTC))
    private fun facts() = Facts(MessageFamily.PURCHASE, Money.of("23", Currency.USD), MoneyDirection.OUTGOING,
        card = CardReference(bank, "7271"), cardChannel = CardChannel.CREDIT, merchant = "Synthetic Store",
        occurredAtLocal = LocalDateTime.ofInstant(at, zone), exchangeRate = BigDecimal("3.756957"),
        internationalFee = Money.of("1.99", Currency.SAR), availableBalance = Money.of("16958.89", Currency.SAR),
        outstandingBalance = Money.of("2694.32", Currency.SAR), paymentDueDate = LocalDate.parse("2026-08-25"))
    private suspend fun capture(id: String, facts: Facts = facts()) {
        val store = RoomBetaEvidenceStore(db)
        val route = EvidenceRoute(RouteKind.MATCHED, setOf(bank), "fixture")
        store.capture(CapturedEvidence(id, EvidenceRequest("fixture", EvidenceOrigin.EXPLICIT_SAMPLE),
            EvidenceMessage(id, "AlJazira", "synthetic", at, ReceiptTimeSource.USER_SUPPLIED), at, route, emptyList()))
        store.begin(id, at)
        val archive = InterpretationArchive.encode(Interpretation(id, LegacyInterpretationAdapter.VERSION, bank, facts,
            ParseStatus.SUCCESS, null, emptyList(), emptySet()))
        store.complete(id, route, EvidenceComparison(EvidenceDisposition.COMPARISON_ONLY, emptySet(), archive, "candidate untrusted"), at)
    }
    private suspend fun approve(id: String, asset: OwnedAsset = credit) {
        val service = RoomBetaFinancialAdoption(db)
        val item = service.reviewPage().single { it.evidenceId == id }
        assertTrue(service.approve(BetaFinancialApproval(id, item.archiveRevision, null, FinancialType.EXPENSE, asset.id, null)) is BetaFinancialAdoptionResult.Posted)
    }

    @Test fun actualPreservedLegacyParserFeeFxAndObservationsReachRealSqlWithoutFinancialRecomputation() = runBlocking {
        val body = """شراء عبر الانترنت
بطاقة ائتمانية: 7271
لدى: Synthetic Store
بمبلغ: USD 23.00
في: 2026-08-06 20:22
الدولة: USA
رسوم العمليات الدولية: 1.99
سعر الصرف: 3.756957
الرصيد المتاح: SAR 16958.89
إجمالي المبلغ المستحق: SAR 2694.32"""
        val stored = ProcessBetaEvidence(RoomBetaEvidenceStore(db), RoomBetaBankSelectionStore(db), LabEvidenceRouter(), LabEvidenceComparer())
            .importBatch(EvidenceRequest("actual", EvidenceOrigin.EXPLICIT_SAMPLE), listOf(EvidenceMessage("one", "AlJazira", body, at,
                ReceiptTimeSource.USER_SUPPLIED))).single() as EvidenceResult.Stored
        approve(stored.summary.id)
        val batch = source().load(query); val result = BetaDashboardBuilder.build(batch)
        assertTrue(batch.coverage.completeFeeFacts); assertEquals(0, batch.coverage.reviewRequiredCount)
        assertEquals(Money.of("1.99", Currency.SAR), batch.fees.single().amount)
        assertEquals(BigDecimal("86.41"), result.snapshot.transactions.single().sarEquivalent!!.amount)
        assertEquals(BigDecimal("88.40"), result.financialSummary!!.metrics.getValue(SummaryMetric.CREDIT_PURCHASES)!!.amount)
        assertEquals(BigDecimal("16958.89"), result.snapshot.creditFacilities.single().cards.single().availableBalance!!.amount)
        assertNull(result.snapshot.creditFacilities.single().statementDue) // Purchase outstanding is not a statement due.
        assertNull(result.snapshot.accounts.single().balance)
        assertNotNull(result.snapshot.analysis)
        db.close(); db = openDb()
        assertEquals(batch, source().load(query))
        assertEquals(1, RoomBetaFinancialLedger(db).history(RoomBetaFinancialAdoption.key(stored.summary.id)).size)
    }

    @Test fun accountBackedMadaUsesExactConfirmedCardRoleWithoutMovingFinancialEndpoint() = runBlocking {
        capture("mada", facts().copy(amount = Money.of("42.35", Currency.SAR), exchangeRate = null, internationalFee = null,
            sourceAccount = AccountReference(bank, "1234"), card = CardReference(bank, "5678"), cardChannel = CardChannel.DEBIT,
            availableBalance = Money.of("900", Currency.SAR), outstandingBalance = null))
        approve("mada", account)
        val batch = source().load(query); val result = BetaDashboardBuilder.build(batch)
        assertEquals(debit.id, batch.postingFacts.single().debitCard)
        val tx = result.snapshot.transactions.single()
        assertEquals(setOf(debit.id), tx.debitSpendingCards)
        assertEquals(BigDecimal("42.35"), result.snapshot.debitCards.single().spending!!.amount)
        assertEquals(BigDecimal("900.00"), result.snapshot.accounts.single().balance!!.amount)
        assertEquals(account.id, batch.rows.single().posting.classification.input.let { (it.source as com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Owned).id })
        assertNull(result.snapshot.creditFacilities.single().statementDue)
    }

    @Test fun manualAmountOrTimeEditMakesFeeUnknownAndSuppressRestoreNeverReturnsAnOldRevision() = runBlocking {
        capture("edit"); approve("edit")
        val key = RoomBetaFinancialAdoption.key("edit")
        var detail = BetaReviewWorkflow(db).detail(key)!!
        assertTrue(BetaReviewWorkflow(db).save(detail.token, "amount", FinancialDecision.ConfirmFamilyOrAmount(amount = Money.of("30", Currency.USD))) is BetaCorrectionResult.Applied)
        val edited = source().load(query)
        assertFalse(edited.coverage.completeFeeFacts); assertTrue(edited.fees.isEmpty())
        assertNull(BetaDashboardBuilder.build(edited).snapshot.creditFacilities.single().cards.single().availableBalance)
        assertEquals(2L, db.dashboardFactsDao().evidence("edit")!!.correction)
        detail = BetaReviewWorkflow(db).detail(key)!!
        assertTrue(BetaReviewWorkflow(db).save(detail.token, "suppress", FinancialDecision.SuppressAsNonFinancial) is BetaCorrectionResult.Applied)
        val suppressed = BetaDashboardBuilder.build(source().load(query))
        assertTrue(suppressed.snapshot.transactions.isEmpty()); assertNull(suppressed.snapshot.creditFacilities.single().statementDue)
        detail = BetaReviewWorkflow(db).detail(key)!!
        assertTrue(BetaReviewWorkflow(db).save(detail.token, "restore", FinancialDecision.ResolveFinancialType(FinancialType.EXPENSE)) is BetaCorrectionResult.Applied)
        assertFalse(source().load(query).coverage.completeFeeFacts)
    }

    @Test fun newerHeldObservationPreventsFallingBackToOlderBalanceAndDeduplicatesReview() = runBlocking {
        capture("old", facts().copy(occurredAtLocal = LocalDateTime.ofInstant(at.minusSeconds(60), zone))); approve("old")
        capture("new"); approve("new")
        assertNull(BetaDashboardBuilder.build(source().load(query)).snapshot.creditFacilities.single().statementDue)
        RoomBetaEvidenceStore(db).begin("new", at)
        val held = source().load(query)
        assertFalse(held.coverage.completeLedger); assertEquals(1, held.coverage.reviewRequiredCount)
        assertNull(BetaDashboardBuilder.build(held).snapshot.creditFacilities.single().statementDue)
        assertNull(RoomBetaFinancialLedger(db).read(RoomBetaFinancialAdoption.key("new")))
        assertNotNull(RoomBetaFinancialLedger(db).readAudit(RoomBetaFinancialAdoption.key("new")))
    }

    @Test fun missingProjectionNeedsExplicitIdempotentWriteSideBackfill() = runBlocking {
        capture("one"); approve("one")
        db.openHelper.writableDatabase.execSQL("DELETE FROM beta_dashboard_evidence_facts")
        assertFalse(source().load(query).coverage.completeFeeFacts)
        assertEquals(1, RoomBetaDashboardFacts(db).refreshPage(limit = 1).refreshed)
        val expected = source().load(query)
        assertTrue(expected.coverage.completeFeeFacts)
        assertEquals(1, RoomBetaDashboardFacts(db).refreshPage(limit = 1).refreshed)
        assertEquals(expected, source().load(query))
        assertEquals(1, RoomBetaFinancialLedger(db).history(RoomBetaFinancialAdoption.key("one")).size)
    }

    @Test fun identicalLocalIdsAndLast4InAnotherBankCannotReceiveBalanceDuesOrMadaFacts() = runBlocking {
        val other = BankId("A_OTHER_BANK")
        for (asset in listOf(account, debit, facility, credit)) BetaAssetRegistry(db).register(asset.copy(
            id = asset.id.copy(bank = other), parentId = asset.parentId?.copy(bank = other)))
        capture("credit"); approve("credit")
        capture("debit", facts().copy(amount = Money.of("10", Currency.SAR), exchangeRate = null, internationalFee = null,
            sourceAccount = AccountReference(bank, "1234"), card = CardReference(bank, "5678"), cardChannel = CardChannel.DEBIT))
        approve("debit", account)
        val result = BetaDashboardBuilder.build(source().load(query))
        assertNull(result.snapshot.creditFacilities.single { it.facilityId.bank == other }.statementDue)
        assertNull(result.snapshot.creditFacilities.single { it.facilityId.bank == other }.cards.single().availableBalance)
        assertNull(result.snapshot.accounts.single { it.id.bank == other }.balance)
        assertEquals(setOf(debit.id), result.snapshot.transactions.single { it.key.transactionId == "evidence:debit" }.debitSpendingCards)
        assertEquals(0, result.snapshot.debitCards.single { it.cardId.bank == other }.spending!!.amount.compareTo(BigDecimal.ZERO))
    }

    @Test fun unapprovedInformationalEvidenceDoesNotInventAnAuthoritativeGlobalReviewCount() = runBlocking {
        capture("notice", facts().copy(family = MessageFamily.BALANCE_NOTICE))
        val batch = source().load(query)
        assertFalse(batch.coverage.completeLedger); assertNull(batch.coverage.reviewRequiredCount)
        assertNull(BetaDashboardBuilder.build(batch).financialSummary)
        assertTrue(batch.assetFacts.isEmpty())
    }

    @Test fun absenceIsKnownOnlyFromExactBaselineWhileNonSarFeeStaysUnknown() = runBlocking {
        capture("none", facts().copy(internationalFee = null)); approve("none")
        val noFee = source().load(query)
        assertTrue(noFee.coverage.completeFeeFacts); assertTrue(noFee.fees.isEmpty())
        assertEquals(BigDecimal("86.41"), BetaDashboardBuilder.build(noFee).financialSummary!!.metrics.getValue(SummaryMetric.CREDIT_PURCHASES)!!.amount)
        capture("other", facts().copy(internationalFee = Money.of("1", Currency.USD))); approve("other")
        val unavailable = source().load(query)
        assertFalse(unavailable.coverage.completeFeeFacts)
        assertTrue(unavailable.fees.isEmpty())
        assertNull(BetaDashboardBuilder.build(unavailable).financialSummary!!.metrics.getValue(SummaryMetric.CREDIT_PURCHASES))
    }

    @Test fun changedConfirmedMadaCardCannotRetainItsOldRoleAttribution() = runBlocking {
        capture("debit", facts().copy(amount = Money.of("10", Currency.SAR), exchangeRate = null, internationalFee = null,
            sourceAccount = AccountReference(bank, "1234"), card = CardReference(bank, "5678"), cardChannel = CardChannel.DEBIT))
        approve("debit", account)
        assertEquals(debit.id, source().load(query).postingFacts.single().debitCard)
        BetaAssetRegistry(db).save(debit.copy(last4 = "9999"), true, debit)
        val batch = source().load(query)
        assertNull(batch.postingFacts.single().debitCard)
        assertNull(BetaDashboardBuilder.build(batch).snapshot.debitCards.single().spending)
        assertEquals(account.id, (batch.rows.single().posting.classification.input.source as com.baraa.masroof.lab.finance.classification.FinancialEndpoint.Owned).id)
    }

    @Test fun factsWriteFailureRollsBackTheFinancialApprovalAndCanRetryExactDurableIntent() = runBlocking {
        capture("fault")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_facts BEFORE INSERT ON beta_dashboard_evidence_facts BEGIN SELECT RAISE(ABORT, 'synthetic fault'); END")
        try { approve("fault"); fail("Expected facts persistence failure") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertNull(RoomBetaFinancialLedger(db).readAudit(RoomBetaFinancialAdoption.key("fault")))
        assertNull(db.financialAdoptionDao().evidence("fault")); assertNull(db.dashboardFactsDao().evidence("fault"))
        assertNotNull(db.financialAdoptionDao().retry("fault"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_facts")
        assertTrue(RoomBetaFinancialAdoption(db).retryApproval("fault") is BetaFinancialAdoptionResult.Posted)
        assertTrue(source().load(query).coverage.completeFeeFacts)
    }

    @Test fun newestEditedOrSuppressedObservationKeepsTargetTombstoneInsteadOfRevivingOldDue() = runBlocking {
        capture("old", facts().copy(occurredAtLocal = LocalDateTime.ofInstant(at.minusSeconds(60), zone))); approve("old")
        capture("new"); approve("new")
        val key = RoomBetaFinancialAdoption.key("new")
        var detail = BetaReviewWorkflow(db).detail(key)!!
        assertTrue(BetaReviewWorkflow(db).save(detail.token, "time", FinancialDecision.EditFacts(time = TransactionTime(at.plusSeconds(60), zone))) is BetaCorrectionResult.Applied)
        assertEquals(facility.id.localId, db.dashboardFactsDao().evidence("new")!!.facilityAsset)
        assertNull(BetaDashboardBuilder.build(source().load(query)).snapshot.creditFacilities.single().statementDue)
        detail = BetaReviewWorkflow(db).detail(key)!!
        assertTrue(BetaReviewWorkflow(db).save(detail.token, "stop", FinancialDecision.SuppressAsNonFinancial) is BetaCorrectionResult.Applied)
        assertNull(BetaDashboardBuilder.build(source().load(query)).snapshot.creditFacilities.single().statementDue)
        db.close(); db = openDb()
        assertNull(BetaDashboardBuilder.build(source().load(query)).snapshot.creditFacilities.single().statementDue)
    }

    @Test fun sameTimeConflictingDuesAreUnknownAndFutureObservationDoesNotReplaceScopedPast() = runBlocking {
        capture("one"); approve("one")
        capture("two", facts().copy(outstandingBalance = Money.of("999", Currency.SAR))); approve("two")
        assertNull(BetaDashboardBuilder.build(source().load(query)).snapshot.creditFacilities.single().statementDue)
        capture("future", facts().copy(occurredAtLocal = LocalDateTime.ofInstant(query.until.plusSeconds(60), zone))); approve("future")
        assertNull(BetaDashboardBuilder.build(source().load(query)).snapshot.creditFacilities.single().statementDue)
        assertEquals(2, source().load(query).rows.size)
    }

    @Test fun pendingApprovalHidesExistingPostingAndFactsAndCountsOneReviewIdentity() = runBlocking {
        capture("pending"); approve("pending")
        val adopted = db.financialAdoptionDao().evidence("pending")!!
        db.financialAdoptionDao().retry(BetaFinancialApprovalRetryEntity("pending", adopted.approvalRequest))
        val pending = source().load(query)
        assertTrue(pending.rows.isEmpty()); assertFalse(pending.coverage.completeLedger)
        assertEquals(1, pending.coverage.reviewRequiredCount)
        assertNull(BetaDashboardBuilder.build(pending).snapshot.creditFacilities.single().statementDue)
        assertTrue(RoomBetaFinancialAdoption(db).retryApproval("pending") is BetaFinancialAdoptionResult.Posted)
        assertEquals(1, source().load(query).rows.size)
        assertEquals(0, source().load(query).coverage.reviewRequiredCount)
    }

    @Test fun contradictoryStructuredAccountSuffixKeepsMadaUnknownAndPreservesExplicitEndpoint() = runBlocking {
        val base = facts().copy(amount = Money.of("10", Currency.SAR), exchangeRate = null, internationalFee = null,
            sourceAccount = AccountReference(bank, "9999"), card = CardReference(bank, "5678"), cardChannel = CardChannel.DEBIT)
        val conflicts = listOf(base, base.copy(debitSourceAccountLast4 = "1234"),
            base.copy(sourceAccount = AccountReference(bank, "unparseable1234")),
            base.copy(sourceAccount = AccountReference(BankId("OTHER_BANK"), "1234")),
            base.copy(sourceAccount = AccountReference(bank, "1234"), card = CardReference(BankId("OTHER_BANK"), "5678")))
        for ((index, conflicting) in conflicts.withIndex()) {
            val id = "contradiction$index"; capture(id, conflicting); approve(id, account)
            val key = RoomBetaFinancialAdoption.key(id)
            val batch = source().load(query)
            assertNull(batch.postingFacts.single { it.key == key }.debitCard)
            val result = BetaDashboardBuilder.build(batch)
            val tx = result.snapshot.transactions.single { it.key == key }
            assertTrue(tx.debitSpendingCards.isEmpty()); assertNull(result.snapshot.debitCards.single().spending)
            assertNull(result.snapshot.accounts.single().balance)
            assertEquals(account.id, tx.source); assertEquals(Money.of("10", Currency.SAR), tx.amount)
        }
    }

    @Test fun missingAccountSuffixStillAllowsExactConfirmedGooglePayMadaParentRole() = runBlocking {
        capture("google-pay", facts().copy(amount = Money.of("10", Currency.SAR), exchangeRate = null, internationalFee = null,
            sourceAccount = null, debitSourceAccountLast4 = null, card = CardReference(bank, "5678"), cardChannel = CardChannel.DEBIT))
        approve("google-pay", account)
        val batch = source().load(query)
        assertEquals(debit.id, batch.postingFacts.single().debitCard)
        assertEquals(BigDecimal("10.00"), BetaDashboardBuilder.build(batch).snapshot.debitCards.single().spending!!.amount)
    }
}
