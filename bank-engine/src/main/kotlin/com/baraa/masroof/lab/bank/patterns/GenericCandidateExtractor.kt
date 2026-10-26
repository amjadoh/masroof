package com.baraa.masroof.lab.bank.patterns

import com.baraa.masroof.lab.bank.interpretation.SourceText

/** Primitive lexical hints, never a message family, ownership, account role or financial approval. */
enum class CandidateKind { MONEY, DATE_TIME, DATE, IDENTIFIER, NUMBER, TEXT }
/** Half-open UTF-16 offsets into the original message, with its original display spelling. */
data class TextSpan(val raw: String, val start: Int, val end: Int)

data class TextCandidate(val kind: CandidateKind, val text: TextSpan)

/** Text-only lexical hints. Never assigns a bank, field role, ownership or financial authority. */
object GenericCandidateExtractor {
    private val date = "(?:[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}|[0-9]{1,2}[-/][0-9]{1,2}[-/](?:[0-9]{4}|[0-9]{2}))"
    private val time = "[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?"
    private val timestamp = Regex("(?<![0-9])(?:$date[t ]+$time|$time +$date)(?![0-9:])")
    private val dateOnly = Regex("(?<![0-9])$date(?![0-9])")
    private val masked = Regex("(?<![\\p{L}\\p{N}_])(?:\\*+[a-z0-9]+|x+[0-9][a-z0-9]*)(?![\\p{L}\\p{N}_])")
    private val currency = "(?:sar|sr|usd|eur|gbp|ريال(?: سعودي)?|ر\\.س\\.?|[$€£])"
    private val magnitude = "[+\\-−]?[0-9]+(?:[.,][0-9]+)*"
    private val money = Regex("(?<![\\p{L}\\p{N}_.,+\\-−])(?:$currency *$magnitude|$magnitude *$currency)(?![\\p{L}\\p{N}_]|[.,][0-9])")
    private val mixedId = Regex("(?<![\\p{L}\\p{N}_])(?=[a-z0-9]*[a-z])(?=[a-z0-9]*[0-9])[a-z0-9]+(?![\\p{L}\\p{N}_])")
    private val number = Regex("(?<![\\p{L}\\p{N}_.,+\\-−])$magnitude(?![\\p{L}\\p{N}_]|[.,][0-9])")

    fun extract(body: String): List<TextCandidate> {
        if (body.length !in 1..4096) return emptyList()
        val source = SourceText(body)
        val hits = mutableListOf<Pair<CandidateKind, IntRange>>()
        fun overlaps(left: IntRange, right: IntRange) = left.first <= right.last && right.first <= left.last
        fun scan(kind: CandidateKind, pattern: Regex) {
            pattern.findAll(source.text).forEach { match ->
                if (match.value.length <= 160 && hits.none { overlaps(it.second, match.range) }) hits += kind to match.range
            }
        }
        // Protect whole tokens: a timestamp must not supply an amount, nor a masked reference lose its prefix.
        scan(CandidateKind.DATE_TIME, timestamp)
        scan(CandidateKind.DATE, dateOnly)
        scan(CandidateKind.IDENTIFIER, masked)
        scan(CandidateKind.MONEY, money)
        scan(CandidateKind.IDENTIFIER, mixedId)
        scan(CandidateKind.NUMBER, number)
        val protected = hits.map { it.second }.sortedBy { it.first }

        fun text(range: IntRange) {
            var start = range.first
            var end = range.last
            while (start <= end && source.text[start].isWhitespace()) start++
            while (end >= start && source.text[end].isWhitespace()) end--
            if (start > end || end - start + 1 > 160) return
            val value = source.text.substring(start, end + 1)
            if (value.none(Char::isLetter)) return
            val words = Regex("\\S+").findAll(value).map { it.range }.toList()
            // ponytail: at most four literal windows, optionally excluding an edge word; these are hints, not entity decisions.
            val starts = listOf(0, words.getOrNull(1)?.first).filterNotNull().distinct()
            val ends = listOf(value.lastIndex, words.getOrNull(words.lastIndex - 1)?.last).filterNotNull().distinct()
            starts.forEach { first -> ends.forEach { last ->
                if (first <= last && value.substring(first, last + 1).any(Char::isLetter))
                    hits += CandidateKind.TEXT to (start + first)..(start + last)
            } }
        }
        Regex("[^\\n]+").findAll(source.text).forEach { line ->
            val separators = line.range.filter { source.text[it] == ':' && protected.none { span -> it in span } }.map { it..it }
            val blocks = (protected.filter { overlaps(it, line.range) } + separators).sortedBy { it.first }
            // Every fragment survives. A late label must not discard text preceding its colon.
            var cursor = line.range.first
            blocks.forEach { span ->
                if (cursor < span.first) text(cursor until span.first)
                cursor = maxOf(cursor, span.last + 1)
            }
            if (cursor <= line.range.last) text(cursor..line.range.last)
        }
        return hits.distinct().sortedWith(compareBy({ it.second.first }, { it.first.ordinal })).map { (kind, range) ->
            val evidence = source.evidence("generic-candidate", kind.name, range)
            TextCandidate(kind, TextSpan(source.original(range), evidence.start, evidence.end))
        }
    }
}
