package com.baraa.masroof.ui.components

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class MasroofAnalyticsChartMathTest {
    @Test fun signedBarsPreserveDirectionAndClampWithoutOverflow() {
        assertEquals(-1f, MasroofAnalyticsChartMath.signedFraction(BigDecimal("-2224.60"), BigDecimal("2000")), 0f)
        assertEquals(0.5f, MasroofAnalyticsChartMath.signedFraction(BigDecimal("100"), BigDecimal("200")), 0f)
        assertEquals(0f, MasroofAnalyticsChartMath.signedFraction(BigDecimal.TEN, BigDecimal.ZERO), 0f)
        assertEquals(-1f, MasroofAnalyticsChartMath.signedFraction(BigDecimal.valueOf(Long.MIN_VALUE), BigDecimal.ONE), 0f)
    }
    @Test fun monthAxisLabelsHaveEvenSpacingWithoutCrowdingTheLastTwoDays() {
        val indices = MasroofAnalyticsChartMath.axisIndices(30).sorted()
        assertEquals(0, indices.first())
        assertEquals(29, indices.last())
        assertTrue(indices.zipWithNext().all { (a, b) -> b - a >= 7 })
        assertEquals(setOf(0), MasroofAnalyticsChartMath.axisIndices(1))
        assertTrue(MasroofAnalyticsChartMath.axisIndices(0).isEmpty())
    }
    @Test fun dailySelectionUsesBarCellsAndHandlesEmptyCharts() {
        assertNull(MasroofAnalyticsChartMath.barAt(10f, 100f, 0))
        assertNull(MasroofAnalyticsChartMath.barAt(10f, 0f, 5))
        assertEquals(0, MasroofAnalyticsChartMath.barAt(-10f, 100f, 5))
        assertEquals(1, MasroofAnalyticsChartMath.barAt(39f, 100f, 5))
        assertEquals(4, MasroofAnalyticsChartMath.barAt(100f, 100f, 5))
    }
}
