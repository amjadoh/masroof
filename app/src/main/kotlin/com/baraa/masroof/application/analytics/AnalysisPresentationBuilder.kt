package com.baraa.masroof.application.analytics

import com.baraa.masroof.analytics.CleanData
import java.time.Instant
import java.time.ZoneId

/** Presentation facts only. Never creates a historical forecast or changes financial totals. */
enum class ForecastComparisonStatus { UNAVAILABLE, AWAITING_ELIGIBLE_FORECAST, WAITING_FOR_ANCHOR, NO_QUALIFIED_FORECAST, WAITING_FOR_PERIOD, INCOMPLETE_COVERAGE, READY }
data class ForecastComparisonExplanation(val status: ForecastComparisonStatus, val anchorDay: Long? = null, val enabledDay: Long? = null)
data class AnalysisDailyBar(val day: Long, val amount: Long?, val expected: Boolean)
enum class ForecastMethod { UNAVAILABLE, INITIAL, PERSONAL }
data class ForecastExplanation(val method: ForecastMethod, val evaluations: List<AnalysisEvaluation>)

object AnalysisPresentationBuilder {
    fun forecast(snapshot: AnalysisSnapshot): ForecastExplanation {
        val method = when {
            snapshot.quality.blocked || snapshot.status != "READY" || snapshot.total == null -> ForecastMethod.UNAVAILABLE
            snapshot.method == "WEEKDAY_MEDIAN" -> ForecastMethod.INITIAL
            snapshot.method == com.baraa.masroof.analytics.AnalyticsVersion.MODEL -> ForecastMethod.PERSONAL
            else -> ForecastMethod.UNAVAILABLE
        }
        val evaluations = if (method == ForecastMethod.UNAVAILABLE ||
            snapshot.evaluationVersion != com.baraa.masroof.analytics.AnalyticsVersion.EVALUATION) emptyList() else
            snapshot.evaluations.filter { it.horizon in setOf(1, 7, 14, 28) && it.count >= 8 && it.mae >= 0 }
                .distinctBy { it.horizon }.sortedBy { it.horizon }
        return ForecastExplanation(method, evaluations)
    }

    fun comparison(state: AnalysisUiState, today: Long, zone: ZoneId): ForecastComparisonExplanation {
        val snapshot = state.snapshot ?: return ForecastComparisonExplanation(ForecastComparisonStatus.UNAVAILABLE)
        val anchor = snapshot.periodStart + 7
        val enabled = state.preferences.enabledAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
        val row = state.forecastComparisons.filter { it.periodStart == snapshot.periodStart && ForecastPerformancePolicy.supports(it) }.minByOrNull { it.issuedAt }
        val status = when {
            row?.actual != null -> ForecastComparisonStatus.READY
            row != null && today < row.periodEnd -> ForecastComparisonStatus.WAITING_FOR_PERIOD
            row != null -> ForecastComparisonStatus.INCOMPLETE_COVERAGE
            today < anchor -> ForecastComparisonStatus.WAITING_FOR_ANCHOR
            today < snapshot.periodEnd -> ForecastComparisonStatus.AWAITING_ELIGIBLE_FORECAST
            else -> ForecastComparisonStatus.NO_QUALIFIED_FORECAST
        }
        return ForecastComparisonExplanation(status, anchor, enabled)
    }

    fun dailyBars(snapshot: AnalysisSnapshot): List<AnalysisDailyBar> = snapshot.days.groupBy { it.day }.toSortedMap().map { (day, rows) ->
        val actual = rows.mapNotNull { it.actual }.takeIf { it.isNotEmpty() }?.fold(0L, Math::addExact)
        val forecast = rows.mapNotNull { it.forecast }.maxOrNull()
        val expected = forecast != null && (actual == null || forecast > actual)
        val gap = snapshot.quality.coverageDays.firstOrNull { it.day == day }?.covered == false
        AnalysisDailyBar(day, if (gap && !expected) null else if (expected) forecast!! else actual ?: 0, expected)
    }

    fun coverage(data: CleanData, today: Long): List<AnalysisCoverageDay> {
        val from = data.days.firstOrNull { it.covered }?.day ?: return emptyList()
        return data.days.filter { it.day in from..today }.map { AnalysisCoverageDay(it.day, it.covered) }
    }

    fun consecutiveCompleteDays(data: CleanData, today: Long): Int =
        data.days.filter { it.day < today }.takeLastWhile { it.covered }.size
}
