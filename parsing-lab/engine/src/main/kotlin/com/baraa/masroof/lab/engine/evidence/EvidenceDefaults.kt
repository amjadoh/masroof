package com.baraa.masroof.lab.engine.evidence

import com.baraa.masroof.lab.engine.*

object EvidenceDefaults {
    val roles = setOf("amount", "balance", "dueAmount", "feeAmount", "otherAmount", "exchangeRate", "card", "source", "destination", "ibanSuffix", "merchant", "occurredAtLocal", "dueDate")
    val states = setOf("EXECUTED", "REJECTED", "PENDING", "REQUESTED")
    val concepts = roles.map { "field.$it" }.toSet() + LabFamilies.editable.map { "family.$it" } +
        states.map { "state.$it" } + setOf("card.CREDIT", "card.DEBIT", "network.VISA", "network.MADA", "network.MASTERCARD")
    fun create(): EvidenceKnowledge {
        val shared = SharedRuleDefaults.create()
        val rules = shared.rules.map { ConceptRule(it.id, "family.${it.family}", it.phrases, it.excludes) }.toMutableList()
        fun add(id: String, concept: String, vararg phrases: String) { rules += ConceptRule(id, concept, phrases.toList()) }
        add("event.deposit", "family.TRANSFER_IN", "أودعت", "تم إيداع", "قيدت لحساب", "credited to your account")
        add("event.settlement", "family.CARD_PAYMENT", "تسديد", "سداد مستحقات البطاقة")
        add("event.compact.purchase", "family.PURCHASE", "online credit card purchase")
        val base = shared.rules.first()
        val fields = mapOf("amount" to base.amountLabels + listOf("مبلغ الحوالة", "قيمة العملية"),
            "balance" to base.balanceLabels + listOf("الرصيد بعد العملية"),
            "dueAmount" to listOf("due amount", "outstanding balance", "المبلغ المستحق", "إجمالي المستحق"),
            "feeAmount" to listOf("fee amount", "رسوم الحوالة", "رسوم حوالة", "الرسوم", "مبلغ الرسوم", "fees"),
            "otherAmount" to listOf("tax amount", "minimum amount", "minimum payment", "مبلغ الضريبة", "الحد الأدنى للسداد"),
            "exchangeRate" to listOf("سعر الصرف", "exchange rate"),
            "card" to base.cardLabels + listOf("باستخدام بطاقة", "بطاقة الأول"),
            "source" to base.sourceLabels + listOf("خصمت من حساب", "من الحساب رقم"),
            "destination" to base.destinationLabels + listOf("لحساب", "في حساب", "لحسابكم", "إلى"),
            "ibanSuffix" to listOf("iban", "آيبان", "ايبان"),
            "merchant" to base.merchantLabels + listOf("التاجر"),
            "occurredAtLocal" to listOf("on", "في", "بتاريخ", "date", "التاريخ"),
            "dueDate" to listOf("تاريخ الاستحقاق", "due date"))
        fields.forEach { (role, phrases) -> rules += ConceptRule("field.$role", "field.$role", phrases.distinct(), nextLine = role in setOf("amount", "balance", "dueAmount")) }
        add("state.rejected", "state.REJECTED", "declined", "rejected", "unsuccessful", "مرفوضة", "رفض العملية", "لم تتم", "تعذر")
        add("state.pending", "state.PENDING", "pending", "قيد التنفيذ", "قيد المعالجة")
        add("state.requested", "state.REQUESTED", "payment request", "طلب دفع", "طلب سداد", "يرجى السداد")
        add("state.executed", "state.EXECUTED", "تم تنفيذ", "completed", "successful")
        add("card.credit", "card.CREDIT", "credit card", "بطاقة ائتمانية", "البطاقة الائتمانية")
        add("card.debit", "card.DEBIT", "debit card", "مدى", "mada")
        add("network.visa", "network.VISA", "visa", "فيزا")
        add("network.mada", "network.MADA", "mada", "مدى")
        add("network.mastercard", "network.MASTERCARD", "mastercard", "ماستركارد")
        return EvidenceKnowledge(rules = rules).also { it.validate() }
    }
}
