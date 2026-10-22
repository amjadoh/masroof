package com.baraa.masroof.lab.bank.interpretation

import com.baraa.masroof.lab.bank.Evidence
import java.text.Normalizer
import java.util.Locale

/** Comparison text with a lossless map to the caller's original UTF-16 offsets. */
internal class SourceText(private val original: String) {
    private val starts = mutableListOf<Int>()
    private val ends = mutableListOf<Int>()
    var text: String = buildString {
        var offset = 0
        while (offset < original.length) {
            val codePoint = original.codePointAt(offset)
            val end = offset + Character.charCount(codePoint)
            val source = String(Character.toChars(codePoint))
            Normalizer.normalize(source, Normalizer.Form.NFKD).lowercase(Locale.ROOT).forEach { c ->
                val mapped = when {
                    Character.getType(c) == Character.NON_SPACING_MARK.toInt() || Character.getType(c) == Character.FORMAT.toInt() || c == '\u0640' -> null
                    c in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    c in '\u06f0'..'\u06f9' -> '0' + (c - '\u06f0')
                    c == '\u066b' -> '.'
                    c == '\u066c' -> ','
                    c == '\u0649' -> '\u064a'
                    c == '\r' -> '\n'
                    c == '\u00a0' || c == '\t' -> ' '
                    else -> c
                }
                if (mapped != null) { append(mapped); starts += offset; ends += end }
            }
            offset = end
        }
    }
        private set

    /** Segment explicit English purchase clauses without changing any original evidence offset. */
    fun segmentInlinePurchase(): SourceText {
        if (!Regex("^ *(?:online|internet) purchase(?: apple pay)? +(?:credit card|debit card|card) *:?").containsMatchIn(text)) return this
        val terms = listOf("available balance is", "available balance", "credit card", "debit card", "due amount", "of", "at", "on")
        val pattern = Regex(terms.joinToString("|") { phrase(it).pattern })
        val boundaries = pattern.findAll(text).map { it.range.first }.filter { it > 0 && text[it - 1] != '\n' }.toSet()
        val oldText = text; val oldStarts = starts.toList(); val oldEnds = ends.toList()
        starts.clear(); ends.clear()
        text = buildString { oldText.forEachIndexed { index, char ->
            if (index in boundaries) { append('\n'); starts += oldStarts[index]; ends += oldStarts[index] }
            append(char); starts += oldStarts[index]; ends += oldEnds[index]
        } }
        return this
    }

    fun phrase(value: String): Regex = Regex("(?<![\\p{L}\\p{N}_])" +
        SourceText(value).text.split(Regex("[ \\t]+" )).joinToString("[ \\t]+") { Regex.escape(it) } + "(?![\\p{L}\\p{N}_])")

    fun evidence(rule: String, field: String, range: IntRange): Evidence =
        Evidence(rule, field, starts[range.first], ends[range.last])

    fun original(range: IntRange): String = original.substring(starts[range.first], ends[range.last])
}
