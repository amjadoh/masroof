package com.baraa.masroof.lab.replica.application.analytics

import com.baraa.masroof.lab.analytics.*
import com.baraa.masroof.lab.replica.data.analytics.AnalysisAlertRow
import com.baraa.masroof.lab.replica.data.analytics.AnalysisPreferencesRow
import kotlinx.serialization.Serializable

@Serializable data class AnalysisDay(val day: Long, val actual: Long?, val forecast: Long?)
@Serializable data class AnalysisDue(val id: String, val name: String, val amount: Long, val day: Long?, val confirmed: Boolean)
@Serializable data class AnalysisCandidate(val key: String, val name: String, val amount: Long, val lower: Long,
    val upper: Long, val nextDay: Long, val cadence: String, val sourceIds: List<String>, val latestDay: Long,
    val latestAmount: Long, val referenceAmount: Long?, val increased: Boolean)
@Serializable data class AnalysisQuality(val used: Int, val excluded: Map<String, List<String>>, val unusual: List<String>,
    val coveredDays: Int, val coverageKnown: Boolean, val pendingReviews: Int, val blocked: Boolean,
    val historyFrom: Long? = null, val historyThrough: Long? = null)
@Serializable data class AnalysisSnapshot(val periodStart: Long, val periodEnd: Long, val issuedAt: Long,
    val sourceHash: String, val status: String, val method: String, val actual: Long, val remaining: Long?,
    val total: Long?, val lower: Long?, val upper: Long?, val days: List<AnalysisDay>, val quality: AnalysisQuality,
    val candidates: List<AnalysisCandidate>, val dues: List<AnalysisDue>, val completedPeriods: Int,
    val cleaningVersion: String = AnalyticsVersion.CLEANING, val featureVersion: String = AnalyticsVersion.FEATURES,
    val engineVersion: String = AnalyticsVersion.MODEL,
    val trainingFrom: Long? = null, val trainingThrough: Long? = null)
data class AnalysisUiState(val snapshot: AnalysisSnapshot? = null, val updating: Boolean = true,
    val unavailable: Boolean = false, val alerts: List<AnalysisAlertRow> = emptyList(),
    val preferences: AnalysisPreferencesRow = AnalysisPreferencesRow(), val history: List<AnalysisSnapshot> = emptyList(),
    val historyActuals: Map<Long, Long> = emptyMap(),
    val purchasePeriods: List<AnalysisPeriod> = emptyList())

data class AnalysisChartPoint(val day: Long, val amount: java.math.BigDecimal, val expected: Boolean)
fun analysisAmount(cents: Long) = com.baraa.masroof.lab.replica.application.dashboard.SignedMoneyAmount(
    java.math.BigDecimal.valueOf(cents, 2), com.baraa.masroof.lab.replica.core.money.Currency.SAR)
fun analysisAmount(amount: java.math.BigDecimal) = com.baraa.masroof.lab.replica.application.dashboard.SignedMoneyAmount(
    amount, com.baraa.masroof.lab.replica.core.money.Currency.SAR)
fun AnalysisSnapshot.chart(): List<AnalysisChartPoint> = days.groupBy { it.day }.toSortedMap().map { (day, rows) ->
    val actual = rows.mapNotNull { it.actual }.sum()
    val expected = rows.mapNotNull { it.forecast }.firstOrNull()
    AnalysisChartPoint(day, java.math.BigDecimal.valueOf(if (expected == null) actual else maxOf(actual, expected), 2), expected != null)
}

@Serializable data class StoredHuber(val weights: List<Double>, val means: List<Double>, val scales: List<Double>,
    val through: Long, val fingerprint: String, val baselineMae: Double, val modelMae: Double,
    val errors: Map<Int, List<Double>>, val accepted: Boolean, val features: String,
    val modelVersion: String = AnalyticsVersion.MODEL, val cleaningVersion: String = AnalyticsVersion.CLEANING, val from: Long = 0) {
    fun model() = HuberModel(weights, means, scales, through, fingerprint, baselineMae, modelMae, errors, accepted, features, from)
    companion object { fun from(m: HuberModel) = StoredHuber(m.weights, m.means, m.scales, m.trainedThrough,
        m.trainingFingerprint, m.baselineMae, m.modelMae, m.errorsByHorizon, m.accepted, m.featureVersion, from = m.trainedFrom) }
}
@Serializable data class StoredBaseline(val medians: List<Long>, val from: Long, val through: Long,
    val errors: Map<Int, List<Double>>, val kind: String = "WEEKDAY_MEDIAN",
    val cleaningVersion: String = AnalyticsVersion.CLEANING, val featureVersion: String = AnalyticsVersion.FEATURES,
    val engineVersion: String = AnalyticsVersion.MODEL)

/** Read models only; neither historical totals nor evidence are copied into the sidecar. */
data class AnalysisPeriod(val start: Long, val end: Long, val actual: Long?, val coveredDays: Int, val totalDays: Int) {
    val complete: Boolean get() = coveredDays == totalDays
}
data class AnalysisEvidence(val id: String,
    val transaction: com.baraa.masroof.lab.replica.domain.model.FinancialTransaction?,
    val facts: com.baraa.masroof.lab.replica.application.dashboard.DashboardTransactionFacts?,
    val cards: List<com.baraa.masroof.lab.replica.domain.model.CardRegistryEntry> = emptyList())

internal fun purchasePeriodHistory(data: CleanData, currentStart: Long): List<AnalysisPeriod> {
    var period = com.baraa.masroof.lab.replica.domain.period.FinancialPeriodPolicy.periodContaining(java.time.LocalDate.ofEpochDay(currentStart))
    return (1..6).map {
        period = com.baraa.masroof.lab.replica.domain.period.FinancialPeriodPolicy.previous(period)
        val start = period.startDate.toEpochDay(); val end = period.endDateExclusive.toEpochDay()
        val days = data.days.filter { it.day in start until end }
        val covered = days.count { it.covered }
        AnalysisPeriod(start, end, if (covered == 0) null else days.fold(0L) { sum, d -> Math.addExact(sum, d.net) }, covered, (end - start).toInt())
    }.reversed()
}

fun visibleAnalysisAlerts(state: AnalysisUiState, now: Long): List<com.baraa.masroof.lab.replica.data.analytics.AnalysisAlertRow> =
    state.alerts.filter { !it.resolved && it.snoozedUntil <= now }.sortedByDescending { it.observedAt }

fun normalizeAnalysisNumber(value: String): String = value.trim().map { c ->
    when { c == '٫' -> '.'; c.isDigit() -> Character.digit(c, 10).toString().single(); else -> c }
}.joinToString("")

data class AnalysisHistoryView(val periods: List<AnalysisPeriod>, val available: Int, val total: Long?, val chartPeriods: List<AnalysisPeriod>)
fun AnalysisUiState.purchaseHistory(count: Int): AnalysisHistoryView {
    require(count == 3 || count == 6)
    val periods = purchasePeriods.takeLast(count)
    val amounts = periods.mapNotNull { it.actual }
    return AnalysisHistoryView(periods, amounts.size, amounts.takeIf { it.isNotEmpty() }?.fold(0L, Math::addExact),
        periods.takeLastWhile { it.complete && it.actual != null })
}
