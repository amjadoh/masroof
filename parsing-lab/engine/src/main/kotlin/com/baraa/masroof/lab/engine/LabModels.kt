package com.baraa.masroof.lab.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.baraa.masroof.lab.copy.core.text.ArabicTextFolding

@Serializable
data class BankProfile(val id: String, val name: String, val senders: List<String>, val zoneId: String = "Asia/Riyadh")

/** Literal phrases only. No arbitrary regex, script, ownership or posting commands. */
@Serializable
data class RuleSpec(
    val id: String,
    val family: String,
    val phrases: List<String>,
    val excludes: List<String> = emptyList(),
    val amountLabels: List<String> = listOf("بمبلغ"),
    val cardLabels: List<String> = listOf("بطاقة"),
    val sourceLabels: List<String> = emptyList(),
    val destinationLabels: List<String> = emptyList(),
    val merchantLabels: List<String> = listOf("لدى"),
    val balanceLabels: List<String> = listOf("الرصيد المتاح"),
    val direction: String = "OUTGOING",
)

@Serializable
data class RulePack(val schema: Int = 1, val bank: BankProfile, val version: Int, val rules: List<RuleSpec>)

@Serializable
data class FieldEvidence(val field: String, val ruleId: String, val label: String, val value: String,
                         val start: Int, val endExclusive: Int)

@Serializable
data class LabFacts(
    val family: String? = null, val direction: String? = null, val amount: String? = null,
    val currency: String? = null, val card: String? = null, val source: String? = null,
    val destination: String? = null, val merchant: String? = null, val balance: String? = null,
)

@Serializable
data class LabOutcome(val status: String, val facts: LabFacts = LabFacts(), val trace: List<String> = emptyList(),
                      val evidence: List<FieldEvidence> = emptyList(), val normalizedText: String = "")

@Serializable
data class ExpectedResult(val status: String, val facts: LabFacts)

object LabJson {
    val format = Json { encodeDefaults = true; prettyPrint = true }
}

object LabFamilies {
    val financial = setOf("PURCHASE", "TRANSFER_IN", "TRANSFER_OUT", "CARD_PAYMENT", "BILL_PAYMENT",
        "FINANCING_INSTALLMENT", "WITHDRAWAL", "REFUND", "FEE")
    val editable = financial + setOf("NON_FINANCIAL", "BALANCE_NOTICE", "OTP")
    fun direction(family: String) = if (family in setOf("TRANSFER_IN", "REFUND")) "INCOMING" else "OUTGOING"
    fun tier(family: String) = when (family) {
        "OTP" -> 100
        "NON_FINANCIAL" -> 95
        "BALANCE_NOTICE" -> 90
        "CARD_PAYMENT", "BILL_PAYMENT", "FINANCING_INSTALLMENT", "REFUND" -> 80
        else -> 70
    }
}

object RulePackValidation {
    fun check(pack: RulePack) {
        require(pack.schema == 1) { "إصدار الملف غير مدعوم" }
        require(pack.version > 0 && pack.bank.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "معرف البنك أو الإصدار غير صالح" }
        require(pack.bank.name.isNotBlank() && pack.bank.name.length <= 80) { "اسم البنك مطلوب" }
        java.time.ZoneId.of(pack.bank.zoneId)
        require(pack.bank.senders.size in 1..20 && pack.bank.senders.all { it.isNotBlank() && it.length <= 64 }) { "أدخل مرسلًا صالحًا" }
        require(pack.bank.senders.map(::senderKey).distinct().size == pack.bank.senders.size) { "مرسل مكرر" }
        require(pack.rules.size <= 100 && pack.rules.map { it.id }.distinct().size == pack.rules.size) { "قواعد كثيرة أو معرفات مكررة" }
        pack.rules.forEach { r ->
            require(r.id.matches(Regex("[A-Za-z0-9_.-]{1,64}")) && r.family in LabFamilies.editable) { "نوع القاعدة أو معرفها غير مدعوم" }
            require(r.direction in setOf("INCOMING", "OUTGOING")) { "اتجاه غير صالح" }
            require(r.phrases.isNotEmpty()) { "عبارة التعرف مطلوبة" }
            listOf(r.phrases, r.excludes, r.amountLabels, r.cardLabels, r.sourceLabels, r.destinationLabels,
                r.merchantLabels, r.balanceLabels).forEach { terms ->
                require(terms.size <= 20 && terms.all {
                    it.length <= 100 && '\n' !in it && '\r' !in it &&
                        ArabicTextFolding.foldForComparison(it).isNotBlank()
                }) { "وسم فارغ أو طويل" }
            }
        }
    }
    fun senderKey(sender: String) = sender.trim().lowercase(java.util.Locale.ROOT)
}

object LabComparison {
    /** Exact required financial facts; optional labels are checked when explicitly annotated. */
    fun differences(expected: ExpectedResult, actual: LabOutcome): List<String> = buildList {
        if (expected.status != actual.status) add("status")
        val e = expected.facts; val a = actual.facts
        if (e.family != a.family) add("family")
        if (expected.status == "SUCCESS" || e.amount != null) {
            if (e.direction != a.direction) add("direction")
            if (!sameAmount(e.amount, a.amount)) add("amount")
            if (e.currency != a.currency) add("currency")
        }
        listOf("card" to (e.card to a.card), "source" to (e.source to a.source),
            "destination" to (e.destination to a.destination), "merchant" to (e.merchant to a.merchant)).forEach { (key, pair) ->
            if (pair.first != null && pair.first != pair.second) add(key)
        }
    }
    private fun sameAmount(a: String?, b: String?): Boolean = when {
        a == null || b == null -> a == b
        else -> runCatching { a.toBigDecimal().compareTo(b.toBigDecimal()) == 0 }.getOrDefault(false)
    }
}
