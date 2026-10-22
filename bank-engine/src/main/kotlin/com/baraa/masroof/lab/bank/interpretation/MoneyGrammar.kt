package com.baraa.masroof.lab.bank.interpretation

import com.baraa.masroof.lab.finance.value.Currency
import com.baraa.masroof.lab.finance.value.Money
import java.util.Locale

/** One bounded grammar for principal and secondary money. Never rounds unsupported precision. */
internal object MoneyGrammar {
    private const val NUMBER = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?"
    private const val CURRENCY = "(?:sar|usd|eur|gbp|ريال سعودي|ريال|ر\\.س\\.?)"
    const val VALUE = "(?:(?<c1>$CURRENCY)[ \\t]+(?<n1>$NUMBER)|(?<n2>$NUMBER)[ \\t]+(?<c2>$CURRENCY))(?![\\p{L}\\p{N}_.,+\\-−])"
    private val entire = Regex(VALUE)

    fun parse(value: String): Money? = entire.matchEntire(value)?.let(::read)

    /** Only explicitly labelled principal currency may complete a bare positive magnitude. */
    fun withCurrency(magnitude: String, currency: String): Money? =
        magnitude.takeIf { it.matches(Regex(NUMBER)) }?.let { parse("$currency $it") }

    fun read(match: MatchResult): Money? {
        val number = (match.groups["n1"] ?: match.groups["n2"])?.value ?: return null
        if (number.length > 18) return null
        val currency = (match.groups["c1"] ?: match.groups["c2"])?.value ?: return null
        val code = when (currency) {
            "ريال", "ريال سعودي", "ر.س", "ر.س." -> "SAR"
            else -> currency.uppercase(Locale.ROOT)
        }
        return runCatching { Money.of(number.replace(",", ""), Currency.valueOf(code)) }.getOrNull()
    }
}
