package com.baraa.masroof.lab.analytics

import kotlin.math.*

data class HuberModel(val weights: List<Double>, val means: List<Double>, val scales: List<Double>,
    val trainedThrough: Long, val trainingFingerprint: String, val baselineMae: Double,
    val modelMae: Double, val errorsByHorizon: Map<Int, List<Double>>, val accepted: Boolean,
    val featureVersion: String = AnalyticsVersion.FEATURES, val trainedFrom: Long = 0)
data class WeekdayModel(val medians: List<Long>, val from: Long, val through: Long, val errors: Map<Int, List<Double>>)
data class ForecastPoint(val day: Long, val gross: Long)
data class PurchaseForecast(val status: String, val method: String, val actual: Long,
    val remaining: Long?, val total: Long?, val lower: Long?, val upper: Long?,
    val points: List<ForecastPoint>, val model: HuberModel?, val baseline: WeekdayModel? = null)

/** Numeric-only engine. All transforms and evaluation fits use the training prefix. */
object PurchaseForecaster {
    fun forecast(data: CleanData, periodStart: Long, periodEnd: Long, today: Long, completedPeriods: Int,
        phase: (Long) -> Int, previous: HuberModel? = null): PurchaseForecast {
        require(periodEnd > periodStart && today in periodStart until periodEnd)
        val actual = data.days.filter { it.day >= periodStart }.fold(0L) { sum, d -> Math.addExact(sum, d.net) }
        val history = data.days.filter { it.day < today && it.covered }
        if (!data.quality.coverageKnown || history.size < 56 || completedPeriods < 2) return PurchaseForecast(
            "INSUFFICIENT_DATA", "NONE", actual, null, null, null, null, emptyList(), null)
        val lastGap = history.zipWithNext().indexOfLast { (a, b) -> b.day != a.day + 1 }
        val training = history.drop(if (lastGap < 0) 0 else lastGap + 1).takeIf { it.lastOrNull()?.day == today - 1 }.orEmpty()
        var model: HuberModel? = null
        if (training.size >= 120 && completedPeriods >= 4 && training.count { it.gross > 0 } >= 20) {
            val canReuse = previous != null && previous.featureVersion == AnalyticsVersion.FEATURES &&
                today - previous.trainedThrough <= 7 && previous.trainingFingerprint == fingerprint(training.filter { it.day <= previous.trainedThrough })
            model = if (canReuse) previous else runCatching { evaluateAndFit(training, phase) }.getOrNull()
        }
        val chosen = model?.takeIf { it.accepted }
        val predictions = predict(if (chosen == null) history else training, today, periodEnd - 1, phase, chosen)
        val todayGross = data.days.firstOrNull { it.day == today }?.gross ?: 0
        val remaining = predictions.fold(0L) { sum, point -> Math.addExact(sum,
            if (point.day == today) (point.gross - todayGross).coerceAtLeast(0) else point.gross) }
        val total = Math.addExact(actual, remaining)
        val horizon = bucket((periodEnd - today).toInt())
        val baseErrors = baselineErrors(history, phase)
        val errors = if (chosen != null) chosen.errorsByHorizon[horizon].orEmpty() else baseErrors[horizon].orEmpty()
        val margin = errors.sorted().takeIf { it.size >= 8 }?.let { it[(ceil(it.size * .9).toInt() - 1).coerceIn(it.indices)].roundToLong() }
        return PurchaseForecast("READY", if (chosen == null) "WEEKDAY_MEDIAN" else AnalyticsVersion.MODEL,
            actual, remaining, total, margin?.let { Math.addExact(actual, (remaining - it).coerceAtLeast(0)) },
            margin?.let { Math.addExact(total, it) }, predictions, model,
            WeekdayModel(weekdayMedians(history).map { it.roundToLong() },
                history.groupBy { weekday(it.day) }.values.flatMap { it.takeLast(12) }.minOf { it.day }, history.last().day, baseErrors))
    }

    private fun fingerprint(days: List<PurchaseDay>) = hash(days.joinToString { "${it.day}:${it.gross}:${it.refunds}:${it.phase}" })
    private fun bucket(days: Int) = if (days <= 7) 7 else if (days <= 14) 14 else 31
    private fun features(day: Long, phase: Int, history: List<PurchaseDay>): DoubleArray {
        fun lag(n: Int) = history.firstOrNull { it.day == day - n }?.gross?.toDouble() ?: 0.0
        fun mean(n: Int) = history.filter { it.day in (day - n) until day }.map { it.gross.toDouble() }.average().takeIf { it.isFinite() } ?: 0.0
        return doubleArrayOf(1.0, *(0..5).map { if (weekday(day) == it) 1.0 else 0.0 }.toDoubleArray(),
            phase.toDouble() / 31, lag(1) / 100, lag(7) / 100, mean(7) / 100, mean(28) / 100)
    }

    private fun fit(history: List<PurchaseDay>): HuberModel {
        val rows = history.drop(28).map { features(it.day, it.phase, history.filter { row -> row.day < it.day }) }
        val targets = history.drop(28).map { it.gross.toDouble() / 100 }.toDoubleArray()
        val p = rows.first().size
        val means = DoubleArray(p) { j -> if (j == 0) 0.0 else rows.map { it[j] }.average() }
        val scales = DoubleArray(p) { j -> if (j == 0) 1.0 else sqrt(rows.map { (it[j] - means[j]).pow(2) }.average()).coerceAtLeast(1e-6) }
        val x = rows.map { row -> DoubleArray(p) { j -> (row[j] - means[j]) / scales[j] } }
        var weights = DoubleArray(p); weights[0] = median(targets.toList())
        repeat(60) {
            val residuals = x.mapIndexed { i, row -> targets[i] - dot(weights, row) }
            val center = median(residuals)
            val sigma = (1.4826 * median(residuals.map { abs(it - center) })).coerceAtLeast(.01)
            val a = Array(p) { DoubleArray(p) }; val b = DoubleArray(p)
            for (i in x.indices) {
                val w = minOf(1.0, 1.35 * sigma / abs(residuals[i]).coerceAtLeast(1e-12))
                for (j in 0 until p) { b[j] += w * x[i][j] * targets[i]; for (k in 0 until p) a[j][k] += w * x[i][j] * x[i][k] }
            }
            for (j in 0 until p) a[j][j] += if (j == 0) 1e-8 else .01
            weights = solve(a, b)
        }
        check(weights.all { it.isFinite() })
        return HuberModel(weights.toList(), means.toList(), scales.toList(), history.last().day,
            fingerprint(history), 0.0, 0.0, emptyMap(), false, trainedFrom = history.first().day)
    }

    private fun predict(history: List<PurchaseDay>, from: Long, through: Long, phase: (Long) -> Int, model: HuberModel?): List<ForecastPoint> {
        val rolling = history.toMutableList()
        val medians = weekdayMedians(history)
        return (from..through).map { day ->
            val amount = if (model == null) medians[weekday(day)] else {
                val x = features(day, phase(day), rolling)
                dot(model.weights.toDoubleArray(), DoubleArray(x.size) { (x[it] - model.means[it]) / model.scales[it] }) * 100
            }
            check(amount.isFinite() && amount < Long.MAX_VALUE / 4)
            val cents = amount.roundToLong().coerceAtLeast(0)
            rolling += PurchaseDay(day, cents, 0, 0, phase(day), true)
            ForecastPoint(day, cents)
        }
    }
    private fun weekdayMedians(history: List<PurchaseDay>) = (0..6).map { dow ->
        median(history.filter { weekday(it.day) == dow }.takeLast(12).map { it.gross.toDouble() })
    }

    private fun evaluateAndFit(history: List<PurchaseDay>, phase: (Long) -> Int): HuberModel {
        val base = mutableMapOf<Int, MutableList<Double>>(); val candidate = mutableMapOf<Int, MutableList<Double>>()
        for (cut in (56 until history.size - 1 step 7).toList().takeLast(16)) {
            val train = history.take(cut); val model = fit(train)
            for (h in listOf(1, 7, 14, 28)) {
                if (cut + h > history.size) continue
                val actual = history.subList(cut, cut + h).sumOf { it.gross.toDouble() }
                val from = history[cut].day; val through = history[cut + h - 1].day
                base.getOrPut(bucket(h)) { mutableListOf() }.add(abs(actual - predict(train, from, through, phase, null).sumOf { it.gross.toDouble() }))
                candidate.getOrPut(bucket(h)) { mutableListOf() }.add(abs(actual - predict(train, from, through, phase, model).sumOf { it.gross.toDouble() }))
            }
        }
        val baselineMae = base.values.flatten().average(); val candidateMae = candidate.values.flatten().average()
        val accepted = candidate.values.sumOf { it.size } >= 8 && baselineMae.isFinite() && baselineMae > 0 &&
            candidateMae <= baselineMae * .9 && base.all { (h, values) -> candidate[h]!!.average() <= values.average() * 1.1 }
        return fit(history).copy(baselineMae = baselineMae, modelMae = candidateMae, errorsByHorizon = candidate, accepted = accepted)
    }

    private fun baselineErrors(history: List<PurchaseDay>, phase: (Long) -> Int): Map<Int, List<Double>> {
        val errors = mutableMapOf<Int, MutableList<Double>>()
        for (cut in (28 until history.size - 1 step 3).toList().takeLast(40)) for (h in listOf(1, 7, 14, 28)) {
            if (cut + h > history.size) continue
            if (history[cut + h - 1].day - history[cut].day != h - 1L) continue
            val actual = history.subList(cut, cut + h).sumOf { it.gross.toDouble() }
            val predicted = predict(history.take(cut), history[cut].day, history[cut + h - 1].day, phase, null).sumOf { it.gross.toDouble() }
            errors.getOrPut(bucket(h)) { mutableListOf() }.add(abs(actual - predicted))
        }
        return errors
    }
    private fun dot(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] }
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        for (i in b.indices) {
            val pivot = (i until b.size).maxBy { abs(a[it][i]) }
            val row = a[i]; a[i] = a[pivot]; a[pivot] = row
            val v = b[i]; b[i] = b[pivot]; b[pivot] = v
            check(abs(a[i][i]) > 1e-15)
            val divisor = a[i][i]; for (k in i until b.size) a[i][k] /= divisor; b[i] /= divisor
            for (j in b.indices) if (j != i) { val f = a[j][i]; for (k in i until b.size) a[j][k] -= f * a[i][k]; b[j] -= f * b[i] }
        }
        return b
    }
}
