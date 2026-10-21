package com.baraa.masroof.lab.beta.bank

import com.baraa.masroof.lab.replica.domain.model.ParsedEvent
import com.baraa.masroof.lab.replica.parsing.model.ParsedEventDetails
import kotlinx.serialization.json.*
import java.io.File

/** Shared strict field comparison for canonical fixtures and production regression replay. */
internal fun productionParityErrors(rawSmsId: String, p: ParsedEvent, e: ParsedEvent,
    productionDetails: ParsedEventDetails?, actualDetails: ParsedEventDetails?): List<String> {
    val errors = mutableListOf<String>()
    val fields = mutableMapOf("family" to (p.messageFamily to e.messageFamily), "status" to (p.parseStatus to e.parseStatus), "amount" to (p.amount to e.amount),"direction" to (p.direction to e.direction),
        "source" to (p.sourceAccountRef to e.sourceAccountRef),"destination" to (p.destinationAccountRef to e.destinationAccountRef),
        "card" to (p.cardRef to e.cardRef),"merchant" to (p.merchant to e.merchant),"counterparty" to (p.counterparty to e.counterparty),
        "purchaseChannel" to (p.purchaseChannel to e.purchaseChannel),"network" to (p.bankNetworkType to e.bankNetworkType),
        "occurredAt" to (p.occurredAt to e.occurredAt))
    fun addDetails(d: ParsedEventDetails?) = mapOf("transactionReference" to d?.transactionReference,"availableBalance" to d?.availableBalance,
        "outstandingBalance" to d?.outstandingBalance,"biller" to d?.biller,"billerCode" to d?.billerCode,"occurredAtLocal" to d?.occurredAtLocal,
        "cardSmsChannel" to d?.cardSmsChannel,"paymentDueDate" to d?.paymentDueDate,"exchangeRate" to d?.exchangeRate,"internationalFee" to d?.internationalFee,
        "labeledForeignAmount" to d?.labeledForeignAmount,"loanType" to d?.loanType,"debitSourceAccountLast4" to d?.debitSourceAccountLast4,"salaryIncomeWording" to d?.salaryIncomeWording)
    val pd = addDetails(productionDetails); val ad = addDetails(actualDetails)
    pd.forEach { (k,v) -> fields["details.$k"] = v to ad[k] }
    val approved = Json.parseToJsonElement(File("ENGINE_PARITY_DIFFERENCES.json").readText()).jsonObject[rawSmsId]?.jsonObject
    fun equivalent(a: Any?, b: Any?) = if (a is java.math.BigDecimal && b is java.math.BigDecimal) a.compareTo(b)==0 else a==b
    fields.filterValues { !equivalent(it.first,it.second) }.forEach { (name,values) ->
        val change = approved?.get(name)?.jsonObject
        if (change == null || change["production"]?.jsonPrimitive?.content != values.first.toString() || change["engine"]?.jsonPrimitive?.content != values.second.toString())
            errors += "$name production=${values.first} engine=${values.second}"
    }
    return errors
}
