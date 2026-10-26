package com.baraa.masroof.analytics

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

data class RecurringPattern(val key: String, val name: String, val cadence: String, val nextDay: Long,
    val estimate: Long, val lower: Long, val upper: Long, val sourceIds: List<String>,
    val latestAmount: Long, val referenceAmount: Long?, val amountIncreased: Boolean,
    val seriesKey: String = "", val origin: String = "RULES", val modelVersion: String? = null)

data class RecurringSeries(val key: String, val rows: List<FinancialFact>) {
    val dates = rows.takeLast(6).map { LocalDate.ofEpochDay(it.day) }
    private val gaps = rows.takeLast(6).zipWithNext().map { (a, b) -> (b.day - a.day).toDouble() }
    val ruleLabel: RecurringLabel get() = when {
        gaps.all { abs(it - 7) <= 2 } -> RecurringLabel.WEEKLY
        dates.zipWithNext().all { (a, b) -> a.withDayOfMonth(1).plusMonths(1) == b.withDayOfMonth(1) &&
            (abs(a.dayOfMonth - b.dayOfMonth) <= 5 || (a.dayOfMonth == a.lengthOfMonth() && b.dayOfMonth == b.lengthOfMonth())) } -> RecurringLabel.MONTHLY
        else -> RecurringLabel.NONE
    }
    val features: List<Double> get() {
        val amounts = rows.takeLast(6).map { it.amount!!.toDouble() }
        val gap = median(gaps)
        val amount = median(amounts).coerceAtLeast(1.0)
        return listOf(rows.takeLast(6).size / 6.0, gap / 31,
            median(gaps.map { abs(it - gap) }) / 31, gaps.map { abs(it - 7) }.average() / 31,
            dates.zipWithNext().map { (a, b) -> abs(b.toEpochDay() - a.plusMonths(1).toEpochDay()).toDouble() }.average() / 31,
            median(amounts.map { abs(it - amount) }) / amount, amounts.last() / amount,
            (rows.last().day - rows.takeLast(6).first().day) / 180.0).map { it.coerceIn(0.0, 4.0) }
    }
}

object RecurringDetector {
    fun series(facts: List<FinancialFact>): List<RecurringSeries> = facts
        .filter { it.recurringEligible && !it.refund && it.amount != null && it.amount > 0 && !it.merchant.isNullOrBlank() }
        .groupBy { listOfNotNull(it.recurringScope, FinancialCleaner.merchantKey(it.merchant!!)).joinToString("|") }
        .mapNotNull { (identity, rows) ->
            // A same-day pair is ambiguous evidence, not grounds to discard an entire merchant's history.
            val ordered = rows.distinctBy { it.id }.groupBy { it.day }.values
                .filter { it.size == 1 }.map { it.single() }.sortedBy { it.day }
            if (ordered.size < 3) null else RecurringSeries(hash("series:$identity"), ordered)
        }

    fun detect(facts: List<FinancialFact>, today: Long,
        classify: ((RecurringSeries) -> RecurringPrediction?)? = null): List<RecurringPattern> = series(facts).mapNotNull { series ->
            val ordered = series.rows
            val recent = ordered.takeLast(6)
            val dates = series.dates
            val prediction = if (classify == null) RecurringPrediction(series.ruleLabel, 1.0, "RULES") else classify(series)
            val cadence = prediction?.label?.takeIf { it != RecurringLabel.NONE }?.name ?: return@mapNotNull null
            fun next(date: LocalDate): LocalDate = if (cadence == "WEEKLY") date.plusWeeks(1) else
                date.plusMonths(1).let { if (date.dayOfMonth == date.lengthOfMonth()) it.withDayOfMonth(it.lengthOfMonth()) else it }
            val due = next(dates.last())
            if (today > due.toEpochDay() + if (cadence == "WEEKLY") 2 else 5) return@mapNotNull null
            val amounts = recent.map { it.amount!!.toDouble() }
            var smoothed = amounts.first(); amounts.drop(1).forEach { smoothed = .3 * it + .7 * smoothed }
            val med = median(amounts); val mad = median(amounts.map { abs(it - med) })
            val margin = maxOf(mad * 2, amounts.max() - amounts.min())
            val reference = ordered.dropLast(1).takeLast(6).map { it.amount!!.toDouble() }.takeIf { it.size >= 3 }?.let(::median)
            val last = ordered.last().amount!!
            RecurringPattern(hash("${series.key}|$cadence"), ordered.last().merchant!!, cadence, due.toEpochDay(),
                smoothed.roundToLong(), (smoothed - margin).roundToLong().coerceAtLeast(0), (smoothed + margin).roundToLong(),
                ordered.map { it.id }, last, reference?.roundToLong(), reference != null && last - reference >= maxOf(reference * .2, 1000.0),
                series.key, prediction.origin, prediction.modelVersion)
        }.sortedBy { it.nextDay }
}
