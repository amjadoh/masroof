package com.baraa.masroof.lab.engine

import kotlinx.serialization.Serializable

/** One shared literal configuration and bank-independent execution. Profiles only route senders. */
@Serializable
data class SharedRuleLibrary(val version: Int = 1, val rules: List<RuleSpec>, val network: NetworkRuleConfig = NetworkRuleConfig()) {
    fun validate() {
        network.validate()
        RulePackValidation.check(RulePack(bank = BankProfile("validation", "validation", listOf("validation")), version = version, rules = rules))
    }
}

@Serializable
data class LabRunSnapshot(val bankPack: RulePack, val library: SharedRuleLibrary, val engineVersion: String)

object SharedRuleDefaults {
    fun create() = SharedRuleLibrary(version = 3, network = SharedNetworkDefaults.create(), rules = listOf(
        rule("purchase", "PURCHASE", listOf("شراء", "pos purchase", "online purchase", "internet purchase", "card purchase", "purchase at")),
        rule("transfer.in", "TRANSFER_IN", listOf("حوالة واردة", "تحويل وارد", "تم ايداع", "إيداع نقدي", "fund transfer credited", "incoming transfer", "salary credited", "cash deposit")),
        rule("transfer.out", "TRANSFER_OUT", listOf("حوالة صادرة", "حوالة مالية صادرة", "تحويل صادر", "تم تحويل مبلغ", "outgoing transfer", "fund transfer debited", "transfer sent")),
        rule("bill", "BILL_PAYMENT", listOf("سداد فاتورة", "سداد فواتير", "bill payment", "bill paid", "سداد مدفوعات حكومية")),
        rule("card.payment", "CARD_PAYMENT", listOf("سداد بطاقة", "سداد البطاقة", "تسديد بطاقة", "بطاقة ائتمانية: تسديد", "credit card payment", "card repayment")),
        rule("installment", "FINANCING_INSTALLMENT", listOf("سداد قسط", "خصم قسط", "قسط تمويل", "loan repayment", "installment payment")),
        rule("withdrawal", "WITHDRAWAL", listOf("سحب نقدي", "سحب من الصراف", "cash withdrawal", "atm withdrawal")),
        rule("refund", "REFUND", listOf("استرداد", "استرجاع مبلغ", "purchase refund", "refund", "purchase reversal")),
        rule("fee", "FEE", listOf("خصم رسوم", "رسوم خدمة", "service fee", "fee charged")),
        rule("balance", "BALANCE_NOTICE", listOf("إشعار رصيد", "balance notification", "balance enquiry")),
        rule("notice", "NON_FINANCIAL", listOf("كشف حساب", "تم تسجيل الدخول", "تم إضافة مستفيد", "تم تفعيل البطاقة", "statement issued", "login notification", "beneficiary added")),
    ))

    private fun rule(id: String, family: String, phrases: List<String>) = RuleSpec(
        id = "shared.$id", family = family, phrases = phrases,
        amountLabels = listOf("بمبلغ", "مبلغ", "مبلغ العملية", "المبلغ", "بقيمة", "amount", "of") + if (family == "FINANCING_INSTALLMENT") listOf("القسط") else emptyList(),
        cardLabels = listOf("بطاقة", "البطاقة", "بطاقة مدى", "بطاقة ائتمانية", "credit card", "debit card", "card") + if (family == "REFUND") listOf("رقم") else emptyList(),
        sourceLabels = when (family) {
            "TRANSFER_IN" -> listOf("رقم حساب المرسل", "from account", "from")
            "BALANCE_NOTICE" -> listOf("حساب", "account")
            else -> listOf("من حساب", "من الحساب", "من", "from account", "from")
        },
        destinationLabels = listOf("إلى حساب", "إلى الحساب", "إلى", "الايبان", "to account", "to"),
        merchantLabels = listOf("لدى", "عند", "at", "merchant") + if (family == "REFUND") listOf("من") else emptyList(),
        balanceLabels = listOf("الرصيد المتاح", "الرصيد", "available balance", "available balance is", "balance"),
        direction = LabFamilies.direction(family),
        detailLabels = mapOf(
            "dueAmount" to listOf("due amount", "outstanding balance", "إجمالي المستحق", "إجمالي المبلغ المستحق", "المبلغ المتبقي"),
            "foreignAmount" to listOf("مبلغ", "foreign amount", "المبلغ الأجنبي"),
            "counterparty" to when (family) { "TRANSFER_IN" -> listOf("اسم المرسل"); "TRANSFER_OUT" -> listOf("الى"); "FINANCING_INSTALLMENT" -> listOf("لـ"); else -> emptyList() },
            "transactionReference" to listOf("رقم المعاملة", "transaction reference"),
            "biller" to listOf("المفوتر", "biller"), "billerCode" to listOf("رمز المفوتر", "biller code"),
            "paymentDueDate" to listOf("تاريخ الاستحقاق", "payment due date", "statement due date"),
        ),
        observations = mapOf(
            "purchase.pos" to listOf("نقاط البيع", "pos purchase"),
            "purchase.online" to listOf("عبر الانترنت", "online purchase", "internet purchase"),
            "card.credit" to listOf("بطاقة ائتمانية", "credit card", "المبلغ المستحق", "due amount"), "card.debit" to listOf("بطاقة مدى", "mada", "debit card", "خصمت من حساب"),
            "card.statement" to listOf("كشف حساب", "statement issued"),
            "card.available_balance" to listOf("الرصيد المتاح", "available balance"),
            "network.intra" to listOf("داخلية", "الى حسابك الجاري"), "network.inter" to listOf("محلية", "sarie"),
            "income.salary" to listOf("راتب", "salary"), "loan.personal" to listOf("تمويل شخصي"),
            "loan.auto" to listOf("تمويل سيارات", "تمويل سيارة"), "loan.mortgage" to listOf("تمويل عقاري"),
        ),
    )
}
