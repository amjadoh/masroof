package com.baraa.masroof.application.dashboard

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/*
 * Credit-card companion math, ported from the standalone SAB Visa tracker.
 * All amounts are integer halalas; nothing here touches storage or SMS text.
 */

/** A 0%-profit installment plan confirmed by the bank. Installment remainders go to the first months. */
data class CompanionPlan(
    val id: String,
    val name: String,
    val principal: Long,
    val months: Int,
    /** Closing date of the first statement that bills this plan. */
    val firstDue: LocalDate,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank() && months in 1..120 && principal >= months)
    }

    fun installment(index: Int): Long {
        require(index in 0 until months)
        return principal / months + if (index < principal % months) 1 else 0
    }

    fun billingDate(index: Int): LocalDate = firstDue.plusMonths(index.toLong())
}

/** Figures read from one verified bank statement. */
data class CompanionBill(
    val id: String,
    val issued: LocalDate,
    val due: LocalDate,
    val limit: Long,
    val available: Long,
    val unbilledPrincipal: Long,
    val installment: Long,
    val totalDue: Long,
    val minimumDue: Long,
    val planId: String? = null,
    val futureMonths: Int? = null,
) {
    init {
        require(due >= issued && limit > 0 && unbilledPrincipal >= 0 && installment >= 0)
        require(totalDue >= 0 && minimumDue >= 0)
        require((planId == null) == (futureMonths == null))
    }

    fun closedAt(zone: ZoneId): Instant = issued.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
}

enum class CompanionMovementKind { PURCHASE, FEE, REFUND, PAYMENT }

/** A posted card movement. [amount] is positive halalas. */
data class CompanionMovement(
    val at: Instant,
    val kind: CompanionMovementKind,
    val amount: Long,
)

data class CompanionInput(
    val limit: Long?,
    val smsAvailable: Long?,
    val smsAvailableAt: Instant?,
    /** Due date from the latest statement SMS, used before a statement is verified. */
    val smsDueDate: LocalDate?,
    val plans: List<CompanionPlan>,
    val bills: List<CompanionBill>,
    val movements: List<CompanionMovement>,
    val now: Instant,
    val zone: ZoneId,
    val closeDay: Int = 30,
)

data class CompanionPlanProgress(
    val plan: CompanionPlan,
    /** Installments billed so far (bank-verified when [verified]). Not proof of cash payment. */
    val billed: Int,
    val verified: Boolean,
    val remainingPrincipal: Long,
) {
    val left: Int get() = plan.months - billed
    val finished: Boolean get() = billed >= plan.months
}

data class CompanionPosition(
    val limit: Long?,
    val available: Long?,
    val availableEstimated: Boolean,
    val availableAt: Instant?,
    val installmentDue: Long?,
    val installmentBy: LocalDate?,
    val purchasesDue: Long?,
    val credit: Long?,
    val toPay: Long?,
    val payBy: LocalDate,
    val purchaseGoal: LocalDate,
    val plans: List<CompanionPlanProgress>,
    val latestBill: CompanionBill?,
    /** Signed: installment due plus purchases, or a negative credit when nothing is owed. Null until the limit is known. */
    val amountDue: Long? = null,
) {
    val used: Long? get() = if (limit != null && available != null) limit - available else null

    /** Share of the limit in use, 0..1, for the card face. */
    val usedShare: Float
        get() {
            val l = limit ?: return 0f
            val u = used ?: return 0f
            return (u.toDouble() / l).toFloat().coerceIn(0f, 1f)
        }

    val verified: Boolean get() = latestBill != null
}

object CardCompanionCalculator {
    fun compute(input: CompanionInput): CompanionPosition {
        val today = input.now.atZone(input.zone).toLocalDate()
        val goal = nextClose(today, input.closeDay)
        val bills = input.bills.filter { it.closedAt(input.zone) <= input.now }.sortedBy { it.issued }
        val latest = bills.lastOrNull()
        val live = liveAvailable(input, latest)
        val progress = input.plans
            .filter { it.createdAt <= input.now }
            .map { plan -> progressOf(plan, bills, today) }

        val limit = input.limit ?: latest?.limit
        if (limit == null || live == null) {
            return CompanionPosition(
                limit = limit,
                available = live?.first,
                availableEstimated = live?.second ?: false,
                availableAt = live?.third,
                installmentDue = null,
                installmentBy = null,
                purchasesDue = null,
                credit = null,
                toPay = null,
                payBy = goal,
                purchaseGoal = goal,
                plans = progress,
                latestBill = latest,
            )
        }
        val available = live.first

        val installmentDue: Long
        val installmentBy: LocalDate?
        val unbilled: Long
        if (latest != null) {
            val remaining = allocatePayments(bills, input)
            installmentDue = remaining.values.sum()
            installmentBy = bills.filter { (remaining[it.id] ?: 0) > 0 }.minOfOrNull { it.due }
            unbilled = latest.unbilledPrincipal
        } else {
            // Every billed installment stays due until a later card payment covers it, oldest first.
            installmentDue = unpaidBilledInstallments(progress, input, today)
            installmentBy = input.smsDueDate?.takeIf { installmentDue > 0 }
            unbilled = progress.sumOf { it.remainingPrincipal }
        }

        val purchasesNet = limit - unbilled - available - installmentDue
        val purchasesDue = purchasesNet.coerceAtLeast(0)
        val credit = (-purchasesNet).coerceAtLeast(0)
        val toPay = purchasesDue + installmentDue
        // A credit balance shows as a negative amount due, but it never settles an installment.
        val amountDue = if (installmentDue > 0) toPay else purchasesNet
        val payBy = when {
            installmentBy == null -> goal
            purchasesDue > 0 -> minOf(goal, installmentBy)
            else -> installmentBy
        }
        return CompanionPosition(
            limit = limit,
            available = available,
            availableEstimated = live.second,
            availableAt = live.third,
            installmentDue = installmentDue,
            installmentBy = installmentBy,
            purchasesDue = purchasesDue,
            credit = credit,
            toPay = toPay,
            payBy = payBy,
            purchaseGoal = goal,
            plans = progress,
            latestBill = latest,
            amountDue = amountDue,
        )
    }

    /**
     * Installments billed at past closings that no later card payment covered, oldest first.
     * Only closings with payment evidence count: from the closing before the first recorded card
     * payment onward (earlier months were paid before the ledger starts). The latest closing's
     * installment always counts, so a missed month carries into the next one.
     */
    private fun unpaidBilledInstallments(progress: List<CompanionPlanProgress>, input: CompanionInput, today: LocalDate): Long {
        val payments = input.movements.filter { it.kind == CompanionMovementKind.PAYMENT && it.at <= input.now }.sortedBy { it.at }
        val lastClose = previousClose(today, input.closeDay)
        val evidenceFrom = payments.firstOrNull()?.let { previousClose(it.at.atZone(input.zone).toLocalDate(), input.closeDay) } ?: lastClose
        val billed = progress.flatMap { p ->
            (0 until p.billed).map { index -> p.plan.billingDate(index) to p.plan.installment(index) }
        }.filter { (closedOn, _) -> closedOn < today && closedOn >= minOf(evidenceFrom, lastClose) }.sortedBy { it.first }
        if (billed.isEmpty()) return 0
        val remaining = billed.map { it.second }.toLongArray()
        payments.forEach { payment ->
            val paidOn = payment.at.atZone(input.zone).toLocalDate()
            var cash = payment.amount
            billed.forEachIndexed { index, (closedOn, _) ->
                if (cash > 0 && closedOn < paidOn) {
                    val paid = minOf(cash, remaining[index])
                    remaining[index] -= paid
                    cash -= paid
                }
            }
        }
        return remaining.sum()
    }

    /** Latest exact balance (SMS or verified statement), moved by later posted card movements. */
    private fun liveAvailable(input: CompanionInput, latest: CompanionBill?): Triple<Long, Boolean, Instant>? {
        val anchors = buildList {
            if (input.smsAvailable != null && input.smsAvailableAt != null) add(input.smsAvailableAt to input.smsAvailable)
            latest?.let { add(it.closedAt(input.zone) to it.available) }
        }
        val (anchorAt, anchorValue) = anchors.filter { it.first <= input.now }.maxByOrNull { it.first } ?: return null
        val later = input.movements.filter { it.at > anchorAt && it.at <= input.now }
        val change = later.sumOf {
            when (it.kind) {
                CompanionMovementKind.PAYMENT, CompanionMovementKind.REFUND -> it.amount
                CompanionMovementKind.PURCHASE, CompanionMovementKind.FEE -> -it.amount
            }
        }
        val at = later.maxOfOrNull { it.at } ?: anchorAt
        return Triple(anchorValue + change, later.isNotEmpty(), at)
    }

    /** Cash payments after the first verified close settle the oldest billed installment first. */
    private fun allocatePayments(bills: List<CompanionBill>, input: CompanionInput): Map<String, Long> {
        val remaining = bills.associate { it.id to it.installment }.toMutableMap()
        val firstClose = bills.first().closedAt(input.zone)
        input.movements
            .filter { it.kind == CompanionMovementKind.PAYMENT && it.at > firstClose && it.at <= input.now }
            .sortedBy { it.at }
            .forEach { payment ->
                var cash = payment.amount
                bills.filter { it.closedAt(input.zone) < payment.at }.forEach { bill ->
                    val paid = minOf(cash, remaining.getValue(bill.id))
                    remaining[bill.id] = remaining.getValue(bill.id) - paid
                    cash -= paid
                }
            }
        return remaining
    }

    private fun progressOf(plan: CompanionPlan, bills: List<CompanionBill>, today: LocalDate): CompanionPlanProgress {
        val verified = bills.filter { it.planId == plan.id }.maxByOrNull { it.issued }
        val billed = verified?.futureMonths?.let { plan.months - it }
            ?: (0 until plan.months).count { plan.billingDate(it) < today }
        val clamped = billed.coerceIn(0, plan.months)
        val remaining = plan.principal - (0 until clamped).sumOf { plan.installment(it) }
        return CompanionPlanProgress(plan, clamped, verified != null, remaining)
    }

    /** Next statement closing date on or after [date]; short months close on their last day. */
    fun nextClose(date: LocalDate, closeDay: Int): LocalDate {
        val month = YearMonth.from(date)
        val here = month.atDay(minOf(closeDay, month.lengthOfMonth()))
        if (!here.isBefore(date)) return here
        val next = month.plusMonths(1)
        return next.atDay(minOf(closeDay, next.lengthOfMonth()))
    }

    /** Most recent closing date strictly before [date]. */
    fun previousClose(date: LocalDate, closeDay: Int): LocalDate {
        val month = YearMonth.from(date)
        val here = month.atDay(minOf(closeDay, month.lengthOfMonth()))
        if (here.isBefore(date)) return here
        val prev = month.minusMonths(1)
        return prev.atDay(minOf(closeDay, prev.lengthOfMonth()))
    }

    /** First statement close that bills a plan created at [createdAt]. */
    fun firstBillingClose(createdAt: Instant, zone: ZoneId, closeDay: Int): LocalDate =
        nextClose(createdAt.atZone(zone).toLocalDate(), closeDay)
}
