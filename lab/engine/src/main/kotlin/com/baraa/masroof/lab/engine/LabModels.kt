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
    val detailLabels: Map<String, List<String>> = emptyMap(),
    val observations: Map<String, List<String>> = emptyMap(),
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
    val dueAmount: String? = null, val ibanSuffix: String? = null, val occurredAtLocal: String? = null,
    val executionState: String? = null, val cardKind: String? = null, val network: String? = null,
    val feeAmount: String? = null, val exchangeRate: String? = null,
    val senderName: String? = null, val recipientName: String? = null,
    val senderBank: String? = null, val accountIdentifier: String? = null,
    val balanceCurrency: String? = null, val dueCurrency: String? = null, val feeCurrency: String? = null,
    val purchaseChannel: String? = null, val cardChannel: String? = null, val bankNetwork: String? = null,
    val transactionReference: String? = null, val counterparty: String? = null,
    val biller: String? = null, val billerCode: String? = null, val paymentDueDate: String? = null,
    val loanType: String? = null, val debitSourceAccountLast4: String? = null,
    val salaryIncomeWording: Boolean? = null, val foreignAmount: String? = null, val foreignCurrency: String? = null,
)

@Serializable
data class LabOutcome(val status: String, val facts: LabFacts = LabFacts(), val trace: List<String> = emptyList(),
                      val evidence: List<FieldEvidence> = emptyList(), val normalizedText: String = "",
                      val confidenceScore: Double? = null, val confidenceReasons: List<String> = emptyList())

@Serializable
data class ExpectedResult(val status: String, val facts: LabFacts, val absentFields: Set<String> = emptySet())

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
            require(r.detailLabels.keys.all { it in SharedVocabulary.detailFields })
            require(r.observations.keys.all { it in SharedVocabulary.observationFields })
            listOf(r.phrases, r.excludes, r.amountLabels, r.cardLabels, r.sourceLabels, r.destinationLabels,
                r.merchantLabels, r.balanceLabels).plus(r.detailLabels.values).plus(r.observations.values).forEach { terms ->
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
        com.baraa.masroof.lab.engine.evidence.ReferenceFields.validate(expected)
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
        if (e.balance != null && !sameAmount(e.balance, a.balance)) add("balance")
        if (e.dueAmount != null && !sameAmount(e.dueAmount, a.dueAmount)) add("dueAmount")
        if (e.ibanSuffix != null && e.ibanSuffix != a.ibanSuffix) add("ibanSuffix")
        if (e.occurredAtLocal != null && !com.baraa.masroof.lab.engine.evidence.ReferenceFields.equal("occurredAtLocal", e.occurredAtLocal, a.occurredAtLocal)) add("occurredAtLocal")
        val extra = com.baraa.masroof.lab.engine.evidence.ReferenceFields.values(e)
        val found = com.baraa.masroof.lab.engine.evidence.ReferenceFields.values(a)
        setOf("executionState", "cardKind", "network", "feeAmount", "exchangeRate", "senderName", "recipientName", "senderBank", "accountIdentifier").forEach { field ->
            if (extra[field] != null && !com.baraa.masroof.lab.engine.evidence.ReferenceFields.equal(field, extra[field], found[field])) add(field)
        }
        expected.absentFields.forEach { if (found[it] != null && it !in this) add(it) }
    }
    private fun sameAmount(a: String?, b: String?): Boolean = when {
        a == null || b == null -> a == b
        else -> runCatching { a.toBigDecimal().compareTo(b.toBigDecimal()) == 0 }.getOrDefault(false)
    }
}
