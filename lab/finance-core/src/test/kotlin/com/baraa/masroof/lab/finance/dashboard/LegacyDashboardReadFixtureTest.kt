package com.baraa.masroof.lab.finance.dashboard

import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

/** Frozen legacy output → Beta contract, no production dependency or calculator copy. */
class LegacyDashboardReadFixtureTest {
    @Test fun datedFxFixtureKeepsUnconvertedRowSeparateFromObservedRate() {
        val fixture = Properties().apply {
            LegacyDashboardReadFixtureTest::class.java.getResourceAsStream("/dashboard/asof-fx.properties")!!.use(::load)
        }
        val env = Environment.BETA
        val cardId = AssetId(env, BankId("BANK_ALJAZIRA"), "registry-card:1")
        val query = DashboardQuery(env, Instant.parse("2026-09-30T21:00:00Z"),
            Instant.parse("2026-10-31T21:00:00Z"), ZoneId.of("Asia/Riyadh"), Currency.SAR)
        fun row(id: String, at: String, rate: AppliedFx?, equivalent: Money?) = TransactionFacts(
            PostingKey(env, id), PostingRevision(0, 1), TransactionTime(Instant.parse(at), query.zone),
            FinancialType.EXPENSE, FinancialType.EXPENSE,
            Money.of(fixture.getProperty("foreignAmount"), Currency.valueOf(fixture.getProperty("currency"))),
            "TEST_FX_SHOP", null, "7271", emptySet(), setOf(cardId), emptySet(), emptySet(),
            setOf(ChartCategory.CREDIT_PURCHASES), rate, equivalent, null, setOf("event:$id"))
        val observed = Money.of(fixture.getProperty("spendingGross"), Currency.SAR)
        val rows = listOf(row("oct1", "2026-10-01T07:00:00Z", null, null),
            row("oct7", "2026-10-07T07:00:00Z", AppliedFx(BigDecimal(fixture.getProperty("observedRate")), "SMS"), observed))
        val metrics = SummaryMetric.entries.associateWith { null as SignedAmount? }.toMutableMap()
        metrics[SummaryMetric.SPENDING_GROSS] = SignedAmount(observed.amount, Currency.SAR)
        val analysis = AnalysisSummary(Currency.SAR, metrics, fixture.getProperty("transactionCount").toInt(),
            0, fixture.getProperty("excludedOtherCurrencyCount").toInt(), emptyList(), emptyList())
        val card = CreditCardSummary(cardId, null, "Credit", "7271", null, null, null, null, null)
        val snapshot = DashboardSnapshot(query, emptyList(), emptyList(),
            listOf(FacilitySummary(AssetId(env, cardId.bank, "registry-facility:1"), null, cards = listOf(card))),
            analysis = analysis, transactions = rows)

        assertNull(snapshot.transactions.first().sarEquivalent)
        assertNull(snapshot.transactions.first().appliedFx)
        assertEquals(observed, snapshot.transactions.last().sarEquivalent)
        assertEquals(BigDecimal("4.000000"), snapshot.transactions.last().appliedFx!!.rateToSar)
        assertEquals(1, snapshot.analysis!!.excludedOtherCurrencyCount)
        assertEquals(BigDecimal("40.00"), snapshot.analysis.metrics[SummaryMetric.SPENDING_GROSS]!!.amount)
        assertTrue(fixture.getProperty("source").endsWith("active_m8_asof_fx.json"))
    }
}
