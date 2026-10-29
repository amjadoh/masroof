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
) {
    private data class Hit<T>(val value: T, val range: IntRange)
    private fun overlaps(range: IntRange) = secondaryRanges.any { range.first <= it.last && it.first <= range.last }
    // Compact bank roles can touch digits (بـ25 / مبلغ25), but never consume a longer word.
    private fun role(label: String) = source.phrase(label).pattern
        .removeSuffix("(?![\\p{L}\\p{N}_])") + "(?![\\p{L}_])"

    fun money(field: ExtractionField): Money? {
        val hits = labels(field).flatMap { label ->
            val pattern = Regex("(?m)^ *" + role(label) + "[ \\t]*:?[ \\t]*" + MoneyGrammar.VALUE)
            val reverse = Regex("(?m)^ *" + MoneyGrammar.VALUE + "[ \\t]*:[ \\t]*" + source.phrase(label).pattern + " *$")
            val matches = pattern.findAll(source.text).toList() + reverse.findAll(source.text).toList()
            val separate = if (field == ExtractionField.AMOUNT && currency != null) {
                Regex("(?m)^ *" + source.phrase(label).pattern + "[ \\t]*:?[ \\t]*([^\\n\\r]+)$")
                    .findAll(source.text).mapNotNull { row ->
                        MoneyGrammar.withCurrency(row.groupValues[1].trim(), currency)?.let { Hit(it, row.range) }
                    }.filterNot { overlaps(it.range) }.toList()
            } else emptyList()
            Regex("(?m)^ *" + role(label)).findAll(source.text).forEach { mention ->
                val longerRole = labels(field).any { other -> SourceText(other).text.length > SourceText(label).text.length &&
                    Regex("(?m)^ *" + source.phrase(other).pattern).find(source.text, mention.range.first)?.range?.first == mention.range.first }
                if (!longerRole && (field != ExtractionField.AMOUNT || !overlaps(mention.range)) && matches.none { it.range.first == mention.range.first } && separate.none { it.range.first == mention.range.first })
                    problem("MALFORMED_${field.name}", Blocker.VALIDATION_ERROR)
            }
            separate + matches.mapNotNull { match ->
                if (field == ExtractionField.AMOUNT && overlaps(match.range)) return@mapNotNull null
                val value = MoneyGrammar.read(match)
                if (value == null) { problem("INVALID_MONEY", Blocker.VALIDATION_ERROR); null }
                else Hit(value, match.range)
            }
        }
        hits.forEach { evidence += source.evidence("extract.money", field.name, it.range) }
        if (field == ExtractionField.AMOUNT && currency != null && hits.any { it.value.currency != MoneyGrammar.withCurrency("1", currency)?.currency })
            problem("CONFLICTING_PRINCIPAL_CURRENCY", Blocker.CONFLICTING_FACTS)
        if (hits.map { it.value }.distinct().size > 1) { problem("CONFLICTING_${field.name}", Blocker.CONFLICTING_FACTS); return null }
        return hits.firstOrNull()?.value
    }

    fun identifier(field: ExtractionField): String? {
        val hits = labels(field).flatMap { label ->
            Regex("(?m)^ *" + role(label) + "[ \\t]*:?[ \\t]*\\(?([*x]*[0-9]{4})(?![\\p{L}\\p{N}_])")
                .findAll(source.text).map { Hit(it.groupValues[1].takeLast(4), it.groups[1]!!.range) }.toList()
        }
        hits.forEach { evidence += source.evidence("extract.identifier", field.name, it.range) }
        if (hits.map { it.value }.distinct().size > 1) { problem("CONFLICTING_${field.name}", Blocker.CONFLICTING_FACTS); return null }
        return hits.firstOrNull()?.value
    }

    fun card(): String? {
        val labeledCard = identifier(ExtractionField.CARD)
        val pattern = Regex("(?m)^ *(?:بطاقة|card) *: *(?:credit|debit|ائتمانية|مدى)(?: +| *\\n+ *)(?:رقم|number) *: *([*x]*[0-9]{4})(?![\\p{L}\\p{N}_])")
        val hits = pattern.findAll(source.text).toList()
        val values = (listOfNotNull(labeledCard) + hits.map { it.groupValues[1].takeLast(4) }).distinct()
        if (values.size > 1) problem("CONFLICTING_CARD", Blocker.CONFLICTING_FACTS)
        hits.forEach { evidence += source.evidence("extract.card.number-role", "CARD", it.groups[1]!!.range) }
        return values.singleOrNull()
    }

    fun merchant(): String? {
        val merchants = labels(ExtractionField.MERCHANT).flatMap { label ->
            Regex("(?m)^ *" + source.phrase(label).pattern + "(?:[ \\t]*:[ \\t]*|[ \\t]+)([^\\n\\r]+)").findAll(source.text).map { match ->
                val range = match.groups[1]!!.range
                evidence += source.evidence("extract.merchant", "MERCHANT", range)
                source.original(range).trim()
            }.toList()
        }.distinct()
        if (merchants.size > 1) problem("CONFLICTING_MERCHANT", Blocker.CONFLICTING_FACTS)
        return merchants.singleOrNull()
    }

    fun transactionTimes(): List<LocalDateTime> {
        val times = DateGrammar.timestamp.findAll(source.text).mapNotNull { match ->
            val lineStart = source.text.lastIndexOf('\n', match.range.first).let { if (it < 0) 0 else it + 1 }
            val prefix = source.text.substring(lineStart, match.range.first).trim().removeSuffix(":").trim()
            if (prefix !in TIME_LABELS) {
                problem("UNBOUND_TIME_ROLE", Blocker.VALIDATION_ERROR); return@mapNotNull null
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
