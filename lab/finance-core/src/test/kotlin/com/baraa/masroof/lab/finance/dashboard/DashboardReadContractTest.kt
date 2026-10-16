package com.baraa.masroof.lab.finance.dashboard

import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.asset.CreditCardRole
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DashboardReadContractTest {
    private val start = Instant.parse("2026-10-01T00:00:00Z")
    private val query = DashboardQuery(Environment.BETA, start, start.plusSeconds(31 * 86400),
        ZoneId.of("UTC"), Currency.SAR)
    private fun id(local: String, bank: String = "BANK_ALJAZIRA") =
        AssetId(Environment.BETA, BankId(bank), local)
    private fun money(value: String, currency: Currency = Currency.SAR) = Money.of(value, currency)
    private fun signed(value: String, currency: Currency = Currency.SAR) = SignedAmount(BigDecimal(value), currency)
    private fun period(kind: PeriodKind = PeriodKind.SALARY) =
        DisplayPeriod(query.from, query.until, query.zone, kind, "October")
    private fun analysis(metrics: Map<SummaryMetric, SignedAmount?> = SummaryMetric.entries.associateWith { signed("0") },
        trends: List<SpendingTrend> = emptyList()) = AnalysisSummary(Currency.SAR, metrics, 0, 0, 0, trends, emptyList())
    private fun snapshot(accounts: List<AccountSummary> = emptyList(),
        debit: List<DebitSpending> = emptyList(), facilities: List<FacilitySummary> = emptyList(),
        loans: List<LoanSummary> = emptyList(), transactions: List<TransactionFacts> = emptyList(),
        commitments: CommitmentsSummary? = null, analysis: AnalysisSummary? = null) =
        DashboardSnapshot(query, accounts, debit, facilities, loans, commitments, analysis, transactions,
            revision = ReadRevision("beta-ledger:7", query.until))
    private fun card(local: String, role: CreditCardRole?, last4: String = "1234") =
        CreditCardSummary(id(local), role, local, last4, null, PeriodSpending(period(), signed("-20")),
            null, signed("-5"), query.from)
    private fun loan() = LoanSummary(id("loan"), "PERSONAL", "Personal", money("800"), query.from,
        money("100"), query.from, signed("100"), period())
    private fun transaction(at: Instant = query.from) = TransactionFacts(
        PostingKey(Environment.BETA, "tx-1"), PostingRevision(2, 7), TransactionTime(at, ZoneId.of("Asia/Riyadh")),
        FinancialType.FEE, FinancialType.LOAN_REPAYMENT, money("10", Currency.USD), null, "Financing",
        null, emptySet(), emptySet(), emptySet(), setOf(id("loan")), setOf(ChartCategory.LOAN_REPAYMENTS),
        AppliedFx(BigDecimal("3.751234"), "SMS"), money("37.51"), money("0.5", Currency.USD), setOf("event:7"))

    @Test fun readerKeepsUnavailableSeparateFromObservedZero() = runTest {
        val reader = DashboardReader { request ->
            snapshot(listOf(AccountSummary(id("missing"), null), AccountSummary(id("zero"), signed("0"))))
                .copy(query = request)
        }
        val result = reader.read(query)
        assertNull(result.accounts.first().balance)
        assertEquals(BigDecimal.ZERO, result.accounts.last().balance!!.amount)
        assertNull(result.analysis)
        assertEquals("beta-ledger:7", result.revision!!.token)
    }

    @Test fun signedBalancesAndRefundHeavySpendingAreNotClampedOrRounded() {
        val result = snapshot(listOf(AccountSummary(id("overdrawn"), signed("-12.34"))),
            listOf(DebitSpending(id("mada"), signed("-20"))))
        assertEquals(BigDecimal("-12.34"), result.accounts.single().balance!!.amount)
        assertEquals(BigDecimal("-20"), result.debitCards.single().spending!!.amount)
        assertThrows(IllegalArgumentException::class.java) { signed("1.001") }
        assertThrows(IllegalArgumentException::class.java) { money("-1") }
    }

    @Test fun stableRegistryIdentityAllowsSameSuffixAtSameAndDifferentBanks() {
        val facilities = listOf(FacilitySummary(id("facility"), money("100"), cards = listOf(
            card("primary", CreditCardRole.PRIMARY), card("supplementary", CreditCardRole.SUPPLEMENTARY))))
        val result = snapshot(listOf(AccountSummary(id("account"), null),
            AccountSummary(id("account", "D360"), null)), facilities = facilities)
        assertEquals(2, result.accounts.size)
        assertEquals(2, result.creditFacilities.single().cards.size)
        assertEquals(money("100"), result.creditFacilities.single().statementDue)
    }

    @Test fun noDuplicateOrCrossEnvironmentEntityCanEnterSnapshot() {
        val account = AccountSummary(id("a"), null)
        assertThrows(IllegalArgumentException::class.java) { snapshot(listOf(account, account)) }
        assertThrows(IllegalArgumentException::class.java) {
            snapshot(listOf(account.copy(id = account.id.copy(environment = Environment.MIGRATION_REHEARSAL))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            snapshot(listOf(account), listOf(DebitSpending(id("a"), null)))
        }
    }

    @Test fun facilityMembersCannotLeakAcrossBanksOrClaimTwoPrimaries() {
        assertThrows(IllegalArgumentException::class.java) {
            FacilitySummary(id("f", "D360"), null, cards = listOf(card("c", null)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FacilitySummary(id("f"), null, cards = listOf(card("a", CreditCardRole.PRIMARY),
                card("b", CreditCardRole.PRIMARY)))
        }
    }

    @Test fun unlinkedDebitIsVisibleButLinkedDebitMustResolveToOwnedAccount() {
        assertEquals(1, snapshot(debit = listOf(DebitSpending(id("card"), null))).debitCards.size)
        assertThrows(IllegalArgumentException::class.java) {
            snapshot(debit = listOf(DebitSpending(id("card"), null, id("missing"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DebitSpending(id("card"), null, id("account", "D360"))
        }
    }

    @Test fun cardPeriodsStayDistinctAndZoneComesFromCaller() {
        val statement = period(PeriodKind.STATEMENT).copy(from = query.from.minusSeconds(86400))
        val row = card("c", null).copy(statementSpending = PeriodSpending(statement, signed("25")),
            calendarSpending = PeriodSpending(period(PeriodKind.CALENDAR_MONTH), signed("30")))
        assertNotEquals(row.statementSpending!!.period.from, row.salarySpending!!.period.from)
        assertThrows(IllegalArgumentException::class.java) {
            row.copy(salarySpending = PeriodSpending(statement, signed("25")))
        }
        assertEquals(ZoneId.of("America/New_York"), query.copy(zone = ZoneId.of("America/New_York")).zone)
    }

    @Test fun unknownStatementDoesNotBorrowAnOldDueDate() {
        assertThrows(IllegalArgumentException::class.java) {
            FacilitySummary(id("f"), null, dueDate = LocalDate.of(2026, 10, 20))
        }
        assertNull(FacilitySummary(id("f"), null).dueDate)
    }

    @Test fun emptyHistoryIsRepresentableWithoutInventingUnknownTotals() {
        val empty = snapshot(analysis = analysis())
        assertEquals(0, empty.analysis!!.transactionCount)
        assertTrue(empty.analysis.metrics.values.all { it!!.amount.signum() == 0 })
        val unavailable = analysis(SummaryMetric.entries.associateWith { null })
        assertTrue(unavailable.metrics.values.all { it == null })
        assertThrows(IllegalArgumentException::class.java) { analysis(emptyMap()) }
    }

    @Test fun allSummaryAndFlowValuesHaveOneDeclaredCurrency() {
        assertThrows(IllegalArgumentException::class.java) {
            analysis(SummaryMetric.entries.associateWith { signed("0", Currency.USD) })
        }
        val flow = AccountFlow(Currency.SAR, FlowBucket.entries.associateWith { money("0") },
            money("0"), money("0"), money("0"), money("0"), signed("-1"), signed("-2"))
        assertEquals(BigDecimal("-2"), flow.cashPosition.amount)
        assertThrows(IllegalArgumentException::class.java) { flow.copy(buckets = emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { flow.copy(totalIn = money("0", Currency.USD)) }
        assertThrows(IllegalArgumentException::class.java) { snapshot(analysis = analysis().copy(currency = Currency.USD)) }
    }

    @Test fun persistedLoanAttributionAndFxFactsCrossBoundaryWithoutReinterpretation() {
        val tx = snapshot(loans = listOf(loan()), transactions = listOf(transaction())).transactions.single()
        assertEquals(FinancialType.FEE, tx.storedType)
        assertEquals(FinancialType.LOAN_REPAYMENT, tx.effectiveType)
        assertEquals(setOf(id("loan")), tx.loans)
        assertEquals(BigDecimal("3.751234"), tx.appliedFx!!.rateToSar)
        assertEquals(money("37.51"), tx.sarEquivalent)
        assertEquals(money("0.5", Currency.USD), tx.internationalFee)
        assertEquals(PostingRevision(2, 7), tx.revision)
        assertEquals(ZoneId.of("Asia/Riyadh"), tx.time.zone)
        assertEquals(setOf("event:7"), tx.evidenceIds)
    }

    @Test fun fleetFlowIsProvidedEvenWhenPerAccountFlowsAreUnavailable() {
        val buckets = FlowBucket.entries.associateWith { money("0") }.toMutableMap()
        buckets[FlowBucket.SALARY] = money("100")
        val fleet = AccountFlow(Currency.SAR, buckets, money("100"), money("0"), money("100"),
            money("0"), signed("100"), signed("100"))
        val result = snapshot(accounts = listOf(AccountSummary(id("a"), null))).copy(fleetFlow = fleet)
        assertNull(result.accounts.single().flow)
        assertEquals(money("100"), result.fleetFlow!!.buckets[FlowBucket.SALARY])
        assertEquals(signed("100"), result.fleetFlow.cashPosition)
        assertThrows(IllegalArgumentException::class.java) {
            result.copy(query = query.copy(currency = Currency.USD))
        }
    }

    @Test fun selectedTransactionWindowIsInclusiveExclusiveAndIdsAreUnique() {
        snapshot(loans = listOf(loan()), transactions = listOf(transaction(query.from)))
        snapshot(loans = listOf(loan()), transactions = listOf(transaction(query.until.minusNanos(1))))
        listOf(query.from.minusNanos(1), query.until).forEach { outside ->
            assertThrows(IllegalArgumentException::class.java) {
                snapshot(loans = listOf(loan()), transactions = listOf(transaction(outside)))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            snapshot(loans = listOf(loan()), transactions = listOf(transaction(), transaction()))
        }
    }

    @Test fun nativeSarNeedsNoConversionAndCannotClaimForeignEquivalent() {
        val native = transaction().copy(amount = money("10"), appliedFx = null, sarEquivalent = null)
        val result = snapshot(loans = listOf(loan()), transactions = listOf(native))
        assertEquals(money("10"), result.transactions.single().amount)
        assertNull(result.transactions.single().sarEquivalent)
        assertNull(result.transactions.single().appliedFx)
        assertThrows(IllegalArgumentException::class.java) { transaction().copy(amount = money("10")) }
    }

    @Test fun readContractDoesNotTurnHistoricalUnknownIntoPostingAuthority() {
        val historical = transaction().copy(storedType = FinancialType.UNKNOWN, effectiveType = FinancialType.UNKNOWN,
            evidenceIds = emptySet())
        assertEquals(FinancialType.UNKNOWN,
            snapshot(loans = listOf(loan()), transactions = listOf(historical)).transactions.single().effectiveType)
        assertEquals(PostingEligibility.Review(setOf(ReviewReason.UNKNOWN_TYPE)),
            PostingGate.evaluate(true, CorrectionAuthority.NONE, historical.effectiveType, emptySet()))
    }

    @Test fun rowInvolvementIsResolvedAndCannotLeakAnUnknownLoanOrCreditIntoDebit() {
        assertThrows(IllegalArgumentException::class.java) { snapshot(transactions = listOf(transaction())) }
        assertThrows(IllegalArgumentException::class.java) {
            snapshot(loans = listOf(loan()), transactions = listOf(transaction().copy(
                cards = setOf(id("credit")), debitSpendingCards = setOf(id("credit")))))
        }
        assertThrows(IllegalArgumentException::class.java) { transaction().copy(evidenceIds = setOf("")) }
        assertThrows(IllegalArgumentException::class.java) { transaction().copy(appliedFx = null) }
    }

    @Test fun ownedTransferEndpointsAndOpaqueCategoryRemainApplicationFacts() {
        val tx = transaction().copy(storedType = FinancialType.SELF_TRANSFER, effectiveType = FinancialType.SELF_TRANSFER,
            accounts = setOf(id("source"), id("destination", "D360")), source = id("source"),
            destination = id("destination", "D360"), categoryId = "opaque-user-category")
        val result = snapshot(accounts = listOf(AccountSummary(id("source"), null),
            AccountSummary(id("destination", "D360"), null)), loans = listOf(loan()), transactions = listOf(tx))
        assertEquals(id("source"), result.transactions.single().source)
        assertEquals(id("destination", "D360"), result.transactions.single().destination)
        assertEquals("opaque-user-category", result.transactions.single().categoryId)
        assertThrows(IllegalArgumentException::class.java) {
            result.copy(transactions = listOf(tx.copy(destination = id("missing"))))
        }
    }

    @Test fun recurringCommitmentOccurrencesRemainSeparateAndAttributionIsUnambiguous() {
        val first = CommitmentRow(Environment.BETA, "rent:2026-10-01", "Rent", money("100"),
            LocalDate.of(2026, 10, 1), PaymentStatus.PAID, userCommitmentId = "rent")
        val second = first.copy(occurrenceId = "rent:2026-10-08", status = PaymentStatus.UNPAID)
        val result = snapshot(commitments = CommitmentsSummary(Currency.SAR, listOf(first, second),
            money("200"), money("100"), money("100")))
        assertEquals(2, result.commitments!!.rows.size)
        assertThrows(IllegalArgumentException::class.java) { first.copy(facilityId = id("f")) }
        assertThrows(IllegalArgumentException::class.java) {
            result.commitments.copy(rows = listOf(first, first))
        }
        assertThrows(IllegalArgumentException::class.java) {
            snapshot(commitments = result.commitments.copy(rows = listOf(first.copy(userCommitmentId = null,
                loanId = id("missing")))))
        }
    }

    @Test fun comparisonTrendCanUseHistoricalEvidenceButCannotCrossEnvironmentOrZone() {
        val historical = period(PeriodKind.CALENDAR_MONTH).copy(from = query.from.minusSeconds(86400), until = query.from)
        val point = DailySpending(LocalDate.of(2026, 9, 30), signed("-2"), signed("3"), signed("5"),
            setOf(PostingKey(Environment.BETA, "historical:1")))
        val trend = SpendingTrend(null, ChartCategory.MADA_PURCHASES, historical, Currency.SAR,
            listOf(point), signed("-2"))
        val result = snapshot(analysis = analysis(trends = listOf(trend)))
        assertTrue(result.transactions.isEmpty())
        assertEquals(1, result.analysis!!.trends.single().points.size)
        assertThrows(IllegalArgumentException::class.java) {
            snapshot(analysis = analysis(trends = listOf(trend.copy(period = historical.copy(zone = ZoneId.of("Asia/Riyadh"))))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            snapshot(analysis = analysis(trends = listOf(trend.copy(points = listOf(point.copy(
                transactions = setOf(PostingKey(Environment.MIGRATION_REHEARSAL, "historical:1"))))))))
        }
    }

    @Test fun chartCustomizationDoesNotChangeFullScopeSummary() {
        val metrics = SummaryMetric.entries.associateWith { signed("0") }.toMutableMap()
        metrics[SummaryMetric.SPENDING_NET] = signed("-20")
        val full = analysis(metrics)
        val selected = full.copy(trends = emptyList())
        assertEquals(full.metrics, selected.metrics)
        assertEquals(BigDecimal("-20"), selected.metrics[SummaryMetric.SPENDING_NET]!!.amount)
    }

    @Test fun aggregateTrendScopesDoNotRequireSummingIndividualCards() {
        val aggregate = SpendingTrend(null, null, period(), Currency.SAR, emptyList(), signed("0"),
            TrendScope.CREDIT_CARDS)
        assertEquals(TrendScope.CREDIT_CARDS, aggregate.scope)
        assertNull(aggregate.assetId)
        assertThrows(IllegalArgumentException::class.java) { aggregate.copy(scope = TrendScope.ASSET) }
        assertThrows(IllegalArgumentException::class.java) { aggregate.copy(assetId = id("credit")) }
        assertEquals(id("credit"), aggregate.copy(scope = TrendScope.ASSET, assetId = id("credit")).assetId)
    }
}
