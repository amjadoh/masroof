package com.baraa.masroof.lab.engine

import kotlinx.serialization.Serializable

/** One immutable, bank-independent library. Bank profiles only route senders. */
@Serializable
data class SharedRuleLibrary(val version: Int = 1, val rules: List<RuleSpec>) {
    fun validate() = RulePackValidation.check(RulePack(bank = BankProfile("validation", "validation", listOf("validation")), version = version, rules = rules))
}

@Serializable
data class LabRunSnapshot(val bankPack: RulePack, val library: SharedRuleLibrary, val engineVersion: String)

object SharedRuleDefaults {
    fun create() = SharedRuleLibrary(rules = listOf(
        rule("purchase", "PURCHASE", listOf("شراء", "pos purchase", "online purchase", "card purchase", "purchase at")),
        rule("transfer.in", "TRANSFER_IN", listOf("حوالة واردة", "تحويل وارد", "تم ايداع", "إيداع نقدي", "fund transfer credited", "incoming transfer", "salary credited", "cash deposit")),
        rule("transfer.out", "TRANSFER_OUT", listOf("حوالة صادرة", "تحويل صادر", "تم تحويل مبلغ", "outgoing transfer", "fund transfer debited", "transfer sent")),
        rule("bill", "BILL_PAYMENT", listOf("سداد فاتورة", "سداد فواتير", "bill payment", "bill paid", "سداد مدفوعات حكومية")),
        rule("card.payment", "CARD_PAYMENT", listOf("سداد بطاقة", "سداد البطاقة", "تسديد بطاقة", "credit card payment", "card repayment")),
        rule("installment", "FINANCING_INSTALLMENT", listOf("سداد قسط", "خصم قسط", "قسط تمويل", "loan repayment", "installment payment")),
        rule("withdrawal", "WITHDRAWAL", listOf("سحب نقدي", "سحب من الصراف", "cash withdrawal", "atm withdrawal")),
        rule("refund", "REFUND", listOf("استرداد", "استرجاع مبلغ", "purchase refund", "refund", "purchase reversal")),
        rule("fee", "FEE", listOf("خصم رسوم", "رسوم خدمة", "service fee", "fee charged")),
        rule("balance", "BALANCE_NOTICE", listOf("إشعار رصيد", "balance notification", "balance enquiry")),
        rule("notice", "NON_FINANCIAL", listOf("كشف حساب", "تم تسجيل الدخول", "تم إضافة مستفيد", "تم تفعيل البطاقة", "statement issued", "login notification", "beneficiary added")),
    ))

    private fun rule(id: String, family: String, phrases: List<String>) = RuleSpec(
        id = "shared.$id", family = family, phrases = phrases,
        amountLabels = listOf("بمبلغ", "مبلغ", "المبلغ", "بقيمة", "amount", "of"),
        cardLabels = listOf("بطاقة", "البطاقة", "credit card", "debit card", "card"),
        sourceLabels = listOf("من حساب", "من الحساب", "from account", "from"),
        destinationLabels = listOf("إلى حساب", "إلى الحساب", "to account", "to"),
        merchantLabels = listOf("لدى", "عند", "at", "merchant"),
        balanceLabels = listOf("الرصيد المتاح", "الرصيد", "available balance", "balance"),
        direction = LabFamilies.direction(family),
    )
}
