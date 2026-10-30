package com.baraa.masroof.engine.bank.interpretation

import com.baraa.masroof.engine.bank.*
import com.baraa.masroof.finance.value.Money
import java.time.LocalDateTime

/** Primary role-bound fields and their source evidence; family and execution checks stay in the interpreter. */
internal class PrimaryFieldExtractor(
    private val source: SourceText,
    private val labels: (ExtractionField) -> List<String>,
    private val currency: String?,
    private val secondaryRanges: List<IntRange>,
    private val evidence: MutableList<Evidence>,
    private val problem: (String, Blocker) -> Unit,
    private val fallbackCurrency: String? = null,
    private val allowNegativeBalance: Boolean = false,
) {
    private data class Hit<T>(val value: T, val range: IntRange)
    private fun overlaps(range: IntRange) = secondaryRanges.any { range.first <= it.last && it.first <= range.last }
    // Compact bank roles can touch digits (بـ25 / مبلغ25), but never consume a longer word.
    private fun role(label: String) = source.phrase(label).pattern
        .removeSuffix("(?![\\p{L}\\p{N}_])") + "(?![\\p{L}_])"
    private val negativeBalances by lazy {
        (if (allowNegativeBalance) labels(ExtractionField.AVAILABLE_BALANCE) else emptyList()).flatMap { label ->
            Regex("(?m)^ *" + role(label) + "[ \\t]*:?[ \\t]*" + MoneyGrammar.NEGATIVE_BALANCE)
                .findAll(source.text).mapNotNull { match -> MoneyGrammar.read(match)?.let { Hit(it, match.range) } }.toList()
        }
    }
    fun negativeBalance(): Money? {
        negativeBalances.forEach { evidence += source.evidence("extract.money.negative-balance", "AVAILABLE_BALANCE", it.range) }
        if (negativeBalances.map { it.value }.distinct().size > 1) { problem("CONFLICTING_AVAILABLE_BALANCE", Blocker.CONFLICTING_FACTS); return null }
        return negativeBalances.firstOrNull()?.value
    }

    fun money(field: ExtractionField, allowStandalone: Boolean = false, titlePrefixes: List<String> = emptyList()): Money? {
        val hits = labels(field).flatMap { label ->
            val pattern = Regex("(?m)^ *" + role(label) + "[ \\t]*:?[ \\t]*" + MoneyGrammar.VALUE)
            val reverse = Regex("(?m)^ *" + MoneyGrammar.VALUE + "[ \\t]*:[ \\t]*" + source.phrase(label).pattern + " *$")
            val matches = pattern.findAll(source.text).toList() + reverse.findAll(source.text).toList()
            val bareCurrency = currency ?: fallbackCurrency
            val separate = if (field == ExtractionField.AMOUNT && bareCurrency != null) {
                val prefix = Regex("(?m)^ *" + role(label) + "[ \\t]*:?[ \\t]*([^\\n\\r]+)$")
                    .findAll(source.text).mapNotNull { row ->
                        MoneyGrammar.withCurrency(row.groupValues[1].trim(), bareCurrency)?.let { Hit(it, row.range) }
                    }.filterNot { overlaps(it.range) }.toList()
                val suffix = Regex("(?m)^ *([^\\n\\r:]+?) *: *" + source.phrase(label).pattern + " *$")
                    .findAll(source.text).mapNotNull { row ->
                        MoneyGrammar.withCurrency(row.groupValues[1].trim(), bareCurrency)?.let { Hit(it, row.range) }
                    }.filterNot { overlaps(it.range) }.toList()
                prefix + suffix
            } else emptyList()
            Regex("(?m)^ *" + role(label)).findAll(source.text).forEach { mention ->
                val longerRole = labels(field).any { other -> SourceText(other).text.length > SourceText(label).text.length &&
                    Regex("(?m)^ *" + source.phrase(other).pattern).find(source.text, mention.range.first)?.range?.first == mention.range.first }
                if (!longerRole && (field != ExtractionField.AMOUNT || !overlaps(mention.range)) && matches.none { it.range.first == mention.range.first } && separate.none { it.range.first == mention.range.first } &&
                    !(field == ExtractionField.AVAILABLE_BALANCE && negativeBalances.any { it.range.first == mention.range.first }))
                    problem("MALFORMED_${field.name}", Blocker.VALIDATION_ERROR)
            }
            separate + matches.mapNotNull { match ->
                if (field == ExtractionField.AMOUNT && overlaps(match.range)) return@mapNotNull null
                val value = MoneyGrammar.read(match)
                if (value == null) { problem("INVALID_MONEY", Blocker.VALIDATION_ERROR); null }
                else Hit(value, match.range)
            }
        } + (if (allowStandalone && field == ExtractionField.AMOUNT) Regex("(?m)^ *" + MoneyGrammar.VALUE + " *$")
            .findAll(source.text).mapNotNull { match -> MoneyGrammar.read(match)?.let { Hit(it, match.range) } }.toList() else emptyList()) +
            titlePrefixes.flatMap { prefix ->
                Regex("(?m)^ *" + source.phrase(prefix).pattern + " +(?:ب *|بمبلغ *)?" + MoneyGrammar.VALUE)
                    .findAll(source.text).filter { source.text.substring(0, it.range.first).isBlank() }
                    .mapNotNull { match -> MoneyGrammar.read(match)?.let { Hit(it, match.range) } }.toList()
            }
        hits.forEach { evidence += source.evidence("extract.money", field.name, it.range) }
        if (field == ExtractionField.AMOUNT && currency != null && hits.any { it.value.currency != MoneyGrammar.withCurrency("1", currency)?.currency })
            problem("CONFLICTING_PRINCIPAL_CURRENCY", Blocker.CONFLICTING_FACTS)
        if (hits.map { it.value }.distinct().size > 1) { problem("CONFLICTING_${field.name}", Blocker.CONFLICTING_FACTS); return null }
        return hits.firstOrNull()?.value
    }

    fun identifier(field: ExtractionField): String? {
        val hits = labels(field).flatMap { label ->
            referencePattern(label).findAll(source.text).mapNotNull {
                val value = it.groupValues[1].trimEnd('*', 'x')
                value.takeLast(4).takeIf { suffix -> suffix.matches(Regex("[0-9]{4}")) }
                    ?.let { suffix -> Hit(suffix, it.groups[1]!!.range) }
            }.toList()
        }
        val identifiers = hits.filterNot { hit ->
            val span = source.evidence("extract.identifier", field.name, hit.range)
            evidence.any { it.field == "AMOUNT" && span.start < it.end && it.start < span.end }
        }
        identifiers.forEach { evidence += source.evidence("extract.identifier", field.name, it.range) }
        if (identifiers.map { it.value }.distinct().size > 1) { problem("CONFLICTING_${field.name}", Blocker.CONFLICTING_FACTS); return null }
        return identifiers.firstOrNull()?.value
    }

    private fun referencePattern(label: String) = Regex("(?m)^ *" + role(label) +
        "[ \\t]*:?[ \\t]*\\(?((?:[a-z]{2})?[*x0-9]{1,34})(?![\\p{L}\\p{N}_])")

    private fun madaCardMatches(): List<MatchResult> =
        if (labels(ExtractionField.CARD).any { SourceText(it).text == "مدي" }) {
            Regex("(?m)^ *(?:مدي-ابل|شراء +pos-مدي) *[*x]*([0-9]{4})[*x]*(?![\\p{L}\\p{N}_])")
                .findAll(source.text).toList()
        } else emptyList()

    fun hasCardReferenceRole(): Boolean = madaCardMatches().isNotEmpty() || labels(ExtractionField.CARD).any { label ->
        referencePattern(label).findAll(source.text).any { match ->
            val span = source.evidence("extract.identifier", "CARD", match.groups[1]!!.range)
            evidence.none { it.field == "AMOUNT" && span.start < it.end && it.start < span.end }
        }
    }

    fun account(field: ExtractionField, fallback: String?): String? {
        identifier(field)?.let { return it }
        if (fallback == null) return null
        val suffixes = labels(field).flatMap { label -> referencePattern(label).findAll(source.text).map { match ->
            match.groupValues[1].trimEnd('*', 'x').takeLastWhile(Char::isDigit)
        }.filter { it.isNotEmpty() }.toList() }
        if (suffixes.any { !fallback.endsWith(it) }) {
            problem("CONFIRMED_ACCOUNT_SUFFIX_CONFLICT", Blocker.CONFLICTING_FACTS)
            return null
        }
        return fallback
    }

    fun card(): String? {
        val labeledCard = identifier(ExtractionField.CARD)
        // Explicit Mada roles may be part of the purchase title or name a wallet token.
        // Retain the observed reference; this never aliases it to a physical card.
        val mada = madaCardMatches()
        val pattern = Regex("(?m)^ *(?:بطاقة|card) *: *(?:credit|debit|ائتمانية|مدى)(?: +| *\\n+ *)(?:رقم|number) *: *([*x]*[0-9]{4})(?![\\p{L}\\p{N}_])")
        val hits = pattern.findAll(source.text).toList()
        val values = (listOfNotNull(labeledCard) + hits.map { it.groupValues[1].takeLast(4) } + mada.map { it.groupValues[1] }).distinct()
        if (values.size > 1) problem("CONFLICTING_CARD", Blocker.CONFLICTING_FACTS)
        hits.forEach { evidence += source.evidence("extract.card.number-role", "CARD", it.groups[1]!!.range) }
        mada.forEach { evidence += source.evidence("extract.card.mada-role", "CARD", it.groups[1]!!.range) }
        return values.singleOrNull()
    }

    fun merchant(): String? {
        val merchants = labels(ExtractionField.MERCHANT).flatMap { label ->
            Regex("(?m)^ *" + source.phrase(label).pattern + "(?:[ \\t]*:[ \\t]*|[ \\t]+)([^\\n\\r]+)").findAll(source.text).map { match ->
                val range = match.groups[1]!!.range
                if (source.text.substring(range).trim().matches(Regex("[*x]*[0-9]{4}[*x]*"))) return@map null
                evidence += source.evidence("extract.merchant", "MERCHANT", range)
                source.original(range).trim()
            }.filterNotNull().toList()
        }.distinct()
        if (merchants.size > 1) problem("CONFLICTING_MERCHANT", Blocker.CONFLICTING_FACTS)
        return merchants.singleOrNull()
    }

    fun transactionTimes(ignoreUnbound: Boolean = false): List<LocalDateTime> {
        val times = DateGrammar.timestamp.findAll(source.text).mapNotNull { match ->
            val lineStart = source.text.lastIndexOf('\n', match.range.first).let { if (it < 0) 0 else it + 1 }
            val prefix = source.text.substring(lineStart, match.range.first).trim().removeSuffix(":").trim()
            if (prefix !in TIME_LABELS) {
                if (!ignoreUnbound) problem("UNBOUND_TIME_ROLE", Blocker.VALIDATION_ERROR)
                return@mapNotNull null
            }
            evidence += source.evidence("extract.time", "occurredAtLocal", match.range)
            DateGrammar.time(match.value).also { if (it == null) problem("INVALID_TIME", Blocker.VALIDATION_ERROR) }
        }.distinct().toList()
        if (times.size > 1) problem("CONFLICTING_TIME", Blocker.CONFLICTING_FACTS)
        return times
    }

    private companion object {
        private val TIME_LABELS = setOf("", "في", "on", "date", "time", "transaction time", "تاريخ العملية", "تاريخ", "التاريخ")
    }
}
