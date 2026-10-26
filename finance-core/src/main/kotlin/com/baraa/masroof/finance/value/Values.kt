package com.baraa.masroof.finance.value

import java.math.BigDecimal
import java.math.RoundingMode

/** Same supported currencies and magnitude/rounding convention as production Money. */
enum class Currency { SAR, USD, EUR, GBP }

class Money private constructor(val amount: BigDecimal, val currency: Currency) {
    override fun equals(other: Any?): Boolean =
        other is Money && currency == other.currency && amount.compareTo(other.amount) == 0

    override fun hashCode(): Int = 31 * amount.stripTrailingZeros().hashCode() + currency.hashCode()

    companion object {
        fun of(amount: BigDecimal, currency: Currency): Money {
            val normalized = amount.setScale(2, RoundingMode.HALF_EVEN)
            require(normalized.signum() >= 0) { "Money is a non-negative magnitude" }
            return Money(normalized, currency)
        }

        fun of(amount: String, currency: Currency): Money = of(BigDecimal(amount), currency)
    }
}

/** Stable catalog ID, never a translated name. Unknown routing has no BankId. */
@JvmInline
value class BankId(val value: String) {
    init {
        require(value.isNotBlank() && value != "UNKNOWN") { "A known bank is required" }
    }
}
