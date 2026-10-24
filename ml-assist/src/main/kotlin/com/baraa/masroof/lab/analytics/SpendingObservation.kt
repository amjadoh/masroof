package com.baraa.masroof.lab.analytics

import java.math.BigDecimal
import java.math.RoundingMode

data class SpendingObservation(val id: String, val merchant: String, val day: Long, val amount: Long,
    val reference: Long, val referenceIds: List<String>, val from: Long, val through: Long)

/** Descriptive observations only. Never filters actual spending or supplies training labels. */
object SpendingObservationBuilder {
    fun build(facts: List<FinancialFact>, periodStart: Long, today: Long, coveredDays: Set<Long>): List<SpendingObservation> {
        val unique = facts.groupBy { it.id }.map { (_, rows) ->
            require(rows.distinct().size == 1) { "Conflicting financial identity" }
            rows.first()
        }
        return unique.filter { it.included && it.recurringEligible && !it.refund && it.amount != null && it.amount > 0 &&
            !it.comparisonScope.isNullOrBlank() && !it.merchant.isNullOrBlank() && it.day <= today && it.day in coveredDays }
            .groupBy { it.comparisonScope to FinancialCleaner.merchantKey(it.merchant!!) }
            .mapNotNull { (_, rows) ->
                val ordered = rows.groupBy { it.day }.values.filter { it.size == 1 }.map { it.single() }.sortedBy { it.day }
                ordered.asReversed().firstNotNullOfOrNull { current ->
                    if (current.day < periodStart) return@firstNotNullOfOrNull null
                    val prior = ordered.filter { it.day < current.day }.takeLast(12)
                    if (prior.size < 6 || prior.last().day - prior.first().day < 28) return@firstNotNullOfOrNull null
                    val reference = middle(prior.map { BigDecimal.valueOf(it.amount!!) })
                    val mad = middle(prior.map { BigDecimal.valueOf(it.amount!!).subtract(reference).abs() })
                    val increase = BigDecimal.valueOf(current.amount!!).subtract(reference)
                    if (increase <= maxOf(mad.multiply(BigDecimal(3)), reference.multiply(BigDecimal("0.5")), BigDecimal(2000)))
                        return@firstNotNullOfOrNull null
                    SpendingObservation(current.id, current.merchant!!, current.day, current.amount,
                        reference.setScale(0, RoundingMode.HALF_EVEN).longValueExact(), prior.map { it.id }, prior.first().day, prior.last().day)
                }
            }.sortedWith(compareByDescending<SpendingObservation> { it.day }.thenByDescending { it.amount - it.reference }.thenBy { it.id })
            .take(3)
    }

    private fun middle(values: List<BigDecimal>): BigDecimal {
        val sorted = values.sorted(); val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else sorted[mid - 1].add(sorted[mid]).divide(BigDecimal(2))
    }
}
