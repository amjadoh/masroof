package com.baraa.masroof.lab.beta.application.interpretation

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.finance.value.Currency
import com.baraa.masroof.lab.finance.value.Money
import com.baraa.masroof.lab.copy.bank.BankRoutingResult
import com.baraa.masroof.lab.copy.bank.BankSmsRegistry
import com.baraa.masroof.lab.copy.bank.aljazira.AlJaziraSmsAdapter
import com.baraa.masroof.lab.copy.parsing.model.ParseResult
import com.baraa.masroof.lab.copy.parsing.model.ParsedEventDraft
import com.baraa.masroof.lab.copy.parsing.model.ParsedEventDetails
import com.baraa.masroof.lab.copy.parsing.model.SmsParseInput

/** Read-only shadow adapter over the preserved lab copy. Never imports or invokes production. */
class LegacyInterpretationAdapter : BankInterpreter {
    override fun interpret(input: MessageInput, catalog: BankCatalog): Interpretation {
        val candidates = catalog.candidates(input.sender)
        if (candidates.size != 1) return unavailable(input, if (candidates.isEmpty()) Blocker.UNKNOWN_BANK else Blocker.AMBIGUOUS_BANK)
        val route = BankSmsRegistry(listOf(AlJaziraSmsAdapter())).route(input.sender, input.body)
        if (route !is BankRoutingResult.Matched || route.adapter.bank.id != candidates.single().value) {
            return unavailable(input, Blocker.UNKNOWN_BANK)
        }
        return map(input, candidates.single(), route.adapter.parse(SmsParseInput(input.evidenceId, input.sender, input.body, input.receivedAt)))
    }

    /** Internal mapping preserves provenance; tested also for draft-only Invalid results. */
    internal fun map(input: MessageInput, bank: BankId, result: ParseResult): Interpretation {
        val event = when (result) {
            is ParseResult.Success -> result.event
            is ParseResult.Partial -> result.event
            is ParseResult.ReviewRequired -> result.event
            is ParseResult.NonFinancial -> result.event
            else -> null
        }
        val draft = when (result) {
            is ParseResult.Partial -> result.draft
            is ParseResult.ReviewRequired -> result.draft
            is ParseResult.Invalid -> result.draft
            else -> null
        } ?: event?.let {
            ParsedEventDraft(it.rawSmsId, it.bank, it.messageFamily, it.direction, it.amount,
                it.purchaseChannel, it.sourceAccountRef, it.destinationAccountRef, it.cardRef,
                it.merchant, it.counterparty, it.occurredAt, it.bankNetworkType, it.confidence, it.parseStatus)
        }
        require(event == null || (event.rawSmsId == input.evidenceId && event.bank.id == bank.value))
        require(draft == null || (draft.rawSmsId == input.evidenceId && draft.bank?.id in setOf(null, bank.value)))
        val details = when (result) {
            is ParseResult.Success -> result.details
            is ParseResult.Partial -> result.details
            is ParseResult.ReviewRequired -> result.details
            is ParseResult.NonFinancial -> result.details
            is ParseResult.Invalid -> result.draft?.details ?: ParsedEventDetails()
            else -> ParsedEventDetails()
        }
        val status = when (result) {
            is ParseResult.Success -> ParseStatus.SUCCESS
            is ParseResult.Partial -> ParseStatus.PARTIAL
            is ParseResult.ReviewRequired -> ParseStatus.REVIEW_REQUIRED
            is ParseResult.NonFinancial -> ParseStatus.NON_FINANCIAL
            is ParseResult.Invalid -> ParseStatus.INVALID
            is ParseResult.Unsupported -> ParseStatus.UNSUPPORTED
        }
        val facts = Facts(
            family = draft?.messageFamily?.name?.let(MessageFamily::valueOf) ?: MessageFamily.UNKNOWN,
            amount = draft?.amount?.toContract(), direction = draft?.direction?.name?.let(MoneyDirection::valueOf),
            sourceAccount = draft?.sourceAccountRef?.let { AccountReference(it.bank.knownId(), it.maskedNumber) },
            destinationAccount = draft?.destinationAccountRef?.let { AccountReference(it.bank.knownId(), it.maskedNumber) },
            card = draft?.cardRef?.let { CardReference(it.bank.knownId(), it.last4) },
            purchaseChannel = draft?.purchaseChannel?.name?.let(PurchaseChannel::valueOf),
            bankNetwork = draft?.bankNetworkType?.name?.let(BankNetwork::valueOf),
            merchant = draft?.merchant, counterparty = draft?.counterparty, occurredAt = draft?.occurredAt,
            transactionReference = details.transactionReference,
            availableBalance = details.availableBalance?.toContract(), outstandingBalance = details.outstandingBalance?.toContract(),
            biller = details.biller, billerCode = details.billerCode, occurredAtLocal = details.occurredAtLocal,
            cardChannel = details.cardSmsChannel?.name?.let(CardChannel::valueOf), paymentDueDate = details.paymentDueDate,
            exchangeRate = details.exchangeRate, internationalFee = details.internationalFee?.toContract(),
            labeledForeignAmount = details.labeledForeignAmount?.toContract(), loanType = details.loanType?.name?.let(LoanType::valueOf),
            debitSourceAccountLast4 = details.debitSourceAccountLast4, salaryIncomeWording = details.salaryIncomeWording,
        )
        val confidence = draft?.confidence ?: (result as? ParseResult.NonFinancial)?.confidence
        val blockers = buildSet {
            if (facts.family == MessageFamily.UNKNOWN) add(Blocker.UNKNOWN_FAMILY)
            if (status in setOf(ParseStatus.PARTIAL, ParseStatus.REVIEW_REQUIRED, ParseStatus.INVALID)) add(Blocker.VALIDATION_ERROR)
        }
        val findings = when (result) {
            is ParseResult.Partial -> result.findings
            is ParseResult.ReviewRequired -> result.findings
            is ParseResult.Invalid -> result.findings
            else -> emptyList()
        }
        val diagnostics = findings.map { InterpretationDiagnostic(it.code, it.message,
            DiagnosticSeverity.valueOf(it.severity.name)) } + when (result) {
            is ParseResult.ReviewRequired -> result.reasons.map { InterpretationDiagnostic("REVIEW_REASON", it, DiagnosticSeverity.WARNING) }
            is ParseResult.Unsupported -> listOf(InterpretationDiagnostic("UNSUPPORTED_REASON", result.reason, DiagnosticSeverity.INFO))
            is ParseResult.NonFinancial -> listOf(InterpretationDiagnostic("NON_FINANCIAL_REASON", result.reason, DiagnosticSeverity.INFO))
            else -> emptyList()
        }
        // Legacy parser does not expose field ranges. This is provenance for the whole input only.
        val evidence = if (input.body.isEmpty()) emptyList() else listOf(Evidence("legacy.validator", "source_message", 0, input.body.length))
        return Interpretation(input.evidenceId, VERSION, bank, facts, status,
            confidence?.let { Confidence(it.score, it.reasons) }, evidence, blockers, diagnostics)
    }

    private fun com.baraa.masroof.lab.copy.core.money.Money.toContract() = Money.of(amount, Currency.valueOf(currency.name))
    private fun com.baraa.masroof.lab.copy.domain.model.Bank.knownId() = if (id == "UNKNOWN") null else BankId(id)

    companion object {
        val VERSION = InterpretationVersion(1, "lab-legacy-aljazira-v1", "preserved-parser-v1")
        internal fun unavailable(input: MessageInput, blocker: Blocker) = Interpretation(
            input.evidenceId, VERSION, null, Facts(MessageFamily.UNKNOWN), ParseStatus.REVIEW_REQUIRED,
            null, emptyList(), setOf(blocker, Blocker.UNKNOWN_FAMILY))
    }
}
