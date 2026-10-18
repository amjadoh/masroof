package com.baraa.masroof.lab.bank.interpretation

import com.baraa.masroof.lab.bank.Evidence

internal data class RoleLine(val normalized: String, val original: String, val range: IntRange)

/** Shared literal role grammar for details and bank identities, with original source ranges. */
internal class RoleLineReader(
    private val source: SourceText,
    private val bindings: Map<String, List<String>>,
    private val evidence: MutableList<Evidence>,
) {
    private val prefixes = bindings.values.flatten().distinct().map {
        SourceText(it).text.length to Regex("^ *" + source.phrase(it).pattern + "(?=[ \\t:]|$)")
    }

    fun read(field: String): List<RoleLine> = bindings[field].orEmpty().distinct().flatMap { label ->
        val pattern = Regex("(?m)^ *" + source.phrase(label).pattern + "(?<tail>[^\\n\\r]*)$")
        pattern.findAll(source.text).mapNotNull { match ->
            val longest = prefixes.filter { it.second.containsMatchIn(match.value) }.maxOfOrNull { it.first } ?: 0
            if (longest > SourceText(label).text.length) return@mapNotNull null
            evidence += source.evidence("extract.role", field, match.range)
            val tail = match.groups["tail"]!!
            var first = tail.value.indexOfFirst { !it.isWhitespace() }
            val last = tail.value.indexOfLast { !it.isWhitespace() }
            if (first < 0 || !(tail.value.first().isWhitespace() || tail.value.first() == ':')) RoleLine("", "", match.range)
            else {
                if (tail.value[first] == ':') first++
                while (first <= last && tail.value[first].isWhitespace()) first++
                if (first > last) RoleLine("", "", match.range)
                else {
                    val range = (tail.range.first + first)..(tail.range.first + last)
                    RoleLine(source.text.substring(range), source.original(range), match.range)
                }
            }
        }.toList()
    }
}
