package com.baraa.masroof.application.credit

import com.baraa.masroof.application.dashboard.CardCompanionCalculator
import com.baraa.masroof.application.dashboard.CompanionBill
import com.baraa.masroof.application.dashboard.CompanionInput
import com.baraa.masroof.application.dashboard.CompanionMovement
import com.baraa.masroof.application.dashboard.CompanionMovementKind
import com.baraa.masroof.application.dashboard.CompanionPlan
import com.baraa.masroof.application.dashboard.CompanionPosition
import com.baraa.masroof.bank.sab.SabInstallmentPlanReader
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.RawSmsRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Credit-card companion: the SAB Visa tracker's view of one card inside Masroof.
 *
 * Reads posted transactions and stored SMS; writes only the companion profile
 * (limit, closing day, discovered plans, verified statement figures). It never posts,
 * hides or reclassifies ledger rows.
 */
class CardCompanionWorkflow(
    private val profiles: CardCompanionProfileRepository,
    private val rawSmsRepository: RawSmsRepository,
    private val transactions: FinancialTransactionRepository,
    private val zone: () -> ZoneId = { ZoneId.of("Asia/Riyadh") },
    private val clock: () -> Instant = Instant::now,
) {
    data class Snapshot(
        val cardKey: String,
        val bank: Bank,
        val last4: String,
        val profile: CardCompanionProfile,
        val position: CompanionPosition,
    )

    /** Latest card facts already computed by the dashboard; the workflow does not re-parse SMS for them. */
    data class SmsFacts(
        val available: Money? = null,
        val negativeAvailable: Money? = null,
        val availableAt: Instant? = null,
        val statementDueDate: LocalDate? = null,
    )

    suspend fun load(bank: Bank, last4: String, facts: SmsFacts): Snapshot {
        val key = cardKey(bank, last4)
        val now = clock()
        var profile = profiles.get(key)
        if (bank.id == SAB_BANK_ID) {
            val discovered = discoverSabPlans(last4, profile)
            if (discovered != profile) {
                profiles.save(key, discovered)
                profile = discovered
            }
        }
        val movements = movements(bank, last4, now)
        val smsAvailable = facts.negativeAvailable?.let { -halalas(it) } ?: facts.available?.let(::halalas)
        val position = CardCompanionCalculator.compute(
            CompanionInput(
                limit = profile.limitHalalas,
                smsAvailable = smsAvailable,
                smsAvailableAt = facts.availableAt,
                smsDueDate = facts.statementDueDate,
                plans = profile.plans.map { it.toPlan() },
                bills = profile.bills.map { it.toBill() },
                movements = movements,
                now = now,
                zone = zone(),
                closeDay = profile.closeDay,
            ),
        )
        return Snapshot(key, bank, last4, profile, position)
    }

    fun setLimit(bank: Bank, last4: String, limit: BigDecimal) {
        val value = limit.setScale(2, java.math.RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        require(value > 0)
        update(bank, last4) { it.copy(limitHalalas = value) }
    }

    fun setCloseDay(bank: Bank, last4: String, day: Int) {
        require(day in 1..31)
        update(bank, last4) { it.copy(closeDay = day, closeDayConfirmed = true) }
    }

    /** First-time setup: the card's limit and its statement day together. */
    fun setup(bank: Bank, last4: String, limit: BigDecimal, day: Int) {
        require(day in 1..31)
        val value = limit.setScale(2, java.math.RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        require(value > 0)
        update(bank, last4) { it.copy(limitHalalas = value, closeDay = day, closeDayConfirmed = true) }
    }

    /** One card's signed amount due and the date to pay it by. */
    data class Due(val amountHalalas: Long, val payBy: LocalDate)

    /**
     * Read-only amount due for each card that has a limit, for the dashboard and reminders.
     * Uses the stored plans (no SMS scan) and one ledger read shared by every card.
     */
    suspend fun dues(cards: List<Pair<Bank, String>>, facts: Map<String, SmsFacts>): Map<String, Due> {
        val configured = cards.map { (bank, last4) -> Triple(bank, last4, profiles.get(cardKey(bank, last4))) }
            .filter { it.third.limitHalalas != null }
        if (configured.isEmpty()) return emptyMap()
        val now = clock()
        val ledger = transactions.listOccurredBetween(now.minus(LOOKBACK), now.plusSeconds(1))
        return configured.mapNotNull { (bank, last4, profile) ->
            val key = cardKey(bank, last4)
            val fact = facts[key] ?: SmsFacts()
            val position = CardCompanionCalculator.compute(input(profile, fact, movementsOf(ledger, bank, last4), now))
            position.amountDue?.let { key to Due(it, position.payBy) }
        }.toMap()
    }

    private fun input(profile: CardCompanionProfile, facts: SmsFacts, movements: List<CompanionMovement>, now: Instant) =
        CompanionInput(
            limit = profile.limitHalalas,
            smsAvailable = facts.negativeAvailable?.let { -halalas(it) } ?: facts.available?.let(::halalas),
            smsAvailableAt = facts.availableAt,
            smsDueDate = facts.statementDueDate,
            plans = profile.plans.map { it.toPlan() },
            bills = profile.bills.map { it.toBill() },
            movements = movements,
            now = now,
            zone = zone(),
            closeDay = profile.closeDay,
        )

    fun renamePlan(bank: Bank, last4: String, planId: String, name: String) {
        val clean = name.trim().take(MAX_PLAN_NAME)
        require(clean.isNotEmpty())
        update(bank, last4) { p -> p.copy(plans = p.plans.map { if (it.id == planId) it.copy(name = clean) else it }) }
    }

    fun dismissPlan(bank: Bank, last4: String, planId: String) {
        update(bank, last4) { p ->
            p.copy(plans = p.plans.filterNot { it.id == planId }, dismissedPlanIds = p.dismissedPlanIds + planId)
        }
    }

    /** Adds verified statement figures. An identical re-import is a no-op; a conflicting one is refused. */
    fun addBill(bank: Bank, last4: String, bill: StoredBill) {
        update(bank, last4) { p ->
            val existing = p.bills.firstOrNull { it.issued == bill.issued || it.id == bill.id }
            when {
                existing == null -> p.copy(bills = p.bills + bill, limitHalalas = p.limitHalalas ?: bill.limitHalalas)
                existing == bill -> p
                else -> throw IllegalStateException("A different statement already exists for ${bill.issued}")
            }
        }
    }

    private fun update(bank: Bank, last4: String, change: (CardCompanionProfile) -> CardCompanionProfile) {
        val key = cardKey(bank, last4)
        profiles.save(key, change(profiles.get(key)))
    }

    private suspend fun discoverSabPlans(last4: String, profile: CardCompanionProfile): CardCompanionProfile {
        val known = profile.plans.map { it.id }.toSet() + profile.dismissedPlanIds
        val found = mutableListOf<StoredPlan>()
        rawSmsRepository.listIdsByReceivedAt().chunked(SCAN_CHUNK).forEach { ids ->
            rawSmsRepository.getByIds(ids).forEach raw@{ raw ->
                if (!raw.sender.trim().equals(SAB_SENDER, ignoreCase = true)) return@raw
                val id = "sms:${raw.id}"
                if (id in known || found.any { it.id == id }) return@raw
                val plan = SabInstallmentPlanReader.read(raw.body) ?: return@raw
                // A confirmation for another card never lands here; one without a card suffix is accepted.
                if (plan.cardLast4 != null && plan.cardLast4 != last4) return@raw
                val first = CardCompanionCalculator.firstBillingClose(raw.receivedAt, zone(), profile.closeDay)
                found += StoredPlan(
                    id = id,
                    name = "تقسيط ${formatWhole(plan.principalHalalas)}",
                    principalHalalas = plan.principalHalalas,
                    months = plan.months,
                    firstDue = first.toString(),
                    createdAtEpochMillis = raw.receivedAt.toEpochMilli(),
                )
            }
        }
        return if (found.isEmpty()) profile else profile.copy(plans = (profile.plans + found).sortedBy { it.createdAtEpochMillis })
    }

    private suspend fun movements(bank: Bank, last4: String, now: Instant): List<CompanionMovement> =
        movementsOf(transactions.listOccurredBetween(now.minus(LOOKBACK), now.plusSeconds(1)), bank, last4)

    private fun movementsOf(ledger: List<com.baraa.masroof.domain.model.FinancialTransaction>, bank: Bank, last4: String): List<CompanionMovement> {
        val cardId = FinancialContainerIdFactory.cardId(bank, last4)
        return ledger.mapNotNull { tx ->
            if (tx.amount.currency != Currency.SAR) return@mapNotNull null
            val kind = when (tx.type) {
                FinancialTransactionType.EXPENSE, FinancialTransactionType.BILL_PAYMENT ->
                    CompanionMovementKind.PURCHASE.takeIf { tx.sourceContainerId == cardId }
                FinancialTransactionType.FEE -> CompanionMovementKind.FEE.takeIf { tx.sourceContainerId == cardId }
                FinancialTransactionType.REFUND -> CompanionMovementKind.REFUND.takeIf { tx.destinationContainerId == cardId }
                FinancialTransactionType.CREDIT_CARD_PAYMENT ->
                    CompanionMovementKind.PAYMENT.takeIf { tx.destinationContainerId == cardId }
                else -> null
            } ?: return@mapNotNull null
            CompanionMovement(tx.occurredAt, kind, halalas(tx.amount))
        }
    }

    private fun StoredPlan.toPlan() = CompanionPlan(
        id = id,
        name = name,
        principal = principalHalalas,
        months = months,
        firstDue = LocalDate.parse(firstDue),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    )

    private fun StoredBill.toBill() = CompanionBill(
        id = id,
        issued = LocalDate.parse(issued),
        due = LocalDate.parse(due),
        limit = limitHalalas,
        available = availableHalalas,
        unbilledPrincipal = unbilledPrincipalHalalas,
        installment = installmentHalalas,
        totalDue = totalDueHalalas,
        minimumDue = minimumDueHalalas,
        planId = planId,
        futureMonths = futureMonths,
    )

    companion object {
        const val SAB_BANK_ID: String = "BANK_SAB"
        private const val SAB_SENDER = "SAB"
        private const val SCAN_CHUNK = 400
        private const val MAX_PLAN_NAME = 40
        private val LOOKBACK: Duration = Duration.ofDays(400)

        fun cardKey(bank: Bank, last4: String): String = "${bank.id}:${last4.trim()}"

        private fun halalas(money: Money): Long = money.amount.movePointRight(2).longValueExact()

        private fun formatWhole(halalas: Long): String =
            java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(halalas / 100)
    }
}
