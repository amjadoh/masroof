package com.baraa.masroof.lab.replica.bank

import com.baraa.masroof.lab.bank.Interpretation
import com.baraa.masroof.lab.bank.MessageInput
import com.baraa.masroof.lab.replica.core.money.Currency
import com.baraa.masroof.lab.replica.core.money.Money
import com.baraa.masroof.lab.replica.domain.model.*
import com.baraa.masroof.lab.replica.parsing.finalize.ParseFinalizer
import com.baraa.masroof.lab.replica.parsing.model.*
import com.baraa.masroof.lab.replica.parsing.validator.*

/** Facts only: no ownership inference, financial classification, re-parsing or posting. */
class InterpretationDraftMapper {
    fun map(input: MessageInput, interpretation: Interpretation, routedBank: Bank): ParseResult {
        val facts = interpretation.facts
        val blockers = interpretation.blockers.map { "ENGINE_${it.name}" }.toMutableList()
        if (!interpretation.version.isSupported()) blockers += "ENGINE_VERSION_UNSUPPORTED"
        if (interpretation.bank?.value != routedBank.id) blockers += "ENGINE_BANK_MISMATCH"
        if (interpretation.evidenceId != input.evidenceId) blockers += "ENGINE_EVIDENCE_MISMATCH"
        if (interpretation.evidence.any { it.end > input.body.length }) blockers += "ENGINE_EVIDENCE_RANGE_INVALID"
        val automatic = interpretation.status == com.baraa.masroof.lab.bank.ParseStatus.SUCCESS && blockers.isEmpty()
        if (!automatic && interpretation.status != com.baraa.masroof.lab.bank.ParseStatus.NON_FINANCIAL && blockers.isEmpty()) blockers += "ENGINE_NOT_AUTOMATIC"
        val rawFamily = MessageFamily.valueOf(facts.family.name)
        val family = if (blockers.isNotEmpty() && rawFamily in setOf(MessageFamily.OTP,MessageFamily.NON_FINANCIAL,MessageFamily.BALANCE_NOTICE)) MessageFamily.UNKNOWN else rawFamily
        val amount = facts.amount?.money()
        val amountEvidence = interpretation.evidence.firstOrNull { it.field == "AMOUNT" && it.end <= input.body.length }
        val selected = amount?.let { AmountCandidate(it,
            amountEvidence?.let { evidence -> "${evidence.ruleId}:${evidence.start}:${evidence.end}" } ?: "engine:unproven-amount",
            if (amountEvidence != null) AmountSourceKind.TRANSACTION_AMOUNT else AmountSourceKind.OTHER) }
        fun bank(id: com.baraa.masroof.lab.finance.value.BankId?): Bank = id?.let { Bank.fromId(it.value) } ?: Bank.UNKNOWN
        val details = ParsedEventDetails(transactionReference = facts.transactionReference,
            availableBalance = facts.availableBalance?.money(),outstandingBalance = facts.outstandingBalance?.money(),
            biller = facts.biller,billerCode = facts.billerCode,occurredAtLocal = facts.occurredAtLocal,
            cardSmsChannel = facts.cardChannel?.let { CardSmsChannel.valueOf(it.name) }, paymentDueDate = facts.paymentDueDate,
            exchangeRate = facts.exchangeRate,internationalFee = facts.internationalFee?.money(),labeledForeignAmount = facts.labeledForeignAmount?.money(),
            loanType = facts.loanType?.let { LoanType.valueOf(it.name) },debitSourceAccountLast4 = facts.debitSourceAccountLast4,salaryIncomeWording = facts.salaryIncomeWording)
        val draft = ParsedEventDraft(rawSmsId = input.evidenceId, bank = routedBank, messageFamily = family,
            direction = facts.direction?.let { MoneyDirection.valueOf(it.name) },amount = amount,
            purchaseChannel = facts.purchaseChannel?.let { PurchaseChannel.valueOf(it.name) },
            sourceAccountRef = facts.sourceAccount?.let { AccountReference(bank(it.bank),it.maskedNumber) },
            destinationAccountRef = facts.destinationAccount?.let { AccountReference(bank(it.bank),it.maskedNumber) },
            cardRef = facts.card?.let { CardReference(bank(it.bank),it.last4) }, merchant = facts.merchant,counterparty = facts.counterparty,
            occurredAt = facts.occurredAt,bankNetworkType = facts.bankNetwork?.let { BankNetworkType.valueOf(it.name) },
            confidence = Confidence(if (automatic) interpretation.confidence?.score ?: 1.0 else 0.0,
                listOf("engine:${interpretation.version.engine}","rules:${interpretation.version.rules}")+blockers),
            parseStatus = if (automatic) ParseStatus.SUCCESS else ParseStatus.REVIEW_REQUIRED,
            details = details,amountCandidates = listOfNotNull(selected),selectedAmount = selected)
        val validator = object : ParsedEventValidator {
            override fun validate(draft: ParsedEventDraft): ValidationResult = ValidationResult(
                DefaultParsedEventValidator().validate(draft).findings + blockers.map { ValidationFinding(it,it,ValidationSeverity.ERROR) })
        }
        return ParseFinalizer(validator).finalize(draft,"evt-${input.evidenceId}")
    }
    private fun com.baraa.masroof.lab.finance.value.Money.money() = Money.of(amount,Currency.valueOf(currency.name))
}
