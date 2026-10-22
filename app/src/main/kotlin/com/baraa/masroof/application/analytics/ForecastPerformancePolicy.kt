package com.baraa.masroof.application.analytics

import com.baraa.masroof.data.analytics.ForecastComparisonRow
import com.baraa.masroof.lab.analytics.AnalyticsVersion
import java.math.BigDecimal

data class ForecastPerformance(val allowPersonal: Boolean = true, val periods: Int = 0,
    val personalMae: Long? = null, val baselineMae: Long? = null, val pausedAfter: Long? = null)

object ForecastPerformancePolicy {
    const val VERSION = "published-day8-v1"
    fun evaluate(rows: List<ForecastComparisonRow>): ForecastPerformance {
        val usable = rows.filter { it.actual != null && it.modelVersion == AnalyticsVersion.MODEL && it.evaluationVersion == VERSION }
            .groupBy { it.periodStart }.values.map { group -> group.minBy { it.issuedAt } }.sortedBy { it.periodStart }
        var pausedAfter: Long? = null
        for(index in 2 until usable.size) {
            val window = usable.subList(index-2,index+1)
            if(pausedAfter != null && window.first().periodStart <= pausedAfter) continue
            val personal = error(window, true)
            val baseline = error(window, false)
            if(pausedAfter == null && personal > baseline.multiply(BigDecimal("1.1"))) pausedAfter = window.last().periodStart
            else if(pausedAfter != null && baseline.signum() > 0 && personal <= baseline.multiply(BigDecimal("0.9"))) pausedAfter = null
        }
        val latest = usable.takeLast(3)
        return ForecastPerformance(pausedAfter == null, usable.size,
            latest.takeIf { it.isNotEmpty() }?.let { averageError(it,true) },
            latest.takeIf { it.isNotEmpty() }?.let { averageError(it,false) },pausedAfter)
    }
    private fun averageError(rows: List<ForecastComparisonRow>, personal: Boolean) = error(rows,personal)
        .divide(BigDecimal(rows.size),0,java.math.RoundingMode.HALF_EVEN).min(BigDecimal.valueOf(Long.MAX_VALUE)).longValueExact()
    private fun error(rows: List<ForecastComparisonRow>, personal: Boolean) = rows.fold(BigDecimal.ZERO) { total,row ->
        total + BigDecimal.valueOf(row.actual!!).subtract(BigDecimal.valueOf(if(personal) row.personalTotal else row.baselineTotal)).abs()
    }
}
