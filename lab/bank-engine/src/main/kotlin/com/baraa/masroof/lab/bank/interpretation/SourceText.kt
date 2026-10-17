package com.baraa.masroof.lab.bank.interpretation

import com.baraa.masroof.lab.bank.Evidence
import java.text.Normalizer
import java.util.Locale

/** Comparison text with a lossless map to the caller's original UTF-16 offsets. */
internal class SourceText(private val original: String) {
    private val starts = mutableListOf<Int>()
    private val ends = mutableListOf<Int>()
    val text: String = buildString {
        var offset = 0
        while (offset < original.length) {
            val codePoint = original.codePointAt(offset)
            val end = offset + Character.charCount(codePoint)
            val source = String(Character.toChars(codePoint))
            Normalizer.normalize(source, Normalizer.Form.NFKD).lowercase(Locale.ROOT).forEach { c ->
                val mapped = when {
                    Character.getType(c) == Character.NON_SPACING_MARK.toInt() || c == '\u0640' -> null
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

    fun phrase(value: String): Regex = Regex("(?<![\\p{L}\\p{N}_])" +
        SourceText(value).text.split(Regex("[ \\t]+" )).joinToString("[ \\t]+") { Regex.escape(it) } + "(?![\\p{L}\\p{N}_])")

    fun evidence(rule: String, field: String, range: IntRange): Evidence =
        Evidence(rule, field, starts[range.first], ends[range.last])

    fun original(range: IntRange): String = original.substring(starts[range.first], ends[range.last])
}
