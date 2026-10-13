package com.baraa.masroof.lab.engine

/** Explicit upgrade of the bundled POS rule. Never replaces custom rules or old run snapshots. */
object LabExampleRules {
    fun withEnglishCreditPurchase(pack: RulePack): RulePack {
        require(pack.bank.id == "BANK_ALJAZIRA") { "القالب خاص ببنك الجزيرة" }
        require(pack.rules.any { it.id == "purchase.pos" && it.family == "PURCHASE" }) { "قاعدة المشتريات النموذجية غير موجودة" }
        return pack.copy(rules = pack.rules.map { rule ->
            if (rule.id != "purchase.pos" || rule.family != "PURCHASE") rule else rule.copy(
                phrases = (rule.phrases + "pos purchase").distinct(),
                // "amount" alone also matches Due Amount; the observed transaction label is "of".
                amountLabels = (rule.amountLabels + "of").distinct(),
                cardLabels = (rule.cardLabels + "credit card").distinct(),
                merchantLabels = (rule.merchantLabels + "at").distinct(),
                balanceLabels = (rule.balanceLabels + "available balance").distinct(),
            )
        })
    }
}
