package com.baraa.masroof.data.patterns

import com.baraa.masroof.engine.bank.*
import kotlinx.serialization.json.*

/** Closed literal profile format. Never accepts executable operators or silently drops capabilities. */
object BankRuleProfileCodec {
    private const val MAX_BYTES = 262_144
    private val json = Json { prettyPrint = true }

    fun encode(profile: BankRuleSchema): String {
        profile.validate()
        val document = buildJsonObject {
            put("schema", profile.schema); put("revision", profile.revision)
            put("rules", JsonArray(profile.rules.map { rule -> buildJsonObject {
                put("id", rule.id); put("family", rule.family.name); put("direction", rule.direction.name)
                put("operator", rule.operator.name); put("phrases", terms(rule.phrases)); put("excludes", terms(rule.excludes))
                put("labels", termMap(rule.labels.mapKeys { it.key.name }))
                put("detailLabels", termMap(rule.detailLabels)); put("observations", termMap(rule.observations))
            } }))
            put("network", profile.network.let { network -> buildJsonObject {
                put("bankAliases", termMap(network.bankAliases))
                put("sourceBankLabels", terms(network.sourceBankLabels)); put("destinationBankLabels", terms(network.destinationBankLabels))
                put("standaloneDestinationBrackets", network.standaloneDestinationBrackets)
                put("networkLabels", terms(network.networkLabels)); put("standaloneSourceBrackets", network.standaloneSourceBrackets)
                put("unidentifiedExternalBankMarkers", terms(network.unidentifiedExternalBankMarkers))
            } })
        }
        return json.encodeToString(JsonElement.serializer(), document).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Rule profile too large" }
        }
    }

    fun decode(raw: String): BankRuleSchema {
        require(raw.length <= MAX_BYTES && raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Rule profile too large" }
        val root = json.parseToJsonElement(raw).jsonObject
        root.only("schema", "revision", "rules", "network")
        val rows = root.getValue("rules").jsonArray
        require(rows.size in 1..100)
        val rules = rows.map { element ->
            val row = element.jsonObject
            row.only("id", "family", "direction", "operator", "phrases", "excludes", "labels", "detailLabels", "observations")
            BankRule(row.text("id"), MessageFamily.valueOf(row.text("family")), readTerms(row.getValue("phrases")),
                excludes = readTerms(row.getValue("excludes")), direction = MoneyDirection.valueOf(row.text("direction")),
                labels = readMap(row.getValue("labels")).mapKeys { ExtractionField.valueOf(it.key) },
                operator = RuleOperator.valueOf(row.text("operator")), detailLabels = readMap(row.getValue("detailLabels")),
                observations = readMap(row.getValue("observations")))
        }
        val network = root.getValue("network").jsonObject
        network.only("bankAliases", "sourceBankLabels", "destinationBankLabels", "standaloneDestinationBrackets",
            "networkLabels", "standaloneSourceBrackets", "unidentifiedExternalBankMarkers")
        return BankRuleSchema(root.integer("schema"), root.integer("revision"), rules, NetworkRules(
            bankAliases = readMap(network.getValue("bankAliases")), sourceBankLabels = readTerms(network.getValue("sourceBankLabels")),
            destinationBankLabels = readTerms(network.getValue("destinationBankLabels")),
            standaloneDestinationBrackets = network.flag("standaloneDestinationBrackets"), networkLabels = readTerms(network.getValue("networkLabels")),
            standaloneSourceBrackets = network.flag("standaloneSourceBrackets"),
            unidentifiedExternalBankMarkers = readTerms(network.getValue("unidentifiedExternalBankMarkers")),
        )).also { it.validate() }
    }

    private fun terms(values: List<String>) = JsonArray(values.map { JsonPrimitive(it) })
    private fun termMap(values: Map<String, List<String>>) = JsonObject(values.mapValues { terms(it.value) })
    private fun readTerms(value: JsonElement) = value.jsonArray.map { item ->
        item.jsonPrimitive.also { require(it.isString) }.content
    }
    private fun readMap(value: JsonElement) = value.jsonObject.mapValues { readTerms(it.value) }
    private fun JsonObject.only(vararg names: String) { require(keys == names.toSet()) { "Unsupported rule profile fields" } }
    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.also { require(it.isString) }.content
    private fun JsonObject.integer(name: String) = getValue(name).jsonPrimitive.also { require(!it.isString) }.int
    private fun JsonObject.flag(name: String) = getValue(name).jsonPrimitive.also { require(!it.isString) }.boolean
}
