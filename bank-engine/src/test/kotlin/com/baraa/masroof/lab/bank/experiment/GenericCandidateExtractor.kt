package com.baraa.masroof.lab.bank.experiment

import com.baraa.masroof.lab.bank.interpretation.SourceText

/** Primitive lexical hints, never a message family, ownership, account role or financial approval. */
internal enum class CandidateKind { MONEY, DATE_TIME, IDENTIFIER, NUMBER, TEXT }
internal data class TextCandidate(val kind: CandidateKind, val text: ExtractedField)

/** Offline experiment: this API deliberately accepts only text, with no sender, label or template. */
internal object GenericCandidateExtractor {
    private val date = "(?:[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}|[0-9]{1,2}[-/][0-9]{1,2}[-/](?:[0-9]{4}|[0-9]{2}))"
    private val time = "[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?"
    private val timestamp = Regex("(?<![0-9])$date[t ]+$time(?![0-9:])")
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
            hits += CandidateKind.TEXT to start..end
            // ponytail: offer a whole fragment and a possible value after its first word; no entity-name claim.
            val space = value.indexOf(' ')
            if (space >= 0) {
                var tail = start + space + 1
                while (tail <= end && source.text[tail].isWhitespace()) tail++
                if (tail <= end && source.text.substring(tail, end + 1).any(Char::isLetter)) hits += CandidateKind.TEXT to tail..end
            }
        }
        Regex("[^\\n]+").findAll(source.text).forEach { line ->
            val colon = line.range.firstOrNull { source.text[it] == ':' && protected.none { span -> it in span } }
            var cursor = colon?.plus(1) ?: line.range.first
            protected.filter { overlaps(it, cursor..line.range.last) }.forEach { span ->
                if (cursor < span.first) text(cursor until span.first)
                cursor = maxOf(cursor, span.last + 1)
            }
            if (cursor <= line.range.last) text(cursor..line.range.last)
        }
        return hits.distinct().sortedWith(compareBy({ it.second.first }, { it.first.ordinal })).map { (kind, range) ->
            val evidence = source.evidence("generic-candidate", kind.name, range)
            TextCandidate(kind, ExtractedField(source.original(range), evidence.start, evidence.end))
        }
    }
}
