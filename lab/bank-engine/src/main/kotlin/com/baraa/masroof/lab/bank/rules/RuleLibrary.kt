package com.baraa.masroof.lab.bank.rules

import com.baraa.masroof.lab.bank.*

/** Initial independent literal vocabulary. Not a bank detector or a claim of corpus coverage. */
object RuleLibrary {
    fun initial(): BankRuleSchema = BankRuleSchema(revision = 1, rules = listOf(
        rule("purchase", MessageFamily.PURCHASE, listOf("شراء", "purchase")),
        rule("refund", MessageFamily.REFUND, listOf("استرداد", "refund"), MoneyDirection.INCOMING),
        rule("withdrawal", MessageFamily.WITHDRAWAL, listOf("سحب نقدي", "cash withdrawal")),
        rule("fee", MessageFamily.FEE, listOf("خصم رسوم", "fee charged")),
        rule("balance", MessageFamily.BALANCE_NOTICE, listOf("اشعار رصيد", "balance notification")),
        rule("notice", MessageFamily.NON_FINANCIAL, listOf("تم تسجيل الدخول", "login notification")),
    ))

    private fun rule(id: String, family: MessageFamily, phrases: List<String>,
        direction: MoneyDirection = MoneyDirection.OUTGOING) = BankRule("bank.$id", family, phrases,
        direction = direction, labels = mapOf(
            ExtractionField.AMOUNT to listOf("مبلغ العملية", "بمبلغ", "amount"),
            ExtractionField.CARD to listOf("بطاقة", "card"),
            ExtractionField.SOURCE_ACCOUNT to listOf("من حساب", "from account"),
            ExtractionField.DESTINATION_ACCOUNT to listOf("الي حساب", "to account"),
            ExtractionField.MERCHANT to listOf("لدي", "merchant"),
            ExtractionField.AVAILABLE_BALANCE to listOf("الرصيد المتاح", "available balance"),
        ))
}
