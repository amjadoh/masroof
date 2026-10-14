package com.baraa.masroof.lab.engine.evidence

import com.baraa.masroof.lab.engine.*
import kotlinx.serialization.Serializable

@Serializable
data class ConceptRule(val id: String, val concept: String, val phrases: List<String>,
    val excludes: List<String> = emptyList(), val requiredRoles: Set<String> = emptySet(),
    val maxDistance: Int = 80, val nextLine: Boolean = false)

@Serializable
data class EvidenceKnowledge(val version: Int = 1, val rules: List<ConceptRule>) {
    fun validate() {
        require(version > 0 && rules.size in 1..200 && rules.map { it.id }.distinct().size == rules.size)
        rules.forEach { rule ->
            require(rule.id.matches(Regex("[A-Za-z0-9_.-]{1,80}")) && rule.concept in EvidenceDefaults.concepts)
            require(rule.maxDistance in 1..160 && rule.requiredRoles.all { it in EvidenceDefaults.roles })
            require(rule.phrases.size in 1..30 && rule.excludes.size <= 30)
            (rule.phrases + rule.excludes).forEach { require(it.isNotBlank() && it.length <= 100 && '\n' !in it && '\r' !in it &&
                com.baraa.masroof.lab.copy.core.text.ArabicTextFolding.foldForComparison(it).isNotBlank()) }
        }
    }
}

@Serializable
data class EvidenceToken(val kind: String, val value: String, val start: Int, val endExclusive: Int,
    val currency: String? = null)

@Serializable
data class EvidenceRelation(val role: String, val value: String, val start: Int, val endExclusive: Int,
    val ruleId: String, val currency: String? = null)

@Serializable
data class EventHypothesis(val family: String, val support: List<String>, val blockers: List<String>)

@Serializable
data class EvidenceAnalysis(val schema: Int = 1, val engineVersion: String = EvidenceLabEngine.VERSION,
    val outcome: LabOutcome, val tokens: List<EvidenceToken> = emptyList(),
    val relations: List<EvidenceRelation> = emptyList(), val hypotheses: List<EventHypothesis> = emptyList())

@Serializable
data class LabEvidenceSnapshot(val banks: List<BankProfile>, val library: SharedRuleLibrary,
    val knowledge: EvidenceKnowledge, val sharedVersion: String = SharedLabEngine.VERSION,
    val evidenceVersion: String = EvidenceLabEngine.VERSION, val referenceSchema: Int = 2)

/** Explicit absence is evaluated; legacy blank optional fields remain unreviewed. */
object ReferenceFields {
    fun values(f: LabFacts): Map<String, String?> = linkedMapOf("family" to f.family, "direction" to f.direction,
        "amount" to f.amount, "currency" to f.currency, "card" to f.card, "source" to f.source,
        "destination" to f.destination, "merchant" to f.merchant, "balance" to f.balance,
        "dueAmount" to f.dueAmount, "ibanSuffix" to f.ibanSuffix, "occurredAtLocal" to f.occurredAtLocal,
        "executionState" to f.executionState, "cardKind" to f.cardKind, "network" to f.network,
        "feeAmount" to f.feeAmount, "exchangeRate" to f.exchangeRate)
    val numeric = setOf("amount", "balance", "dueAmount", "feeAmount", "exchangeRate")
    fun validate(e: ExpectedResult) {
        val values = values(e.facts)
        require(e.absentFields.all { it in values && values[it] == null }) { "حقل غائب غير صالح أو يحمل قيمة" }
    }
    fun equal(field: String, expected: String?, actual: String?): Boolean = when {
        expected == null || actual == null -> expected == actual
        field in numeric -> runCatching { expected.toBigDecimal().compareTo(actual.toBigDecimal()) == 0 }.getOrDefault(false)
        field == "occurredAtLocal" -> runCatching { java.time.LocalDateTime.parse(expected) == java.time.LocalDateTime.parse(actual) }.getOrDefault(false)
        else -> expected == actual
    }
    fun differences(e: ExpectedResult, actual: LabOutcome): List<String> {
        validate(e)
        val expected = values(e.facts); val found = values(actual.facts)
        return buildList {
            if (e.status != actual.status) add("status")
            expected.forEach { (key, value) ->
                if ((value != null || key in e.absentFields) && !equal(key, value, found[key])) add(key)
            }
        }
    }
}
