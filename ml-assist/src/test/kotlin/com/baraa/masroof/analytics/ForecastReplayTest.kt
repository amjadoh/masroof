package com.baraa.masroof.analytics

import org.junit.Assert.*
import org.junit.Test

class ForecastReplayTest {
    private fun data(laterAmount: Long, gaps: Set<Long> = emptySet()) = FinancialCleaner.clean(
        (0L..95L).map { FinancialFact("p$it", it, if (it >= 80) laterAmount else 1000, false, true) },
        0, 95, 0, gaps) { 0 }

    @Test fun laterPaymentsChangeTheEvaluationActualButNeverTheEarlierPrediction() {
        fun replay(data: CleanData) = ForecastReplay.evaluate(data, 60, 90, 68, 2, { 0 })
        val original = replay(data(1000))
        val changed = replay(data(100000))
        assertNotNull(original.forecast.total)
        assertEquals(original.forecast.total, changed.forecast.total)
        assertEquals(original.forecast.baselineTotal, changed.forecast.baselineTotal)
        assertNotEquals(original.actual, changed.actual)
        assertEquals(30000L, original.actual)
    }

    @Test fun incompleteFutureCoverageCannotProduceAnAccuracyClaim() {
        val result = ForecastReplay.evaluate(data(1000, setOf(85)), 60, 90, 68, 2, { 0 })
        assertNotNull(result.forecast.total)
        assertNull(result.actual)
    }
}
