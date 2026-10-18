package com.baraa.masroof.lab.bank.interpretation

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Explicit source wall clocks only, never receipt time or guessed year/timezone. */
internal object DateGrammar {
    private val dates = listOf("uuuu-MM-dd", "dd/MM/uuuu", "dd-MM-uuuu").map {
        DateTimeFormatter.ofPattern(it).withResolverStyle(ResolverStyle.STRICT)
    }
    val timestamp = Regex("(?<![0-9])(?:[0-9]{4}-[0-9]{2}-[0-9]{2}[t ][0-9]{2}:[0-9]{2}(?::[0-9]{2})?|[0-9]{2}:[0-9]{2} [0-9]{2}[-/][0-9]{2}[-/][0-9]{4})(?![0-9:])")
    fun date(value: String): LocalDate? = dates.firstNotNullOfOrNull { runCatching { LocalDate.parse(value, it) }.getOrNull() }
    fun time(value: String): LocalDateTime? = runCatching {
        if (value[2] == ':') LocalDateTime.of(requireNotNull(date(value.substring(6))), java.time.LocalTime.parse(value.substring(0, 5)))
        else LocalDateTime.parse(value.replace(' ', 'T').replace('t', 'T'))
    }.getOrNull()
}
