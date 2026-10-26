package com.baraa.masroof.bank.patterns

import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.parsing.model.ParseResult
import java.lang.reflect.Modifier
import java.security.MessageDigest

/** Field digests keep SMS values out of baseline files and failure messages. Test sources only. */
internal object ParseParity {
    fun fields(result: ParseResult): Map<String, String> = buildMap {
        put("outcome", result.javaClass.simpleName)
        when (result) {
            is ParseResult.Success -> { putAll(fields("event", result.event)); putAll(fields("details", result.details)) }
            is ParseResult.NonFinancial -> {
                putAll(fields("event", result.event)); putAll(fields("details", result.details))
                put("reason", digest(result.reason)); putAll(fields("confidence", result.confidence))
            }
            is ParseResult.Partial -> {
                putAll(fields("event", result.event)); putAll(fields("details", result.details))
                putAll(fields("draft", result.draft)); put("findings", digest(result.findings.map { it.code to it.severity }))
            }
            is ParseResult.ReviewRequired -> {
                putAll(fields("event", result.event)); putAll(fields("details", result.details))
                putAll(fields("draft", result.draft)); put("findings", digest(result.findings.map { it.code to it.severity }))
                put("reasons", digest(result.reasons))
            }
            is ParseResult.Invalid -> {
                putAll(fields("draft", result.draft)); put("findings", digest(result.findings.map { it.code to it.severity }))
            }
            is ParseResult.Unsupported -> put("reason", digest(result.reason))
        }
    }.toSortedMap()

    // Reflect every stored fact, including new properties added later; no hand-maintained subset.
    fun fields(prefix: String, value: Any?): Map<String, String> {
        if (value == null) return mapOf(prefix to "null")
        return value.javaClass.declaredFields.filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .sortedBy { it.name }.flatMap { field ->
                field.isAccessible = true
                val item = field.get(value)
                if (item is Confidence) fields("$prefix.${field.name}", item).toList()
                else {
                    val fact = if (value is Confidence && field.name == "reasons") value.reasons.filterNot {
                        it.startsWith("engine:") || it.startsWith("rules:")
                    } else item
                    listOf("$prefix.${field.name}" to digest(fact))
                }
            }.toMap()
    }

    fun differences(expected: Map<String, String>, actual: Map<String, String>): List<String> =
        (expected.keys + actual.keys).filter { expected[it] != actual[it] }.sorted()

    fun digest(value: Any?): String = MessageDigest.getInstance("SHA-256")
        .digest((value?.let { "${it.javaClass.name}:$it" } ?: "null").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
