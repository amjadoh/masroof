package com.baraa.masroof.lab.beta.application.interpretation

import com.baraa.masroof.lab.bank.Evidence
import com.baraa.masroof.lab.engine.FieldEvidence
import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

/** Tracks each normalized character back to its original UTF-16 range. No substring guessing. */
internal class OriginalEvidence(private val original: String) {
    private data class Token(val char: Char, val start: Int, val end: Int)
    private val tokens: List<Token> = buildList {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(original) }
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            Normalizer.normalize(original.substring(start, end), Normalizer.Form.NFC).forEach { c ->
                val mapped = when (c) {
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    in '\u06f0'..'\u06f9' -> '0' + (c - '\u06f0')
                    '\u066c' -> ','
                    '\u066b' -> '.'
                    '\uff1a' -> ':'
                    else -> c
                }
                add(Token(mapped, start, end))
            }
            start = end
            end = iterator.next()
        }
    }.let { source ->
        val endings = mutableListOf<Token>()
        var i = 0
        while (i < source.size) {
            val token = source[i]
            if (token.char == '\r') {
                val paired = source.getOrNull(i + 1)?.takeIf { it.char == '\n' }
                endings += Token('\n', token.start, paired?.end ?: token.end)
                i += if (paired == null) 1 else 2
            } else { endings += token; i++ }
        }
        val result = mutableListOf<Token>()
        val line = mutableListOf<Token>()
        fun flush() {
            val trimmed = line.dropWhile { it.char.isWhitespace() }.dropLastWhile { it.char.isWhitespace() }
            trimmed.forEach { token ->
                if (token.char in listOf(' ', '\t', '\u00a0')) {
                    if (result.lastOrNull()?.char != ' ') result += token.copy(char = ' ')
                } else result += token
            }
            line.clear()
        }
        endings.forEach { token ->
            if (token.char == '\n') { flush(); result += token } else line += token
        }
        flush()
        result
    }
    val normalized: String = tokens.joinToString("") { it.char.toString() }

    fun map(field: FieldEvidence): Evidence {
        require(field.start >= 0 && field.endExclusive > field.start && field.endExclusive <= tokens.size)
        return Evidence(field.ruleId, field.field, tokens[field.start].start, tokens[field.endExclusive - 1].end)
    }
}
