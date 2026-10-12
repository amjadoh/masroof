package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ChartDataWorkflowTest {
    private val first = LocalDate.parse("2026-09-27")
    private fun money(value: String) = SignedMoneyAmount(BigDecimal(value).setScale(2), Currency.SAR)
    private fun part(values: List<String>, id: String): DailySpendingTrend {
        val points = values.mapIndexed { i, v -> DailySpendingPoint(first.plusDays(i.toLong()), money(v),
            money(if (BigDecimal(v).signum() >= 0) v else "0"), money(if (BigDecimal(v).signum() < 0) BigDecimal(v).negate().toPlainString() else "0")) }
        return DailySpendingTrend(points, money("0"), points.associate { it.date to if (it.spending.amount.signum() != 0) setOf("$id-${it.date}") else emptySet() })
    }
    private val purchase = part(listOf("100", "0", "200"), "purchase")
    private val cash = part(listOf("0", "600", "0"), "cash")
    private val refund = part(listOf("-20", "0", "0"), "refund")
    private val categories = linkedMapOf(ChartDataCategory.MADA_PURCHASES to purchase,
        ChartDataCategory.CASH_WITHDRAWALS to cash, ChartDataCategory.UNATTRIBUTED_REFUNDS to refund)
    private val base = part(listOf("80", "600", "200"), "base").copy(categoryTrends = categories)

    @Test fun selectionUpdatesAmountAverageGrossRefundsAndIdsTogether_includingZeroDays() {
        val trend = ChartDataWorkflow.select(base, setOf(ChartDataCategory.MADA_PURCHASES, ChartDataCategory.UNATTRIBUTED_REFUNDS))
        assertEquals(listOf("80.00", "0.00", "200.00"), trend.points.map { it.spending.amount.toPlainString() })
        assertEquals("93.33", trend.averageDailySpending.amount.toPlainString())
        assertEquals("300.00", trend.totalBeforeRefunds.amount.toPlainString())
        assertEquals("20.00", trend.totalRefunds.amount.toPlainString())
        assertEquals(purchase.transactionIdsByDate.getValue(first) + refund.transactionIdsByDate.getValue(first), trend.transactionIdsByDate[first])
        assertEquals(emptySet<String>(), trend.transactionIdsByDate[first.plusDays(1)])
        assertEquals("600.00", base.points[1].spending.amount.toPlainString())
    }
    @Test fun scopeAndBankQualifiedEntityChoicesRemainIndependentAndSurviveWorkflowReload() {
        val repo = InMemoryChartDataPreferencesRepository()
        val workflow = ChartDataWorkflow(repo)
        workflow.save("card:BANK_A:1234", setOf(ChartDataCategory.MADA_PURCHASES), categories.keys)
        workflow.setHomeScope("overall")
        val reload = ChartDataWorkflow(repo)
        assertEquals(setOf(ChartDataCategory.MADA_PURCHASES), reload.categories(base, "card:BANK_A:1234"))
        assertEquals(categories.keys, reload.categories(base, "card:BANK_B:1234"))
        assertEquals(categories.keys, reload.categories(base, "home:overall"))
        assertEquals("overall", reload.preferences.value.homeScope)
    }
    @Test fun restoringDefaultsClearsOverrideAndIncludesFutureEligibleCategories() {
        val workflow = ChartDataWorkflow(InMemoryChartDataPreferencesRepository())
        workflow.save("card:a", setOf(ChartDataCategory.MADA_PURCHASES), categories.keys)
        workflow.save("card:a", categories.keys, categories.keys)
        assertFalse(workflow.preferences.value.selections.containsKey("card:a"))
        val future = base.copy(categoryTrends = categories + (ChartDataCategory.FEES to part(listOf("0","0","0"), "fee")))
        assertEquals(future.categoryTrends.keys, workflow.categories(future, "card:a"))
    }
    @Test(expected = IllegalArgumentException::class) fun cannotSaveNoCategories() {
        ChartDataWorkflow(InMemoryChartDataPreferencesRepository()).save("card:a", emptySet(), categories.keys)
    }
    @Test(expected = IllegalArgumentException::class) fun cannotSaveCategoriesOutsideScope() {
        ChartDataWorkflow(InMemoryChartDataPreferencesRepository()).save("card:a", setOf(ChartDataCategory.CREDIT_SETTLEMENTS), categories.keys)
    }
    @Test fun legacyAndFuturePeriodsRemainSafe() {
        val legacy = base.copy(categoryTrends = emptyMap())
        assertSame(legacy, ChartDataWorkflow.select(legacy, emptySet()))
        val empty = base.copy(points = emptyList(), averageDailySpending = money("0"),
            categoryTrends = mapOf(ChartDataCategory.MADA_PURCHASES to purchase.copy(points = emptyList())))
        assertEquals("0.00", ChartDataWorkflow.select(empty, emptySet()).averageDailySpending.amount.toPlainString())
    }
}
