package com.baraa.masroof.lab.analytics

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

data class RecurringPattern(val key: String, val name: String, val cadence: String, val nextDay: Long,
    val estimate: Long, val lower: Long, val upper: Long, val sourceIds: List<String>,
    val latestAmount: Long, val referenceAmount: Long?, val amountIncreased: Boolean)

object RecurringDetector {
    fun detect(facts: List<FinancialFact>, today: Long): List<RecurringPattern> = facts
        .filter { it.recurringEligible && it.amount != null && it.amount > 0 && !it.merchant.isNullOrBlank() }
        .groupBy { FinancialCleaner.merchantKey(it.merchant!!) }.mapNotNull { (key, rows) ->
            // A same-day pair is ambiguous evidence, not grounds to discard an entire merchant's history.
            val ordered = rows.distinctBy { it.id }.groupBy { it.day }.values
                .filter { it.size == 1 }.map { it.single() }.sortedBy { it.day }
            if (ordered.size < 3) return@mapNotNull null
            val recent = ordered.takeLast(6)
            val dates = recent.map { LocalDate.ofEpochDay(it.day) }
            val gaps = recent.zipWithNext().map { (a, b) -> (b.day - a.day).toDouble() }
            val cadence = when {
                gaps.all { abs(it - 7) <= 2 } -> "WEEKLY"
                dates.zipWithNext().all { (a, b) -> a.withDayOfMonth(1).plusMonths(1) == b.withDayOfMonth(1) &&
                    (abs(a.dayOfMonth - b.dayOfMonth) <= 5 || (a.dayOfMonth == a.lengthOfMonth() && b.dayOfMonth == b.lengthOfMonth())) } -> "MONTHLY"
                else -> return@mapNotNull null
            }
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
            RecurringPattern(hash("$key|$cadence"), ordered.last().merchant!!, cadence, due.toEpochDay(),
                smoothed.roundToLong(), (smoothed - margin).roundToLong().coerceAtLeast(0), (smoothed + margin).roundToLong(),
                ordered.map { it.id }, last, reference?.roundToLong(), reference != null && last - reference >= maxOf(reference * .2, 1000.0))
        }.sortedBy { it.nextDay }
}
