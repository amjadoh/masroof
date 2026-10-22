package com.baraa.masroof.lab.beta.analytics

import com.baraa.masroof.lab.analytics.*
import com.baraa.masroof.lab.replica.application.analytics.*
import com.baraa.masroof.lab.replica.data.analytics.*
import com.baraa.masroof.lab.replica.domain.period.FinancialPeriodPolicy
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnalyticsReadModelsTest {
    @Test fun previousSalaryPeriodsExcludeCurrentAndRespectWeekendBoundariesRefundsAndZeroDays() {
        val current = FinancialPeriodPolicy.periodContaining(LocalDate.of(2026, 10, 21))
        val start = current.startDate.toEpochDay()
        val days = (start - 220..start + 3).map { PurchaseDay(it, if (it == start) 999999 else 100, 20, 1, 0, true) }
        val history = purchasePeriodHistory(clean(days), start)
        assertEquals(6, history.size)
        assertEquals(start, history.last().end)
        history.forEach { period ->
            assertEquals(FinancialPeriodPolicy.periodContaining(LocalDate.ofEpochDay(period.start)).endDateExclusive.toEpochDay(), period.end)
            assertEquals(period.totalDays * 80L, period.actual)
            assertTrue(period.complete)
        }
        val view = AnalysisUiState(purchasePeriods = history).purchaseHistory(3)
        assertEquals(history.takeLast(3).sumOf { it.actual!! }, view.total)
        assertEquals(3, view.available)
        assertEquals(3, view.chartPeriods.size)
        assertFalse(history.any { start in it.start until it.end })
    }
    @Test fun unavailableIsNotZeroAndPartialPeriodsBreakChartContinuity() {
        val current = FinancialPeriodPolicy.periodContaining(LocalDate.of(2026, 10, 21))
        val start = current.startDate.toEpochDay()
        val previous = FinancialPeriodPolicy.previous(current)
        val prior = FinancialPeriodPolicy.previous(previous)
        val days = (prior.startDate.toEpochDay() until start).map { day ->
            PurchaseDay(day, 0, 0, 0, 0, day != prior.startDate.toEpochDay())
        }
        val history = purchasePeriodHistory(clean(days), start)
        assertNull(history.first().actual)
        assertEquals(0L, history.last().actual)
        assertTrue(history.last().complete)
        assertFalse(history[history.lastIndex - 1].complete)
        val view = AnalysisUiState(purchasePeriods = history).purchaseHistory(6)
        assertEquals(2, view.available)
        assertEquals(0L, view.total)
        assertEquals(listOf(history.last()), view.chartPeriods)
    }
    @Test fun negativeRefundTotalsRemainNegativeInHistory() {
        val current = FinancialPeriodPolicy.periodContaining(LocalDate.of(2026, 10, 21))
        val previous = FinancialPeriodPolicy.previous(current)
        val days = (previous.startDate.toEpochDay() until current.startDate.toEpochDay()).map { PurchaseDay(it, 0, 500, 1, 0, true) }
        assertTrue(purchasePeriodHistory(clean(days), current.startDate.toEpochDay()).last().actual!! < 0)
    }
    @Test fun unifiedListFiltersSnoozedAndResolvedButKeepsReadRowsAndStableOrdering() {
        val rows = listOf(AnalysisAlertRow("read", "DUE", "", 1, 1, 20, read = true),
            AnalysisAlertRow("new", "ACTUAL", "", 1, 1, 30),
            AnalysisAlertRow("snoozed", "DUE", "", 1, 1, 40, snoozedUntil = 101),
            AnalysisAlertRow("ended", "DUE", "", 1, 1, 50, resolved = true))
        val state = AnalysisUiState(alerts = rows)
        val visible = visibleAnalysisAlerts(state, 100)
        assertEquals(listOf("new", "read"), visible.map { it.id })
        assertEquals(1, visible.count { !it.read })
        assertEquals(listOf("snoozed", "new", "read"), visibleAnalysisAlerts(state, 101).map { it.id })
    }
    @Test fun arabicAndPersianDigitsAndDecimalSeparatorNormalizeWithoutSilentlyFixingInvalidInput() {
        assertEquals("1200.50", normalizeAnalysisNumber(" ١٢٠٠٫٥٠ "))
        assertEquals("30", normalizeAnalysisNumber("۳۰"))
        assertEquals("1x", normalizeAnalysisNumber("١x"))
        assertNull(normalizeAnalysisNumber("٣٫٥").toIntOrNull())
    }
    @Test fun oldSnapshotDecodesWithoutNewPresentationMetadata() {
        val raw = """{"periodStart":0,"periodEnd":30,"issuedAt":1,"sourceHash":"a","status":"INSUFFICIENT_DATA","method":"NONE","actual":0,"remaining":null,"total":null,"lower":null,"upper":null,"days":[],"quality":{"used":0,"excluded":{},"unusual":[],"coveredDays":0,"coverageKnown":false,"pendingReviews":0,"blocked":false},"candidates":[],"dues":[],"completedPeriods":0}"""
        val snapshot = AnalyticsJson.format.decodeFromString(AnalysisSnapshot.serializer(), raw)
        assertNull(snapshot.trainingFrom); assertNull(snapshot.trainingThrough)
    }
    @Test fun analyticsNavigationReturnsToCenterOnlyForCenterOrigins() {
        val before = AnalyticsNavigation.centerReturns.value
        AnalyticsNavigation.open("due:test", fromCenter = true)
        AnalyticsNavigation.clear()
        assertNull(AnalyticsNavigation.target.value)
        AnalyticsNavigation.finish()
        assertEquals(before + 1, AnalyticsNavigation.centerReturns.value)
        AnalyticsNavigation.open("phone-alert")
        AnalyticsNavigation.clear(); AnalyticsNavigation.finish()
        assertEquals(before + 1, AnalyticsNavigation.centerReturns.value)
    }
    private fun clean(days: List<PurchaseDay>) = CleanData(days, emptyList(), QualityReport(0, emptyMap(), emptyList(), days.count { it.covered }, true), "test")
}
