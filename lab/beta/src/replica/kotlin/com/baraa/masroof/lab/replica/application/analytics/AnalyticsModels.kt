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
    val engineVersion: String = AnalyticsVersion.MODEL)
data class AnalysisUiState(val snapshot: AnalysisSnapshot? = null, val updating: Boolean = true,
    val unavailable: Boolean = false, val alerts: List<AnalysisAlertRow> = emptyList(),
    val preferences: AnalysisPreferencesRow = AnalysisPreferencesRow(), val history: List<AnalysisSnapshot> = emptyList(),
    val historyActuals: Map<Long, Long> = emptyMap())

data class AnalysisChartPoint(val day: Long, val amount: java.math.BigDecimal, val expected: Boolean)
fun analysisAmount(cents: Long) = com.baraa.masroof.lab.replica.application.dashboard.SignedMoneyAmount(
    java.math.BigDecimal.valueOf(cents, 2), com.baraa.masroof.lab.replica.core.money.Currency.SAR)
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
