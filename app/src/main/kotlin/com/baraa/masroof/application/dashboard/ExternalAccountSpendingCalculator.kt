package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.*
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.time.LocalDate

data class ExternalSpendingRow(
    val rawSmsId: String,
    val amount: Money,
    val refund: Boolean,
    val merchant: String?,
    val date: LocalDate?,
)

data class ExternalSpendingCurrency(
    val purchasesAndFees: Money,
    val refunds: Money,
    val net: SignedMoneyAmount,
)

/** Read-only SMS spending evidence; never posts another holder's activity to the personal ledger. */
data class ExternalAccountSpending(
    val rows: List<ExternalSpendingRow>,
    val totals: List<ExternalSpendingCurrency>,
    val unconfirmedCount: Int,
)

object ExternalAccountSpendingCalculator {
    fun calculate(
        account: AccountRegistryEntry,
        cards: List<CardRegistryEntry>,
        records: List<ParsedEventRecord>,
        excludedRawSmsIds: Set<String> = emptySet(),
        manuallyClassifiedRawSmsIds: Set<String> = emptySet(),
    ): ExternalAccountSpending {
        require(account.ownership == OwnershipStatus.EXTERNAL)
        val ref = AccountReference(account.bank, account.maskedNumber)
        val linkedCards = cards.filter {
            it.bank == account.bank && it.linkedAccount == ref &&
                it.cardType == CardType.DEBIT && it.ownership == OwnershipStatus.EXTERNAL
        }.map { CardReference(it.bank, it.last4) }.toSet()
        var unconfirmed = 0
        val rows = records.distinctBy { it.event.rawSmsId }.mapNotNull { record ->
            val event = record.event
            if (event.rawSmsId in excludedRawSmsIds || event.bank != account.bank) return@mapNotNull null
            val localAccount = if (event.messageFamily == MessageFamily.REFUND)
                event.destinationAccountRef else event.sourceAccountRef
            val linkedCard = event.cardRef in linkedCards
            if (localAccount != ref && !linkedCard) return@mapNotNull null
            if (event.messageFamily !in setOf(MessageFamily.PURCHASE, MessageFamily.BILL_PAYMENT,
                    MessageFamily.FEE, MessageFamily.REFUND, MessageFamily.UNKNOWN)) return@mapNotNull null
            if (record.details.loanType != null) return@mapNotNull null
            val conflictingAccount = localAccount != null && localAccount != ref
            val registeredCard = event.cardRef?.let { cardRef ->
                cards.find { it.bank == cardRef.bank && it.last4 == cardRef.last4 }
            }
            val conflictingCard = registeredCard != null &&
                (registeredCard.ownership != OwnershipStatus.EXTERNAL ||
                    registeredCard.linkedAccount != null && registeredCard.linkedAccount != ref)
            if (conflictingAccount || conflictingCard ||
                event.parseStatus != ParseStatus.SUCCESS && !record.automationConfirmed ||
                event.amount == null || event.messageFamily == MessageFamily.UNKNOWN ||
                event.rawSmsId in manuallyClassifiedRawSmsIds) {
                unconfirmed++
                return@mapNotNull null
            }
            ExternalSpendingRow(event.rawSmsId, requireNotNull(event.amount),
                event.messageFamily == MessageFamily.REFUND, event.merchant ?: event.counterparty,
                record.details.occurredAtLocal?.toLocalDate())
        }.sortedWith(compareByDescending<ExternalSpendingRow> { it.date }.thenBy { it.rawSmsId })
        val totals = rows.groupBy { it.amount.currency }.toSortedMap(compareBy { it.name }).map { (currency, items) ->
            val spent = items.filterNot { it.refund }.fold(Money.zero(currency)) { sum, row -> sum + row.amount }
            val refunds = items.filter { it.refund }.fold(Money.zero(currency)) { sum, row -> sum + row.amount }
            ExternalSpendingCurrency(spent, refunds, SignedMoneyAmount.difference(spent, refunds))
        }
        return ExternalAccountSpending(rows, totals, unconfirmed)
    }
}
