package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.value.Currency
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.*

class BetaDashboardExplorerTest {
    @Test fun signedUnknownAndZeroPointsKeepTheirAuthoritativeLabelsWhenChartGeometryIsNormalized() {
        val zone = ZoneId.of("Asia/Riyadh")
        val period = DisplayPeriod(Instant.parse("2026-09-30T21:00:00Z"), Instant.parse("2026-10-31T21:00:00Z"), zone, PeriodKind.SELECTED, null)
        val points = listOf("-10", "0", "20", null).mapIndexed { i, value -> DailySpending(LocalDate.of(2026, 10, i + 1), value?.let { SignedAmount(BigDecimal(it), Currency.SAR) }, null, null, emptySet()) }
        val trend = SpendingTrend(null, null, period, Currency.SAR, points, null)
        val bars = BetaDashboardExplorer.bars(trend)
        assertEquals(listOf(.5f, 0f, 1f, null), bars.map { it.magnitude })
        assertEquals(points, bars.map { it.day })
        assertNull(trend.averageDailySpending)
    }
}
