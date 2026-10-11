package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.FinancialTransaction

/** Precomputed display scopes. No presentation code sums or classifies money. */
data class DashboardSpendingTrends(
    val overall: DailySpendingTrend,
    val accounts: DailySpendingTrend,
    val creditCards: DailySpendingTrend,
    val byAccount: Map<String, DailySpendingTrend>,
    val byCard: Map<String, DailySpendingTrend>,
    val debitCards: DailySpendingTrend? = null,
    val purchases: DailySpendingTrend,
)

object DashboardSpendingTrendsBuilder {
    fun build(
        context: DashboardProjectionContext,
        accounts: AccountsDashboardSection,
        cards: CardsDashboardSection,
    ): DashboardSpendingTrends = with(context) {
        fun trend(rows: List<FinancialTransaction>, outflows: Boolean = false) =
            DailySpendingTrendBuilder.build(
                period = period,
                transactions = rows.distinctBy { it.id },
                parsedRecords = parsedRecords,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
                zoneId = zoneId,
                today = today,
                includeFinancialOutflows = outflows,
            )

        val cardRows = linkedMapOf<String, List<FinancialTransaction>>()
        val creditKeys = mutableSetOf<String>()
        val debitKeys = mutableSetOf<String>()
        cards.creditFacilities.facilities.flatMap { it.allCards }.forEach { card ->
            val key = CardTransactionInvolvementResolver.cardKey(card.bank.id, card.last4)
            val container = FinancialContainerIdFactory.cardId(card.bank, card.last4)
            creditKeys.add(key)
            cardRows[key] = transactions.filter { transaction ->
                key in cards.transactionCardInvolvement[transaction.id].orEmpty() ||
                    (container != null && (transaction.sourceContainerId == container || transaction.destinationContainerId == container))
            }
        }
        cards.creditFacilities.debitCards.forEach { card ->
            val key = CardTransactionInvolvementResolver.cardKey(card.bank.id, card.last4)
            debitKeys.add(key)
            cardRows[key] = transactions.filter { transaction ->
                key in cards.transactionDebitSpendInvolvement[transaction.id].orEmpty()
            }
        }
        val cardTransactionIds = cardRows.values.flatten().map { it.id }.toSet()
        val parsedById = parsedRecords.associateBy { it.event.id }
        val purchaseRows = transactions.filter {
            DailyPurchasesScope.includes(it, it.id in cardTransactionIds, parsedById)
        }
        val accountTrends = AccountDailySpendingTrendsBuilder.build(context, accounts.transactionAccountInvolvement, creditKeys,
            cards.transactionCardInvolvement)
        DashboardSpendingTrends(
            overall = trend(transactions, outflows = true),
            accounts = accountTrends.aggregate,
            creditCards = trend(creditKeys.flatMap { cardRows[it].orEmpty() }),
            byAccount = accountTrends.byAccount,
            byCard = cardRows.mapValues { (key, rows) -> trend(rows, outflows = key in debitKeys) },
            debitCards = trend(debitKeys.flatMap { cardRows[it].orEmpty() }, outflows = true),
            purchases = trend(purchaseRows, outflows = true),
        )
    }
}
