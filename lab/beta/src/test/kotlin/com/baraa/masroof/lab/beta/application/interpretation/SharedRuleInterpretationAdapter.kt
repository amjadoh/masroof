package com.baraa.masroof.lab.beta.application.interpretation

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.finance.value.Currency
import com.baraa.masroof.lab.finance.value.Money
import com.baraa.masroof.lab.engine.BankProfile
import com.baraa.masroof.lab.engine.RuleSpec
import com.baraa.masroof.lab.engine.SharedLabEngine
import com.baraa.masroof.lab.engine.SharedRuleLibrary
import com.baraa.masroof.lab.engine.SharedRuleDefaults
import com.baraa.masroof.lab.engine.NetworkRuleConfig
import java.time.Clock
import java.time.LocalDateTime

/** M6 transition: calls the exact configurable engine used by Parsing Lab; never copies its rules. */
class SharedRuleInterpretationAdapter(schema: BankRuleSchema, clock: Clock = Clock.systemUTC()) : BankInterpreter {
    private val snapshot = schema.copy(network = schema.network.copy(bankAliases = schema.network.bankAliases.mapValues { it.value.toList() },
        sourceBankLabels = schema.network.sourceBankLabels.toList(), destinationBankLabels = schema.network.destinationBankLabels.toList(),
        networkLabels = schema.network.networkLabels.toList()), rules = schema.rules.map { it.copy(
        phrases = it.phrases.toList(), excludes = it.excludes.toList(), labels = it.labels.mapValues { entry -> entry.value.toList() },
        detailLabels = it.detailLabels.mapValues { entry -> entry.value.toList() }, observations = it.observations.mapValues { entry -> entry.value.toList() }) })
    private val engine = SharedLabEngine(clock)

    override fun interpret(input: MessageInput, catalog: BankCatalog): Interpretation {
        val banks = catalog.candidates(input.sender)
        if (banks.size != 1) return LegacyInterpretationAdapter.unavailable(input,
            if (banks.isEmpty()) Blocker.UNKNOWN_BANK else Blocker.AMBIGUOUS_BANK).copy(version = version())
        val bank = banks.single()
        val library = try { snapshot.validate(); require(!snapshot.network.standaloneSourceBrackets); library() } catch (_: IllegalArgumentException) {
            return failure(input, bank, Blocker.UNSUPPORTED_RULE_SCHEMA)
        }
        val result = try {
            engine.parse(listOf(BankProfile(bank.value, bank.value, listOf(input.sender))), library,
                input.sender, input.body, input.evidenceId, input.receivedAt)
        } catch (_: IllegalArgumentException) { return failure(input, bank, Blocker.UNSUPPORTED_RULE_SCHEMA) }
        val old = result.facts
        fun money(amount: String?) = amount?.let { Money.of(it, Currency.valueOf(requireNotNull(old.currency))) }
        val family = old.family?.let(MessageFamily::valueOf) ?: MessageFamily.UNKNOWN
        val facts = Facts(family = family, amount = money(old.amount), direction = old.direction?.let(MoneyDirection::valueOf),
            sourceAccount = old.source?.let { AccountReference(if (family == MessageFamily.TRANSFER_IN && old.bankNetwork != "INTRA_BANK") null else bank, it) },
            destinationAccount = old.destination?.let { AccountReference(if (family == MessageFamily.TRANSFER_OUT && old.bankNetwork != "INTRA_BANK") null else bank, it) },
            card = old.card?.let { CardReference(bank, it) }, merchant = old.merchant,
            // Secondary amounts carry their own currency, never the principal amount currency.
            availableBalance = currencyMoney(old.balance, old.balanceCurrency),
            outstandingBalance = currencyMoney(old.dueAmount, old.dueCurrency),
            occurredAtLocal = old.occurredAtLocal?.let(LocalDateTime::parse), ibanSuffix = old.ibanSuffix,
            executionState = old.executionState, cardKind = old.cardKind, network = old.network,
            internationalFee = currencyMoney(old.feeAmount, old.feeCurrency),
            exchangeRate = old.exchangeRate?.toBigDecimal(), senderName = old.senderName,
            recipientName = old.recipientName, senderBank = old.senderBank, accountIdentifier = old.accountIdentifier,
            purchaseChannel = old.purchaseChannel?.let(PurchaseChannel::valueOf), cardChannel = old.cardChannel?.let(CardChannel::valueOf),
            bankNetwork = old.bankNetwork?.let(BankNetwork::valueOf), transactionReference = old.transactionReference,
            counterparty = old.counterparty, biller = old.biller, billerCode = old.billerCode,
            paymentDueDate = old.paymentDueDate?.let(java.time.LocalDate::parse), loanType = old.loanType?.let(LoanType::valueOf),
            debitSourceAccountLast4 = old.debitSourceAccountLast4, salaryIncomeWording = old.salaryIncomeWording,
            labeledForeignAmount = currencyMoney(old.foreignAmount, old.foreignCurrency))
        val map = OriginalEvidence(input.body)
        val validMapping = map.normalized == result.normalizedText
        val evidence = if (validMapping) result.evidence.map(map::map) else emptyList()
        val lostCurrency = (old.balance != null && facts.availableBalance == null) ||
            (old.dueAmount != null && facts.outstandingBalance == null) || (old.feeAmount != null && facts.internationalFee == null) ||
            (old.foreignAmount != null && facts.labeledForeignAmount == null)
        val status = ParseStatus.valueOf(result.status)
        val blockers = buildSet {
            if (family == MessageFamily.UNKNOWN) add(Blocker.UNKNOWN_FAMILY)
            if (status in setOf(ParseStatus.REVIEW_REQUIRED, ParseStatus.PARTIAL, ParseStatus.INVALID)) add(Blocker.VALIDATION_ERROR)
            if (!validMapping && result.evidence.isNotEmpty()) add(Blocker.EVIDENCE_MAPPING_ERROR)
            if (lostCurrency) add(Blocker.VALIDATION_ERROR)
        }
        return Interpretation(input.evidenceId, version(), bank, facts,
            if (status == ParseStatus.SUCCESS && blockers.isNotEmpty()) ParseStatus.REVIEW_REQUIRED else status,
            result.confidenceScore?.let { Confidence(it, result.confidenceReasons) }, evidence, blockers,
            result.trace.map { InterpretationDiagnostic("LAB_TRACE", it, DiagnosticSeverity.INFO) })
    }

    private fun currencyMoney(amount: String?, currency: String?): Money? =
        if (amount != null && currency != null) Money.of(amount, Currency.valueOf(currency)) else null

    private fun library() = SharedRuleLibrary(snapshot.revision, snapshot.rules.map { rule ->
        fun labels(field: ExtractionField) = rule.labels[field].orEmpty()
        RuleSpec(rule.id, rule.family.name, rule.phrases, rule.excludes,
            labels(ExtractionField.AMOUNT), labels(ExtractionField.CARD), labels(ExtractionField.SOURCE_ACCOUNT),
            labels(ExtractionField.DESTINATION_ACCOUNT), labels(ExtractionField.MERCHANT), labels(ExtractionField.AVAILABLE_BALANCE), rule.direction.name,
            rule.detailLabels, rule.observations)
    }, NetworkRuleConfig(snapshot.network.bankAliases, snapshot.network.sourceBankLabels, snapshot.network.destinationBankLabels, snapshot.network.standaloneDestinationBrackets, snapshot.network.networkLabels))
    private fun version() = InterpretationVersion(1, SharedLabEngine.VERSION, "literal-v${snapshot.schema}-r${snapshot.revision}")
    private fun failure(input: MessageInput, bank: BankId, blocker: Blocker) = Interpretation(input.evidenceId,
        version(), bank, Facts(MessageFamily.UNKNOWN), ParseStatus.REVIEW_REQUIRED, null, emptyList(), setOf(blocker, Blocker.UNKNOWN_FAMILY))
    companion object {
        /** Adapts the existing library, preserving one source for Lab and Beta rules. */
        fun currentLabSchema(): BankRuleSchema = SharedRuleDefaults.create().let { library ->
            BankRuleSchema(revision = library.version, rules = library.rules.map { r ->
                BankRule(r.id, MessageFamily.valueOf(r.family), r.phrases, r.excludes,
                    MoneyDirection.valueOf(r.direction), mapOf(
                        ExtractionField.AMOUNT to r.amountLabels, ExtractionField.CARD to r.cardLabels,
                        ExtractionField.SOURCE_ACCOUNT to r.sourceLabels, ExtractionField.DESTINATION_ACCOUNT to r.destinationLabels,
                        ExtractionField.MERCHANT to r.merchantLabels, ExtractionField.AVAILABLE_BALANCE to r.balanceLabels),
                    detailLabels = r.detailLabels, observations = r.observations)
            }, network = NetworkRules(library.network.bankAliases, library.network.sourceBankLabels, library.network.destinationBankLabels, library.network.standaloneDestinationBrackets, library.network.networkLabels))
        }
    }

}
