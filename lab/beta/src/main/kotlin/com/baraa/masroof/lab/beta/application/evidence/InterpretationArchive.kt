package com.baraa.masroof.lab.beta.application.evidence

import com.baraa.masroof.lab.bank.Interpretation
import com.baraa.masroof.lab.finance.value.Money
import org.json.JSONArray
import org.json.JSONObject

/** Lossless versioned local archive only, never a log/report or a public summary. */
internal object InterpretationArchive {
    private val currentVersion = com.baraa.masroof.lab.bank.interpretation.RuleInterpreter(
        com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService.currentSchema()).version
    fun isCurrent(json: JSONObject): Boolean = json.optInt("archiveVersion") == 1 &&
        json.optInt("contractVersion") == currentVersion.contract && json.optString("authority") == "BANK_ENGINE" &&
        json.optString("engineVersion") == currentVersion.engine && json.optString("rulesVersion") == currentVersion.rules

    fun encode(value: Interpretation): String {
        val f = value.facts
        fun money(value: Money?) = value?.let { JSONObject().put("amount", it.amount.toPlainString()).put("currency", it.currency.name) }
        fun reference(bank: String?, number: String?) = JSONObject().put("bank", bank ?: JSONObject.NULL)
            .put("number", number ?: JSONObject.NULL)
        val facts = JSONObject()
        mapOf(
            "family" to f.family.name, "direction" to f.direction?.name,
            "amount" to money(f.amount), "sourceAccount" to f.sourceAccount?.let { reference(it.bank?.value, it.maskedNumber) },
            "destinationAccount" to f.destinationAccount?.let { reference(it.bank?.value, it.maskedNumber) },
            "card" to f.card?.let { reference(it.bank?.value, it.last4) }, "debitSourceAccountLast4" to f.debitSourceAccountLast4,
            "cardChannel" to f.cardChannel?.name, "purchaseChannel" to f.purchaseChannel?.name,
            "bankNetwork" to f.bankNetwork?.name, "transactionReference" to f.transactionReference,
            "merchant" to f.merchant, "counterparty" to f.counterparty, "occurredAtLocal" to f.occurredAtLocal?.toString(),
            "occurredAt" to f.occurredAt?.toString(), "availableBalance" to money(f.availableBalance),
            "outstandingBalance" to money(f.outstandingBalance), "paymentDueDate" to f.paymentDueDate?.toString(),
            "exchangeRate" to f.exchangeRate?.toPlainString(), "internationalFee" to money(f.internationalFee),
            "labeledForeignAmount" to money(f.labeledForeignAmount), "biller" to f.biller, "billerCode" to f.billerCode,
            "loanType" to f.loanType?.name, "salaryIncomeWording" to f.salaryIncomeWording, "ibanSuffix" to f.ibanSuffix,
            "executionState" to f.executionState, "cardKind" to f.cardKind, "network" to f.network,
            "senderName" to f.senderName, "recipientName" to f.recipientName, "senderBank" to f.senderBank,
            "accountIdentifier" to f.accountIdentifier,
        ).forEach { (key, field) -> facts.put(key, field ?: JSONObject.NULL) }
        return JSONObject().put("archiveVersion", 1).put("evidenceId", value.evidenceId)
            .put("authority", if (value.version.engine == com.baraa.masroof.lab.bank.interpretation.RuleInterpreter.VERSION) "BANK_ENGINE" else "HISTORICAL")
            .put("contractVersion", value.version.contract).put("engineVersion", value.version.engine)
            .put("rulesVersion", value.version.rules).put("bank", value.bank?.value ?: JSONObject.NULL)
            .put("status", value.status.name).put("facts", facts)
            .put("confidence", value.confidence?.let {
                JSONObject().put("score", it.score).put("reasons", JSONArray(it.reasons))
            } ?: JSONObject.NULL)
            .put("evidence", JSONArray(value.evidence.map { JSONObject().put("ruleId", it.ruleId)
                .put("field", it.field).put("start", it.start).put("end", it.end) }))
            .put("blockers", JSONArray(value.blockers.map { it.name }.sorted()))
            .put("diagnostics", JSONArray(value.diagnostics.map { JSONObject().put("code", it.code)
                .put("message", it.message).put("severity", it.severity.name) }))
            .toString()
    }
}
