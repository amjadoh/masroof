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

        val accountRows = ownedAccounts.associate { account ->
            val container = FinancialContainerIdFactory.accountId(account.bank, account.maskedNumber)
            val rows = transactions.filter { transaction ->
                container != null && (
                    transaction.sourceContainerId == container ||
                        transaction.destinationContainerId == container ||
                        container in accounts.transactionAccountInvolvement[transaction.id].orEmpty()
                    )
            }
            "${account.bank.id}:${account.maskedNumber}" to rows
        }
        val cardRows = linkedMapOf<String, List<FinancialTransaction>>()
        val creditKeys = mutableSetOf<String>()
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
            cardRows[key] = transactions.filter { transaction ->
                key in cards.transactionDebitSpendInvolvement[transaction.id].orEmpty()
            }
        }
        DashboardSpendingTrends(
            overall = trend(transactions, outflows = true),
            accounts = trend(accountRows.values.flatten(), outflows = true),
            creditCards = trend(creditKeys.flatMap { cardRows[it].orEmpty() }),
            byAccount = accountRows.mapValues { trend(it.value, outflows = true) },
            byCard = cardRows.mapValues { trend(it.value) },
        )
    }
}
