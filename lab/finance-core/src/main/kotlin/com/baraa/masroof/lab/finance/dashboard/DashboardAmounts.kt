package com.baraa.masroof.lab.finance.dashboard

import com.baraa.masroof.lab.finance.value.Currency
import com.baraa.masroof.lab.finance.value.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/** Signed result: refund-heavy spending and negative balances/remaining values must survive.
 * No rounding here: the owning calculator supplies the authoritative monetary precision.
 */
data class SignedAmount(val amount: BigDecimal, val currency: Currency) {
    init { require(amount.stripTrailingZeros().scale() <= 2) }
}

enum class PeriodKind { SALARY, CALENDAR_MONTH, STATEMENT, SELECTED }

data class DisplayPeriod(
    val from: Instant,
    val until: Instant,
    val zone: ZoneId,
    val kind: PeriodKind,
    val label: String?,
) {
    init { require(from < until) }
}

/** Null means unavailable/incomplete; zero means an observed or fully computed zero. No sums here. */
data class PortfolioTotals(
    val currency: Currency,
    val accountInflow: Money?,
    val accountOutflow: Money?,
    val accountRemaining: SignedAmount?,
    val externalRemaining: SignedAmount?,
    val facilityDue: Money?,
    val creditSalarySpending: SignedAmount?,
    val debitSalarySpending: SignedAmount?,
    val loanRemaining: Money?,
    val loanSalaryPayment: SignedAmount?,
    val creditStatementSpending: SignedAmount? = null,
) {
    init {
        requireCurrency(currency, accountInflow, accountOutflow, facilityDue, loanRemaining)
        requireSignedCurrency(currency, accountRemaining, externalRemaining, creditSalarySpending,
            debitSalarySpending, loanSalaryPayment,creditStatementSpending)
    }
}

/** Production flow buckets, including internal transfers separately from external totals. */
enum class FlowBucket {
    SALARY, OTHER_INCOME, EXTERNAL_IN, ACCOUNT_REFUNDS, INTERNAL_IN,
    EXTERNAL_OUT, CREDIT_SETTLEMENT, CASH_WITHDRAWAL, BILLS, POS_PURCHASES, FEES, INTERNAL_OUT, LOAN,
}

data class AccountFlow(
    val currency: Currency,
    val buckets: Map<FlowBucket, Money>,
    val totalIn: Money,
    val totalOut: Money,
    val externalIn: Money,
    val externalOut: Money,
    val remaining: SignedAmount,
    val cashPosition: SignedAmount,
) {
    init {
        require(buckets.keys == FlowBucket.entries.toSet()) { "Missing flow is not zero" }
        requireCurrency(currency, *buckets.values.toTypedArray(), totalIn, totalOut, externalIn, externalOut)
        requireSignedCurrency(currency, remaining, cashPosition)
    }
}

internal fun requireCurrency(currency: Currency, vararg amounts: Money?) {
    require(amounts.filterNotNull().all { it.currency == currency }) { "Mixed currency projection" }
}

internal fun requireSignedCurrency(currency: Currency, vararg amounts: SignedAmount?) {
    require(amounts.filterNotNull().all { it.currency == currency }) { "Mixed currency projection" }
}
