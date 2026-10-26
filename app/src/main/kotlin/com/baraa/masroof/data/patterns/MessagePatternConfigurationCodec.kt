package com.baraa.masroof.data.patterns

import com.baraa.masroof.lab.bank.patterns.*
import kotlinx.serialization.json.*

/** Literal text and closed primitive slots only. This document grants no financial authority. */
object MessagePatternConfigurationCodec {
    const val MAX_BYTES = 1_048_576
    private val json = Json

    fun encode(configuration: MessagePatternConfiguration): String = buildJsonObject {
        put("schema", 1); put("normalization", 1); put("generation", configuration.generation)
        putJsonArray("banks") { configuration.banks.sortedBy { it.bankId }.forEach { bank -> add(buildJsonObject {
            put("bankId", bank.bankId); put("displayName", bank.displayName?.let(::JsonPrimitive) ?: JsonNull)
            put("senders", JsonArray(bank.senders.map { JsonPrimitive(it) }))
            putJsonArray("patterns") { bank.patterns.sortedWith(compareBy({ it.id }, { it.revision })).forEach { pattern ->
                require(pattern.bankId == bank.bankId) { "Pattern bank mismatch" }
                add(buildJsonObject {
                    put("id", pattern.id); put("sender", pattern.sender); put("label", pattern.label)
                    put("revision", pattern.revision); put("enabled", pattern.enabled)
                    putJsonArray("parts") { pattern.parts.forEach { part -> add(buildJsonObject {
                        when (part) {
                            is StaticText -> put("literal", part.value)
                            is FieldSlot -> {
                                put("field", part.field); put("kind", part.kind.name)
                                put("width", part.width); put("ignored", part.ignored)
                            }
                        }
                    }) } }
                })
            } }
        }) } }
    }.let { json.encodeToString(JsonElement.serializer(), it) }.also { decode(it) }

    fun decode(raw: String): MessagePatternConfiguration {
        require(raw.length <= MAX_BYTES && raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Pattern configuration too large" }
        val root = json.parseToJsonElement(raw).jsonObject
        root.only("schema", "normalization", "generation", "banks")
        require(root.number("schema") == 1L && root.number("normalization") == 1L) { "Unsupported pattern configuration" }
        val generation = root.number("generation").also { require(it >= 0) }
        val rows = root.getValue("banks").jsonArray
        require(rows.size <= 64)
        require(rows.sumOf { it.jsonObject.getValue("patterns").jsonArray.size } <= 512)
        val banks = rows.map { value ->
            val row = value.jsonObject
            row.only("bankId", "displayName", "senders", "patterns")
            val bankId = row.text("bankId")
            require(bankId.matches(Regex("[A-Za-z0-9_-]{1,64}")) && !bankId.equals("UNKNOWN", ignoreCase = true))
            val name = row.getValue("displayName").takeUnless { it == JsonNull }?.let { row.text("displayName") }
            require(name == null || validLabel(name))
            val senders = row.getValue("senders").jsonArray.map { it.jsonPrimitive.also { p -> require(p.isString) }.content }
            require(senders.size in 1..16 && senders.distinct().size == senders.size && senders.all(::validLabel))
            val patterns = row.getValue("patterns").jsonArray
            require(patterns.size <= 128)
            val definitions = patterns.map { element ->
                val p = element.jsonObject
                p.only("id", "sender", "label", "revision", "enabled", "parts")
                require(validLabel(p.text("sender")) && validLabel(p.text("label")))
                val parts = p.getValue("parts").jsonArray.map { entry ->
                    val part = entry.jsonObject
                    if (part.keys == setOf("literal")) StaticText(part.text("literal")) else {
                        part.only("field", "kind", "width", "ignored")
                        FieldSlot(part.text("field"), FieldKind.valueOf(part.text("kind")), part.integer("width"), part.flag("ignored"))
                    }
                }
                SavedMessagePattern(p.text("id"), bankId, p.text("sender"), p.text("label"), p.integer("revision"),
                    p.flag("enabled"), parts).also { it.validate() }
            }
            require(definitions.map { it.id to it.revision }.distinct().size == definitions.size) { "Duplicate pattern revision" }
            BankMessagePatterns(bankId, senders, name, definitions)
        }
        require(banks.map { it.bankId }.distinct().size == banks.size) { "Duplicate bank" }
        return MessagePatternConfiguration(generation, banks)
    }

    private fun validLabel(value: String) = value.isNotBlank() && value.length <= 128 && value.none(Char::isISOControl)
    private fun JsonObject.only(vararg names: String) { require(keys == names.toSet()) { "Unsupported pattern configuration fields" } }
    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.also { require(it.isString) }.content
    private fun JsonObject.number(name: String) = getValue(name).jsonPrimitive.also { require(!it.isString) }.long
    private fun JsonObject.integer(name: String) = getValue(name).jsonPrimitive.also { require(!it.isString) }.int
    private fun JsonObject.flag(name: String) = getValue(name).jsonPrimitive.also { require(!it.isString) }.boolean
}
