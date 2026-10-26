package com.baraa.masroof.data.patterns

import com.baraa.masroof.engine.bank.patterns.*
import com.baraa.masroof.engine.bank.MessageFamily
import com.baraa.masroof.engine.bank.MoneyDirection
import com.baraa.masroof.finance.value.BankId
import com.baraa.masroof.finance.value.Currency
import kotlinx.serialization.json.*

/** Declarative definitions only. Fact contracts still require the production validation and write gates. */
object MessagePatternConfigurationCodec {
    const val MAX_BYTES = 1_048_576
    private val json = Json

    fun encode(configuration: MessagePatternConfiguration): String = buildJsonObject {
        val schema = if (configuration.banks.any { bank -> bank.patterns.any { it.factContract != null } }) 2 else 1
        put("schema", schema); put("normalization", 1); put("generation", configuration.generation)
        putJsonArray("banks") { configuration.banks.sortedBy { it.bankId }.forEach { bank -> add(buildJsonObject {
            put("bankId", bank.bankId); put("displayName", bank.displayName?.let(::JsonPrimitive) ?: JsonNull)
            put("senders", JsonArray(bank.senders.map { JsonPrimitive(it) }))
            putJsonArray("patterns") { bank.patterns.sortedWith(compareBy({ it.id }, { it.revision })).forEach { pattern ->
                require(pattern.bankId == bank.bankId) { "Pattern bank mismatch" }
                add(buildJsonObject {
                    put("id", pattern.id); put("sender", pattern.sender); put("label", pattern.label)
                    put("revision", pattern.revision); put("enabled", pattern.enabled)
                    if (schema == 2) put("factContract", pattern.factContract?.let { contract -> buildJsonObject {
                        put("version", contract.version); put("family", contract.family.name); put("direction", contract.direction.name)
                        put("currency", contract.currency?.name?.let(::JsonPrimitive) ?: JsonNull)
                        put("sourceAccountBank", contract.sourceAccountBank?.value?.let(::JsonPrimitive) ?: JsonNull)
                        put("destinationAccountBank", contract.destinationAccountBank?.value?.let(::JsonPrimitive) ?: JsonNull)
                        put("cardBank", contract.cardBank?.value?.let(::JsonPrimitive) ?: JsonNull)
                        if (contract.version >= 2) {
                            putJsonArray("accountBindings") { contract.accountBindings.sortedBy { it.role.name }.forEach { binding -> add(buildJsonObject {
                                put("role", binding.role.name); put("bankId", binding.bank.value); put("last4", binding.last4)
                                put("field", binding.field?.let(::JsonPrimitive) ?: JsonNull)
                                put("observedValue", binding.observedValue?.let(::JsonPrimitive) ?: JsonNull)
                            }) } }
                            put("contextReferences", JsonArray(contract.contextReferences.sorted().map(::JsonPrimitive)))
                        }
                    } } ?: JsonNull)
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
        val schema = root.number("schema")
        require(schema in 1L..2L && root.number("normalization") == 1L) { "Unsupported pattern configuration" }
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
                p.only(*(listOf("id", "sender", "label", "revision", "enabled", "parts") +
                    if (schema == 2L) listOf("factContract") else emptyList()).toTypedArray())
                require(validLabel(p.text("sender")) && validLabel(p.text("label")))
                val parts = p.getValue("parts").jsonArray.map { entry ->
                    val part = entry.jsonObject
                    if (part.keys == setOf("literal")) StaticText(part.text("literal")) else {
                        part.only("field", "kind", "width", "ignored")
                        FieldSlot(part.text("field"), FieldKind.valueOf(part.text("kind")), part.integer("width"), part.flag("ignored"))
                    }
                }
                SavedMessagePattern(p.text("id"), bankId, p.text("sender"), p.text("label"), p.integer("revision"),
                    p.flag("enabled"), parts, if (schema == 2L) p.getValue("factContract").takeUnless { it == JsonNull }?.let {
                        val c = it.jsonObject
                        val version = c.integer("version")
                        require(version in 1..2) { "Unsupported pattern fact contract" }
                        c.only(*(listOf("version", "family", "direction", "currency", "sourceAccountBank", "destinationAccountBank", "cardBank") +
                            if (version == 2) listOf("accountBindings", "contextReferences") else emptyList()).toTypedArray())
                        fun bank(key: String) = c.optionalText(key)?.also {
                            require(it.matches(Regex("[A-Za-z0-9_-]{1,64}")) && !it.equals("UNKNOWN", ignoreCase = true))
                        }?.let(::BankId)
                        PatternFactContract(MessageFamily.valueOf(c.text("family")), MoneyDirection.valueOf(c.text("direction")),
                            c.optionalText("currency")?.let(Currency::valueOf), bank("sourceAccountBank"),
                            bank("destinationAccountBank"), bank("cardBank"), version,
                            if (version == 2) c.getValue("accountBindings").jsonArray.also { require(it.size <= 2) }.map { binding ->
                                val b = binding.jsonObject
                                b.only("role", "bankId", "last4", "field", "observedValue")
                                PatternAccountBinding(PatternAccountRole.valueOf(b.text("role")), BankId(b.text("bankId")), b.text("last4"),
                                    b.optionalText("field"), b.optionalText("observedValue"))
                            } else emptyList(),
                            if (version == 2) c.getValue("contextReferences").jsonArray.also { require(it.size <= 5) }.map {
                                it.jsonPrimitive.also { p -> require(p.isString) }.content
                            }.also { require(it.distinct().size == it.size) }.toSet() else emptySet())
                    } else null).also { it.validate() }
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
    private fun JsonObject.optionalText(name: String) = getValue(name).takeUnless { it == JsonNull }?.let { text(name) }
    private fun JsonObject.number(name: String) = getValue(name).jsonPrimitive.also { require(!it.isString) }.long
    private fun JsonObject.integer(name: String) = getValue(name).jsonPrimitive.also { require(!it.isString) }.int
    private fun JsonObject.flag(name: String) = getValue(name).jsonPrimitive.also { require(!it.isString) }.boolean
}
