package com.baraa.masroof.lab.bank.interpretation

import com.baraa.masroof.lab.bank.Blocker
import com.baraa.masroof.lab.bank.Evidence
import com.baraa.masroof.lab.finance.value.Currency
import com.baraa.masroof.lab.finance.value.Money
import java.math.BigDecimal
import java.time.LocalDate

internal data class RoleDetails(
    val due: Money?, val foreign: Money?, val fee: Money?, val rate: BigDecimal?,
    val dueDate: LocalDate?, val counterparty: String?, val reference: String?,
    val biller: String?, val billerCode: String?, val reserved: List<IntRange>,
)

/** Explicit role lines only; quoted names and unlabeled money never acquire financial roles. */
internal class DetailExtractor(
    private val source: SourceText,
    private val configured: Map<String, List<String>>,
    private val evidence: MutableList<Evidence>,
    private val problem: (String, Blocker) -> Unit,
) {
    private data class Entry(val normalized: String, val original: String)
    private val reserved = mutableListOf<IntRange>()
    private val prefixes = (configured.values.flatten() + FEE_LABELS + RATE_LABELS).distinct().map {
        SourceText(it).text.length to Regex("^ *" + source.phrase(it).pattern + "(?=[ \\t:]|$)")
    }

    private fun entries(field: String, labels: List<String>, reserve: Boolean = false): List<Entry> = labels.distinct().flatMap { label ->
        val pattern = Regex("(?m)^ *" + source.phrase(label).pattern + "(?<tail>[^\\n\\r]*)$")
        pattern.findAll(source.text).mapNotNull { match ->
            // "Biller code" is a distinct role, never the value of the shorter "Biller" label.
            val longest = prefixes.filter { it.second.containsMatchIn(match.value) }.maxOfOrNull { it.first } ?: 0
            if (longest > SourceText(label).text.length) return@mapNotNull null
            if (reserve) reserved += match.range
            evidence += source.evidence("extract.detail", field, match.range)
            val tail = match.groups["tail"]!!
            var first = tail.value.indexOfFirst { !it.isWhitespace() }
            val last = tail.value.indexOfLast { !it.isWhitespace() }
            if (first < 0 || !(tail.value.first().isWhitespace() || tail.value.first() == ':')) Entry("", "")
            else {
                if (tail.value[first] == ':') first++
                while (first <= last && tail.value[first].isWhitespace()) first++
                if (first > last) Entry("", "")
                else {
                    val range = (tail.range.first + first)..(tail.range.first + last)
                    Entry(source.text.substring(range), source.original(range))
                }
            }
        }.toList()
    }

    private fun <T : Any> one(field: String, values: List<T?>): T? {
        if (values.any { it == null }) problem("INVALID_$field", Blocker.VALIDATION_ERROR)
        val known = values.filterNotNull().distinct()
        if (known.size > 1) { problem("CONFLICTING_$field", Blocker.CONFLICTING_FACTS); return null }
        return if (values.any { it == null }) null else known.singleOrNull()
    }

    private fun money(field: String, labels: List<String>): Money? =
        one(field, entries(field, labels, reserve = true).map { MoneyGrammar.parse(it.normalized) })

    private fun text(field: String, identifier: Boolean = false): String? =
        one(field, entries(field, configured[field].orEmpty()).map { entry ->
            when {
                entry.normalized.isBlank() || entry.original.length > 160 -> null
                identifier && !entry.normalized.matches(Regex("[a-z0-9][a-z0-9_/-]{0,63}")) -> null
                else -> if (field == "billerCode") entry.normalized else entry.original
            }
        })

    fun read(): RoleDetails {
        val due = money("dueAmount", configured["dueAmount"].orEmpty())
        val foreign = money("foreignAmount", configured["foreignAmount"].orEmpty())
        if (foreign?.currency == Currency.SAR) problem("FOREIGN_ROLE_HAS_LOCAL_CURRENCY", Blocker.CONFLICTING_FACTS)
        // Stable generic grammar, versioned with the engine. Not configurable executable rules.
        val fee = money("internationalFee", FEE_LABELS)
        val rate = one("exchangeRate", entries("exchangeRate", RATE_LABELS, reserve = true).map {
            if (!it.normalized.matches(Regex("[0-9]{1,10}(?:\\.[0-9]{1,10})?"))) null
            else it.normalized.toBigDecimalOrNull()?.takeIf { value -> value.signum() > 0 }?.stripTrailingZeros()
        })
        val date = one("paymentDueDate", entries("paymentDueDate", configured["paymentDueDate"].orEmpty()).map {
            runCatching { LocalDate.parse(it.normalized) }.getOrNull()
        })
        return RoleDetails(due, foreign, fee, rate, date, text("counterparty"), text("transactionReference", true),
            text("biller"), text("billerCode", true), reserved.toList())
    }

    companion object {
        private val FEE_LABELS = listOf("international fee", "fee amount", "رسوم العمليات الدولية", "مبلغ الرسوم")
        private val RATE_LABELS = listOf("exchange rate", "سعر الصرف")
    }
}
