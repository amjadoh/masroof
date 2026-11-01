package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.TransactionListRow
import com.baraa.masroof.application.dashboard.TransactionListWorkflow
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransactionType

typealias TransactionListFilterState = com.baraa.masroof.application.dashboard.TransactionListQuery

data class TransactionListFilterResult(val transactions: List<TransactionPreviewUi>, val totalAmount: Money?)
data class TransactionListProjectionUi(
    val result: TransactionListFilterResult = TransactionListFilterResult(emptyList(), null),
    val availableTypes: List<FinancialTransactionType> = emptyList(),
    val availableCards: List<String> = emptyList(),
    val availableAccounts: List<String> = emptyList(),
)

/** Maps UI rows without calculating money, parsing container ids or reading storage. */
object TransactionListPresentationMapper {
    fun project(
        transactions: List<TransactionPreviewUi>,
        filter: TransactionListFilterState,
        ownedCardKeys: Set<String> = emptySet(),
        ownedAccountContainerIds: Set<String> = emptySet(),
        accountInvolvementByTransactionId: Map<String, Set<String>> = emptyMap(),
        primaryCurrency: Currency = Currency.SAR,
        workflow: TransactionListWorkflow = TransactionListWorkflow(),
    ): TransactionListProjectionUi {
        val facts = transactions.map { TransactionListRow(it.id, it.title, it.amount, it.typeLabelResHint,
            it.cardLast4, it.sourceContainerId, it.destinationContainerId, it.searchText) }
        val projection = workflow.project(facts, filter, ownedCardKeys, ownedAccountContainerIds,
            accountInvolvementByTransactionId, primaryCurrency)
        return TransactionListProjectionUi(
            TransactionListFilterResult(projection.selection.indices.map(transactions::get), projection.selection.totalAmount),
            projection.availableTypes, projection.availableCards, projection.availableAccounts,
        )
    }
}
