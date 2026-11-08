package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import java.time.Instant
import java.time.LocalDate

data class CreditCardBalanceSnapshot(
    val availableBalance: Money?,
    val dueAmount: Money?,
    val dueDate: LocalDate?,
    val statementIssuedAt: Instant?,
    val updatedAt: Instant,
    val negativeAvailableBalance: Money? = null,
    /** When the available balance above was reported (purchase/refund SMS), not the statement time. */
    val availableUpdatedAt: Instant? = null,
)

data class CreditCardDashboardRow(
    val bank: Bank,
    val last4: String,
    val statementSpendingNet: SignedMoneyAmount,
    val salaryPeriodSpendingNet: SignedMoneyAmount,
    val statementPeriodLabel: String?,
    val snapshot: CreditCardBalanceSnapshot?,
    /** Posted [com.baraa.masroof.domain.model.FinancialTransactionType.CREDIT_CARD_PAYMENT] into this card since its statement window start. */
    val paymentsSinceStatement: Money = Money.zero(statementSpendingNet.currency),
)

/**
<<<<<<< HEAD
 * All credit cards with statement-cycle and salary-period spending.
=======
 * Estimated amount owed now on one credit facility:
 * latest statement due + net card spending after that statement − card payments after it.
 *
 * Null when no statement due is known (the opening balance would be a guess) or currencies differ.
 * A negative value means the card is in credit.
 */
fun estimateCurrentOwed(
    due: StatementDueSnapshot?,
    cards: List<CreditCardDashboardRow>,
): SignedMoneyAmount? {
    if (due == null) return null
    val currency = due.amount.currency
    if (cards.any { it.statementSpendingNet.currency != currency || it.paymentsSinceStatement.currency != currency }) {
        return null
    }
    val spending = cards.fold(java.math.BigDecimal.ZERO) { total, row -> total + row.statementSpendingNet.amount }
    val payments = cards.fold(java.math.BigDecimal.ZERO) { total, row -> total + row.paymentsSinceStatement.amount }
    return SignedMoneyAmount(
        (due.amount.amount + spending - payments).setScale(Money.SCALE, java.math.RoundingMode.HALF_EVEN),
        currency,
    )
}

/**
 * All credit cards with dual spending windows (calendar month + statement cycle).
>>>>>>> 9542f2cf (Add SAB Visa card companion, biometric app lock and owed-now estimate)
 */
data class CreditCardsOverview(
    val cards: List<CreditCardDashboardRow>,
    val aggregateStatementPeriodLabel: String?,
    val salaryPeriodLabel: String?,
    val currency: Currency,
) {
    val hasContent: Boolean
        get() = cards.isNotEmpty()
}

/**
 * Latest statement due among [cards] — one value for linked cards on the same credit facility.
 * Ignores purchase/refund SMS outstanding balances.
 */
fun resolveLatestStatementDue(cards: List<CreditCardDashboardRow>): StatementDueSnapshot? =
    cards.mapNotNull { row ->
        val snap = row.snapshot ?: return@mapNotNull null
        val amount = snap.dueAmount ?: return@mapNotNull null
        val issuedAt = snap.statementIssuedAt ?: return@mapNotNull null
        StatementDueSnapshot(amount = amount, updatedAt = issuedAt, dueDate = snap.dueDate)
    }.maxByOrNull { it.updatedAt }

data class StatementDueSnapshot(
    val amount: Money,
    val updatedAt: Instant,
    val dueDate: LocalDate?,
)

/** Keep the amount and date from one statement; never borrow an older date. */
fun CreditCardDashboardRow.statementDueFor(facility: CreditFacilityOverview? = null): StatementDueSnapshot? {
    val matchingFacility = facility?.takeIf { candidate ->
        candidate.bank == bank && candidate.allCards.any { it.bank == bank && it.last4 == last4 }
    }
    return matchingFacility?.facilityDue ?: resolveLatestStatementDue(listOf(this))
}

internal fun sumCreditCardSpending(
    rows: List<CreditCardDashboardRow>,
    selector: (CreditCardDashboardRow) -> SignedMoneyAmount,
): SignedMoneyAmount =
    if (rows.isEmpty()) SignedMoneyAmount.zero(Currency.SAR) else SignedMoneyAmount(
        rows.sumOf { selector(it).amount }.setScale(Money.SCALE, java.math.RoundingMode.HALF_EVEN),
        rows.first().statementSpendingNet.currency,
    )
