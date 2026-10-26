package com.baraa.masroof.lab.bank.experiment

import com.baraa.masroof.lab.bank.interpretation.SourceText
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Offline experiment only: human-selected spans produce raw candidates, never financial facts. */
internal enum class FieldKind(val dateFormat: String? = null) {
    MONEY, TEXT, DIGITS,
    DATE_DMY_SHORT("dd/MM/uu HH:mm"), DATE_DMY_LONG("dd-MM-uuuu HH:mm"), DATE_YMD("uuuu-MM-dd HH:mm");

    fun pattern(example: String): String = when (this) {
        MONEY -> "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})? *(?:sar|sr|usd|eur|gbp)"
        TEXT -> "[^\\n]{1,160}"
        DIGITS -> "[0-9]{${example.length}}"
        DATE_DMY_SHORT -> "[0-9]{2}/[0-9]{2}/[0-9]{2} [0-9]{2}:[0-9]{2}"
        DATE_DMY_LONG -> "[0-9]{2}-[0-9]{2}-[0-9]{4} [0-9]{2}:[0-9]{2}"
        DATE_YMD -> "[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}"
    }

    fun accepts(value: String): Boolean = when {
        this == MONEY -> runCatching {
            value.takeWhile { it.isDigit() || it == '.' || it == ',' }.replace(",", "").toBigDecimal().signum() > 0
        }.getOrDefault(false)
        dateFormat != null -> runCatching {
            // Explicit preview convention: reduced years use 2000–2099. Never produces a transaction time/zone.
            LocalDateTime.parse(value, DateTimeFormatter.ofPattern(dateFormat).withResolverStyle(ResolverStyle.STRICT))
        }.isSuccess
        else -> value.isNotBlank()
    }
}
internal data class Selection(val field: String, val kind: FieldKind, val start: Int, val end: Int)
internal data class ExtractedField(val raw: String, val start: Int, val end: Int)
internal data class Preview(val label: String, val fields: Map<String, ExtractedField>) {
    val financialPostingAllowed: Boolean get() = false
}

internal class ExampleTemplate private constructor(
    private val sender: String,
    private val label: String,
    private val selections: List<Selection>,
    private val pattern: Regex,
) {
    fun preview(sender: String, body: String): Preview? {
        if (sender != this.sender || body.length !in 1..4096) return null
        val source = SourceText(body)
        val match = pattern.matchEntire(source.text) ?: return null
        val fields = selections.mapIndexed { index, selection ->
            val group = requireNotNull(match.groups[index + 1])
            if (!selection.kind.accepts(group.value)) return null
            val evidence = source.evidence("example-preview", selection.field, group.range)
            selection.field to ExtractedField(source.original(group.range), evidence.start, evidence.end)
        }.toMap()
        return Preview(label, fields)
    }

    companion object {
        fun learn(sender: String, label: String, body: String, selections: List<Selection>): ExampleTemplate {
            require(sender.isNotBlank() && label.isNotBlank() && body.length in 1..4096)
            require(selections.size in 1..16 && selections.map { it.field }.distinct().size == selections.size)
            val ordered = selections.sortedBy { it.start }.toList()
            val first = body.indexOfFirst { !it.isWhitespace() }
            val titleEnd = body.indexOf('\n', first).let { if (it < 0) body.length else it }
            require(ordered.first().start > titleEnd) { "The experiment keeps the first title line fixed" }
            val source = SourceText(body)
            var cursor = 0
            val expression = buildString {
                append("\\A[ \\n]*")
                ordered.forEach { selection ->
                    require(selection.field.matches(Regex("[a-z][a-z0-9_]{0,47}")))
                    require(selection.start in 0 until selection.end && selection.end <= body.length)
                    require(listOf(selection.start, selection.end).none { offset ->
                        offset in 1 until body.length && body[offset].isLowSurrogate() && body[offset - 1].isHighSurrogate()
                    }) { "Selection splits a Unicode character" }
                    val start = SourceText(body.substring(0, selection.start)).text.length
                    val end = SourceText(body.substring(0, selection.end)).text.length
                    require(start >= cursor && end > start) { "Selections overlap or normalize to empty" }
                    val value = source.text.substring(start, end)
                    require('\n' !in value && value.length <= 160)
                    val fieldPattern = selection.kind.pattern(value)
                    require(Regex(fieldPattern).matches(value) && selection.kind.accepts(value)) { "Invalid teaching field" }
                    append(literal(source.text.substring(cursor, start)))
                    append('(').append(fieldPattern).append(')')
                    cursor = end
                }
                append(literal(source.text.substring(cursor)))
                append("[ \\n]*\\z")
            }
            return ExampleTemplate(sender, label, ordered, Regex(expression))
        }

        fun previewUnique(templates: List<ExampleTemplate>, sender: String, body: String): Preview? =
            templates.mapNotNull { it.preview(sender, body) }.singleOrNull()

        private fun literal(value: String): String = buildString {
            var index = 0
            while (index < value.length) {
                when (value[index]) {
                    ':' -> {
                        append(": *")
                        index++
                        while (index < value.length && value[index] == ' ') index++
                    }
                    ' ', '\n' -> {
                        val char = value[index]
                        append(if (char == ' ') " +" else "\\n+")
                        while (index < value.length && value[index] == char) index++
                    }
                    else -> {
                        val start = index++
                        while (index < value.length && value[index] !in " :\n") index++
                        append(Regex.escape(value.substring(start, index)))
                    }
                }
            }
        }
    }
}
