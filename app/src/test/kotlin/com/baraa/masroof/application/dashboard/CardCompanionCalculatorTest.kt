package com.baraa.masroof.application.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Synthetic figures. Amounts are halalas. */
class CardCompanionCalculatorTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private fun at(date: String, time: String = "12:00:00") = LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant()

    private val plan = CompanionPlan(
        id = "p1", name = "جوال", principal = 300_000, months = 12,
        firstDue = LocalDate.parse("2026-05-30"), createdAt = at("2026-05-10"),
    )

    @Test
    fun installmentRemainders_goToFirstMonths_andSumToPrincipal() {
        val odd = plan.copy(principal = 100_001, months = 3)
        assertEquals(listOf(33_334L, 33_334L, 33_333L), (0 until 3).map(odd::installment))
    }

    @Test
    fun withoutStatement_billedMonthsFollowClosings() {
        val position = CardCompanionCalculator.compute(
            input(now = at("2026-11-08"), plans = listOf(plan)),
        )

        val progress = position.plans.single()
        // Closings 30/5 .. 30/10 have passed: 6 billed, 6 left.
        assertEquals(6, progress.billed)
        assertEquals(6, progress.left)
        assertEquals(150_000L, progress.remainingPrincipal)
        assertFalse(progress.verified)
    }

    @Test
    fun withoutStatement_toPaySplitsInstallmentFromPurchases() {
        // Limit 15,000; unbilled plan principal 1,500; available 10,950.
        // Latest billed installment (30/10) is 250 and unpaid -> purchases = 15000-1500-10950-250 = 2300.
        val position = CardCompanionCalculator.compute(
            input(now = at("2026-11-08"), plans = listOf(plan), available = 1_095_000, availableAt = at("2026-11-07")),
        )

        assertEquals(25_000L, position.installmentDue)
        assertEquals(230_000L, position.purchasesDue)
        assertEquals(255_000L, position.toPay)
        assertEquals(0L, position.credit)
        assertEquals(LocalDate.parse("2026-11-30"), position.purchaseGoal)
    }

    @Test
    fun laterMovements_estimateLiveAvailable() {
        val position = CardCompanionCalculator.compute(
            input(
                now = at("2026-11-08"),
                available = 1_000_000,
                availableAt = at("2026-11-01"),
                movements = listOf(
                    CompanionMovement(at("2026-11-03"), CompanionMovementKind.PURCHASE, 12_000),
                    CompanionMovement(at("2026-11-05"), CompanionMovementKind.PAYMENT, 50_000),
                ),
            ),
        )

        assertEquals(1_038_000L, position.available)
        assertTrue(position.availableEstimated)
    }

    @Test
    fun verifiedStatement_paymentsSettleOldestInstallmentFirst() {
        val bill = CompanionBill(
            id = "b-oct", issued = LocalDate.parse("2026-10-30"), due = LocalDate.parse("2026-11-25"),
            limit = 1_500_000, available = 1_000_000, unbilledPrincipal = 150_000, installment = 25_000,
            totalDue = 350_000, minimumDue = 17_500, planId = "p1", futureMonths = 6,
        )
        val position = CardCompanionCalculator.compute(
            input(
                now = at("2026-11-08"),
                plans = listOf(plan),
                bills = listOf(bill),
                available = 1_000_000,
                availableAt = at("2026-11-02"),
                movements = listOf(CompanionMovement(at("2026-11-04"), CompanionMovementKind.PAYMENT, 10_000)),
            ),
        )

        assertTrue(position.verified)
        assertEquals(15_000L, position.installmentDue)
        assertEquals(LocalDate.parse("2026-11-25"), position.installmentBy)
        assertTrue(position.plans.single().verified)
        assertEquals(6, position.plans.single().billed)
    }

    @Test
    fun overpayment_isCreditAndNeverClearsInstallment() {
        val bill = CompanionBill(
            id = "b", issued = LocalDate.parse("2026-10-30"), due = LocalDate.parse("2026-11-25"),
            limit = 1_500_000, available = 1_400_000, unbilledPrincipal = 0, installment = 25_000,
            totalDue = 25_000, minimumDue = 25_000,
        )
        val position = CardCompanionCalculator.compute(
            input(now = at("2026-11-08"), bills = listOf(bill), available = 1_480_000, availableAt = at("2026-11-07")),
        )

        assertEquals(25_000L, position.installmentDue)
        assertEquals(0L, position.purchasesDue)
        assertEquals(5_000L, position.credit)
        assertEquals(25_000L, position.toPay)
    }

    @Test
    fun withoutLimit_moneyFiguresStayUnknown() {
        val position = CardCompanionCalculator.compute(
            input(now = at("2026-11-08"), limit = null, available = 500_000, availableAt = at("2026-11-07")),
        )

        assertNull(position.toPay)
        assertEquals(500_000L, position.available)
    }

    @Test
    fun missedInstallment_staysDueIntoTheNextMonth() {
        // Paid 30/9's installment on 5/10, nothing after the 30/10 closing: only 30/10's 250 is due.
        val paidSeptember = CardCompanionCalculator.compute(input(now = at("2026-11-08"), plans = listOf(plan),
            available = 1_095_000, availableAt = at("2026-11-07"),
            movements = listOf(CompanionMovement(at("2026-10-05"), CompanionMovementKind.PAYMENT, 25_000))))
        assertEquals(25_000L, paidSeptember.installmentDue)

        // Paid 30/8's installment on 5/9 and then nothing: 30/9 and 30/10 are both still due.
        val missedSeptember = CardCompanionCalculator.compute(input(now = at("2026-11-08"), plans = listOf(plan),
            available = 1_095_000, availableAt = at("2026-11-07"),
            movements = listOf(CompanionMovement(at("2026-09-05"), CompanionMovementKind.PAYMENT, 25_000))))
        assertEquals(50_000L, missedSeptember.installmentDue)
        assertEquals(missedSeptember.installmentDue!! + missedSeptember.purchasesDue!!, missedSeptember.amountDue)
    }

    @Test
    fun payingTheInstallment_leavesOnlyPurchasesDue_andFreesTheSameAmount() {
        // Limit 15,000; unbilled 1,500; 250 billed on 30/10. Available rises by 250 after paying it.
        val before = CardCompanionCalculator.compute(input(now = at("2026-11-08"), plans = listOf(plan),
            available = 1_095_000, availableAt = at("2026-11-07")))
        val after = CardCompanionCalculator.compute(input(now = at("2026-11-08"), plans = listOf(plan),
            available = 1_095_000, availableAt = at("2026-11-07"),
            movements = listOf(CompanionMovement(at("2026-11-07", "18:00:00"), CompanionMovementKind.PAYMENT, 25_000))))
        assertEquals(1_120_000L, after.available)
        assertEquals(0L, after.installmentDue)
        assertEquals(before.purchasesDue, after.purchasesDue)
        assertEquals(before.purchasesDue, after.amountDue)
    }

    @Test
    fun creditBalance_isANegativeAmountDue() {
        // Limit 500, nothing owed, available 966.86: 466.86 in your favour.
        val position = CardCompanionCalculator.compute(input(now = at("2026-11-08"), limit = 50_000,
            available = 96_686, availableAt = at("2026-11-07")))
        assertEquals(-46_686L, position.amountDue)
        assertEquals(0L, position.toPay)
        assertEquals(46_686L, position.credit)
    }

    private fun input(
        now: Instant,
        limit: Long? = 1_500_000,
        available: Long? = null,
        availableAt: Instant? = null,
        plans: List<CompanionPlan> = emptyList(),
        bills: List<CompanionBill> = emptyList(),
        movements: List<CompanionMovement> = emptyList(),
    ) = CompanionInput(
        limit = limit,
        smsAvailable = available,
        smsAvailableAt = availableAt,
        smsDueDate = null,
        plans = plans,
        bills = bills,
        movements = movements,
        now = now,
        zone = zone,
    )
}
