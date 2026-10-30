package com.baraa.masroof.application.transaction

import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.*
import com.baraa.masroof.domain.repository.*
import com.baraa.masroof.engine.bank.patterns.ConfirmedLoanPayment
import com.baraa.masroof.application.review.EffectiveParsedEventProvider
import com.baraa.masroof.parsing.repository.ParsedEventRecord

class LoanPaymentPreview internal constructor(
    internal val generation: Long,
    internal val rule: ConfirmedLoanPayment,
    internal val corrections: List<Pair<FinancialTransaction, FinancialTransaction>>,
    val totals: List<Money>,
    val unconfirmedCount: Int,
) {
    val transactionCount: Int get() = corrections.size
}

/** User-confirmed biller meaning; never matches amounts, dates or guessed funding accounts. */
class ConfirmedLoanPaymentsWorkflow(
    private val store: MessagePatternStore,
    private val gate: DatabaseAccessGate,
    private val transactions: FinancialTransactionRepository,
    private val effectiveEvents: EffectiveParsedEventProvider,
    private val cards: CardRegistryRepository,
    private val accounts: AccountRegistryRepository,
    private val loans: LoanRegistryRepository,
    private val reviews: ReviewRepository,
    private val inTransaction: suspend (suspend () -> Unit) -> Unit,
    private val onChanged: () -> Unit = {},
) {
    private fun biller(record: ParsedEventRecord): String? =
        (record.details.billerCode ?: record.details.biller)?.trim()?.takeIf { it.matches(Regex("[0-9]{1,16}")) }

    private fun rule(record: ParsedEventRecord): ConfirmedLoanPayment? {
        if (record.event.messageFamily != MessageFamily.BILL_PAYMENT) return null
        val code = biller(record) ?: return null
        return store.load().confirmedLoanPayments.singleOrNull { it.billerCode == code }
    }

    private fun target(rule: ConfirmedLoanPayment) = LoanReference(Bank(rule.loanBankId), LoanType.valueOf(rule.loanType))

    private suspend fun funding(record: ParsedEventRecord): String? {
        val event = record.event
        val cardRef = event.cardRef
        val accountRef = event.sourceAccountRef
        if (cardRef != null) {
            if (cardRef.bank != event.bank) return null
            val card = cards.get(cardRef) ?: return null
            if (card.ownership != OwnershipStatus.OWNED) return null
            return when (card.cardType) {
                CardType.CREDIT -> if (accountRef == null) FinancialContainerIdFactory.cardId(cardRef) else null
                CardType.DEBIT -> {
                    val linked = card.linkedAccount ?: return null
                    if (linked.bank != cardRef.bank || accountRef != null && accountRef != linked ||
                        accounts.resolve(linked) != OwnershipStatus.OWNED) null
                    else FinancialContainerIdFactory.accountId(linked)
                }
                null -> null
            }
        }
        return accountRef?.takeIf { it.bank == event.bank && accounts.resolve(it) == OwnershipStatus.OWNED }
            ?.let(FinancialContainerIdFactory::accountId)
    }

    suspend fun reviewReason(record: ParsedEventRecord): String? {
        val rule = rule(record) ?: return null
        return if (loans.resolve(target(rule)) != OwnershipStatus.OWNED || funding(record) == null)
            "loan_payment_funding_or_target_unconfirmed" else null
    }

    suspend fun attribute(transaction: FinancialTransaction, records: List<ParsedEventRecord>): FinancialTransaction {
        if (transaction.type != FinancialTransactionType.BILL_PAYMENT) return transaction
        val record = records.singleOrNull() ?: return transaction
        val rule = rule(record) ?: return transaction
        if (preservesManualDecision(record)) return transaction
        return corrected(transaction, record, rule)
    }

    private suspend fun preservesManualDecision(record: ParsedEventRecord): Boolean {
        if (record.userCorrected) return true
        val resolution = reviews.findByRawSmsId(record.event.rawSmsId)?.resolutionKind
        return resolution != null && resolution != ReviewResolutionKind.AUTO_NO_LONGER_REQUIRED
    }

    private suspend fun corrected(transaction: FinancialTransaction, record: ParsedEventRecord,
        rule: ConfirmedLoanPayment): FinancialTransaction {
        require(record.event.messageFamily == MessageFamily.BILL_PAYMENT && biller(record) == rule.billerCode)
        require(record.event.parseStatus == ParseStatus.SUCCESS && record.event.amount == transaction.amount)
        require(transaction.linkedParsedEventIds == listOf(record.event.id))
        require(loans.resolve(target(rule)) == OwnershipStatus.OWNED)
        val source = requireNotNull(funding(record)) { "Loan payment funding is unconfirmed" }
        require(transaction.sourceContainerId == source || transaction.sourceContainerId == record.event.cardRef?.let(FinancialContainerIdFactory::cardId))
        return transaction.copy(type = FinancialTransactionType.LOAN_REPAYMENT, sourceContainerId = source,
            destinationContainerId = FinancialContainerIdFactory.loanId(Bank(rule.loanBankId), LoanType.valueOf(rule.loanType)))
    }

    suspend fun preview(loan: LoanReference, billerCode: String): LoanPaymentPreview = gate.withAccess {
        require(loans.resolve(loan) == OwnershipStatus.OWNED)
        val code = com.baraa.masroof.parsing.normalizer.MessageNormalizer().normalize(billerCode).comparisonBody.trim()
        val rule = ConfirmedLoanPayment(code, loan.bank.id, loan.loanType.name)
        val current = store.load()
        val candidates = transactions.listByTypes(listOf(FinancialTransactionType.BILL_PAYMENT))
        val rawIds = transactions.listRawSmsIdsForTransactions(candidates.map { it.id })
        val records = effectiveEvents.listEffectiveByRawSmsIds(rawIds).associateBy { it.event.id }
        val corrections = mutableListOf<Pair<FinancialTransaction, FinancialTransaction>>()
        var unconfirmed = 0
        for (tx in candidates) {
            val record = tx.linkedParsedEventIds.singleOrNull()?.let(records::get) ?: continue
            if (record.event.messageFamily != MessageFamily.BILL_PAYMENT || biller(record) != rule.billerCode) continue
            if (preservesManualDecision(record) || record.event.parseStatus != ParseStatus.SUCCESS || funding(record) == null) {
                unconfirmed++
                continue
            }
            corrections += tx to corrected(tx, record, rule)
        }
        val totals = corrections.map { it.first.amount }.groupBy { it.currency }.map { (currency, items) ->
            items.fold(Money.zero(currency)) { total, item -> total + item }
        }.sortedBy { it.currency.name }
        LoanPaymentPreview(current.generation, rule, corrections, totals, unconfirmed)
    }

    suspend fun confirm(preview: LoanPaymentPreview) = gate.withExclusiveAccess {
        val current = store.load()
        check(current.generation == preview.generation) { "Preview expired" }
        require(loans.resolve(target(preview.rule)) == OwnershipStatus.OWNED)
        val priorRaw = store.captureRaw()
        val priorIds = current.confirmedLoanPayments.find { it.billerCode == preview.rule.billerCode }?.confirmedTransactionIds.orEmpty()
        val rule = preview.rule.copy(confirmedTransactionIds = priorIds + preview.corrections.map { it.first.id })
        try {
            inTransaction {
                for ((before, after) in preview.corrections) {
                    check(transactions.getById(before.id) == before) { "Transaction changed; preview again" }
                    val ids = transactions.listRawSmsIds(before.id)
                    val record = effectiveEvents.listEffectiveByRawSmsIds(ids).single()
                    check(!preservesManualDecision(record) && corrected(before, record, rule) == after) { "Evidence changed; preview again" }
                    check(transactions.update(after)) { "Loan correction failed" }
                    reviews.findByRawSmsId(record.event.rawSmsId)?.let { review ->
                        check(reviews.markResolved(review.id, ReviewResolutionKind.USER_FINANCIAL_TYPE,
                            java.time.Instant.now(), after.id) != null) { "Loan correction decision was not saved" }
                    }
                }
                store.saveConfirmedLoanPayments(current.generation,
                    current.confirmedLoanPayments.filterNot { it.billerCode == rule.billerCode } + rule)
            }
        } catch (error: Throwable) {
            store.restoreRaw(priorRaw, validate = false)
            throw error
        }
        onChanged()
    }

    /** Replays only exact historical corrections explicitly approved with the saved rule. */
    suspend fun recoverConfirmedHistory() = gate.withExclusiveAccess {
        val rules = store.load().confirmedLoanPayments
        if (rules.none { it.confirmedTransactionIds.isNotEmpty() }) return@withExclusiveAccess
        inTransaction {
            for (rule in rules) for (id in rule.confirmedTransactionIds) {
                val tx = transactions.getById(id) ?: continue
                if (tx.type != FinancialTransactionType.BILL_PAYMENT) continue
                val records = effectiveEvents.listEffectiveByRawSmsIds(transactions.listRawSmsIds(id))
                val record = records.singleOrNull() ?: continue
                if (preservesManualDecision(record)) continue
                check(transactions.update(corrected(tx, record, rule))) { "Confirmed loan correction incomplete" }
                reviews.findByRawSmsId(record.event.rawSmsId)?.let { review ->
                    check(reviews.markResolved(review.id, ReviewResolutionKind.USER_FINANCIAL_TYPE,
                        java.time.Instant.now(), tx.id) != null) { "Loan correction decision was not saved" }
                }
            }
        }
    }
}
