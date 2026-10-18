package com.baraa.masroof.lab.bank.interpretation

/** Uses the interpreter's normalization and original UTF-16 map, never a second normalizer. */
object LearningTokenization {
    const val VERSION = "bank-source-tokens-v1"
    data class Token(val text: String, val start: Int, val end: Int)

    fun tokens(original: String): List<Token> {
        val source = SourceText(original)
        return Regex("[\\p{L}\\p{N}]+(?:[.,][\\p{N}]+)*").findAll(source.text).map {
            val span = source.evidence(VERSION, "token", it.range)
            Token(it.value, span.start, span.end)
        }.toList()
    }

    /** Half-open token indices overlapping the explicitly selected original span. */
    fun range(original: String, start: Int, end: Int): IntRange {
        val selected = tokens(original).withIndex().filter { it.value.start < end && it.value.end > start }
        require(selected.isNotEmpty()) { "Select a source token" }
        return selected.first().index until selected.last().index + 1
    }
}
