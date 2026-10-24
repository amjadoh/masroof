package com.baraa.masroof.lab.analytics

import org.junit.Assert.*
import org.junit.Test

class SpendingObservationTest {
    private fun fact(id: String, day: Long, amount: Long = 4000, scope: String? = "BANK_A:card:1234|SAR|EXPENSE") =
        FinancialFact(id, day, amount, false, true, "Merchant", true, "card", comparisonScope = scope)
    private val history = (0..5).map { fact("prior-$it", it * 7L) }
    private val candidate = fact("current", 42, 9000)
    private fun build(rows: List<FinancialFact>) = SpendingObservationBuilder.build(rows, 40, 45, (0L..45L).toSet())

    @Test fun explainsAnUnusualPaymentFromCoveredPriorDistinctDaysOnly() {
        val observation = build(history + candidate + fact("future", 50, 100000)).single()
        assertEquals("current", observation.id)
        assertEquals(4000L, observation.reference)
        assertEquals(9000L, observation.amount)
        assertEquals(6, observation.referenceIds.size)
        assertFalse(observation.referenceIds.contains("current"))
        assertEquals(history.map { it.id }, observation.referenceIds)
    }

    @Test fun abstainsForSparseShortUncoveredAmbiguousOrUnqualifiedEvidence() {
        val cases = listOf(history.drop(1) + candidate, history.mapIndexed { i, f -> f.copy(day = i.toLong()) } + candidate,
            history + fact("duplicate-day", 0) + candidate, history.map { it.copy(comparisonScope = "BANK_B:card:1234|SAR|EXPENSE") } + candidate,
            history + candidate.copy(refund = true), history + candidate.copy(amount = null),
            history + candidate.copy(included = false), history + candidate.copy(comparisonScope = null))
        cases.forEach { assertTrue(build(it).isEmpty()) }
        assertTrue(SpendingObservationBuilder.build(history + candidate, 40, 45, (1L..45L).toSet()).isEmpty())
        assertTrue(SpendingObservationBuilder.build(history + candidate, 40, 45, (0L..41L).toSet()).isEmpty())
    }

    @Test fun unchangedSpendingAndSmallOrVolatileIncreasesNeverBecomeAnObservation() {
        listOf(4000L, 5999L, 6000L).forEach { assertTrue(build(history + candidate.copy(amount = it)).isEmpty()) }
        assertEquals(1, build(history + candidate.copy(amount = 6001)).size)
        val volatile = history.mapIndexed { i, f -> f.copy(amount = if (i % 2 == 0) 1000 else 9000) }
        assertTrue(build(volatile + candidate).isEmpty())
    }

    @Test fun deletionAndCorrectionRemoveTheObservationAndOnlyThreeDistinctGroupsAreShown() {
        assertTrue(build(history + candidate.copy(amount = 4000)).isEmpty())
        assertTrue(build(history.dropLast(1) + candidate).isEmpty())
        val rows = (0..4).flatMap { group -> (history + candidate).map { it.copy(id = "$group:${it.id}", merchant = "Merchant $group") } }
        assertEquals(3, build(rows).size)
        assertEquals(build(rows), build(rows.reversed()))
        assertEquals(1, build(history + candidate + candidate).size)
    }
}
