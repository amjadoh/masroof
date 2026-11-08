package com.baraa.masroof.application.dashboard

import java.time.LocalDate

/** A card's amount due from its companion (limit, installments, latest balance). Negative is a credit. */
data class CompanionDue(val amount: SignedMoneyAmount, val payBy: LocalDate)

/** Resolves [CompanionDue] per card, keyed `bankId:last4`. Cards without a limit are absent. */
fun interface CardDueSource {
    suspend fun dues(cards: List<CreditCardDashboardRow>): Map<String, CompanionDue>

    companion object {
        val NONE = CardDueSource { emptyMap() }

        fun key(row: CreditCardDashboardRow): String = "${row.bank.id}:${row.last4.trim()}"
    }
}

/**
 * Replaces each facility's bank-statement due with its primary card's companion due: the amount
 * to pay before the statement closes. A credit becomes a zero due (settled) and a negative current owed.
 */
internal fun CreditFacilitiesOverview.withCompanionDues(
    dues: Map<String, CompanionDue>,
    now: java.time.Instant,
): CreditFacilitiesOverview {
    if (dues.isEmpty()) return this
    return copy(facilities = facilities.map { facility ->
        val due = dues[CardDueSource.key(facility.primary)] ?: return@map facility
        if (due.amount.currency != facility.currency) return@map facility
        val owed = due.amount.amount.max(java.math.BigDecimal.ZERO).setScale(com.baraa.masroof.core.money.Money.SCALE)
        facility.copy(
            facilityDue = StatementDueSnapshot(com.baraa.masroof.core.money.Money(owed, due.amount.currency), now, due.payBy),
            currentOwed = due.amount,
            companionDue = due,
        )
    })
}
