package com.baraa.masroof.lab.engine

import com.baraa.masroof.lab.copy.bank.BankRoutingResult
import com.baraa.masroof.lab.copy.bank.BankSmsRegistry
import com.baraa.masroof.lab.copy.bank.aljazira.AlJaziraSmsAdapter
import com.baraa.masroof.lab.copy.parsing.model.*
import java.time.Instant

object LabResultProjection {
    fun baseline(sender: String, body: String, id: String, receivedAt: Instant): LabOutcome {
        val route = BankSmsRegistry(listOf(AlJaziraSmsAdapter())).route(sender, body)
        return when (route) {
            is BankRoutingResult.Matched -> project(route.adapter.parse(SmsParseInput(id, sender, body, receivedAt)))
            is BankRoutingResult.NotMatched -> LabOutcome("UNSUPPORTED", trace = listOf("المرسل لا يطابق البنك المرجعي"))
            else -> LabOutcome("REVIEW_REQUIRED", trace = listOf("توجيه البنك المرجعي يحتاج مراجعة"))
        }
    }

    fun project(result: ParseResult): LabOutcome {
        val event = when (result) {
            is ParseResult.Success -> result.event
            is ParseResult.NonFinancial -> result.event
            is ParseResult.ReviewRequired -> result.event
            is ParseResult.Partial -> result.event
            else -> null
        }
        val status = when (result) {
            is ParseResult.Success -> "SUCCESS"
            is ParseResult.NonFinancial -> "NON_FINANCIAL"
            is ParseResult.ReviewRequired -> "REVIEW_REQUIRED"
            is ParseResult.Partial -> "PARTIAL"
            is ParseResult.Unsupported -> "UNSUPPORTED"
            is ParseResult.Invalid -> "INVALID"
        }
        val details = when (result) {
            is ParseResult.Success -> result.details
            is ParseResult.NonFinancial -> result.details
            is ParseResult.ReviewRequired -> result.details
            is ParseResult.Partial -> result.details
            else -> ParsedEventDetails()
        }
        val trace = when (result) {
            is ParseResult.ReviewRequired -> result.reasons + result.findings.map { "${it.code}: ${it.message}" }
            is ParseResult.Invalid -> result.findings.map { "${it.code}: ${it.message}" }
            is ParseResult.Partial -> result.findings.map { "${it.code}: ${it.message}" }
            is ParseResult.Unsupported -> listOf(result.reason)
            is ParseResult.NonFinancial -> listOf(result.reason)
            else -> emptyList()
        }
        val confidence = event?.confidence ?: (result as? ParseResult.NonFinancial)?.confidence
        return LabOutcome(status, LabFacts(event?.messageFamily?.name, event?.direction?.name,
            event?.amount?.amount?.toPlainString(), event?.amount?.currency?.name, event?.cardRef?.last4,
            event?.sourceAccountRef?.maskedNumber, event?.destinationAccountRef?.maskedNumber,
            event?.merchant, details.availableBalance?.amount?.toPlainString(), balanceCurrency = details.availableBalance?.currency?.name,
            dueAmount = details.outstandingBalance?.amount?.toPlainString(), dueCurrency = details.outstandingBalance?.currency?.name,
            occurredAtLocal = details.occurredAtLocal?.toString(), cardChannel = details.cardSmsChannel?.name,
            purchaseChannel = event?.purchaseChannel?.name, bankNetwork = event?.bankNetworkType?.name,
            transactionReference = details.transactionReference, counterparty = event?.counterparty,
            biller = details.biller, billerCode = details.billerCode, paymentDueDate = details.paymentDueDate?.toString(),
            loanType = details.loanType?.name, salaryIncomeWording = details.salaryIncomeWording,
            debitSourceAccountLast4 = details.debitSourceAccountLast4,
            feeAmount = details.internationalFee?.amount?.toPlainString(), feeCurrency = details.internationalFee?.currency?.name,
            exchangeRate = details.exchangeRate?.toPlainString(), foreignAmount = details.labeledForeignAmount?.amount?.toPlainString(),
            foreignCurrency = details.labeledForeignAmount?.currency?.name), trace + event?.confidence?.reasons.orEmpty(),
            confidenceScore = confidence?.score, confidenceReasons = confidence?.reasons.orEmpty())
    }
}
