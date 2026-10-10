package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.application.dashboard.DebitCardOverview

@Composable
fun DebitCardDetailScreen(
    debit: DebitCardOverview,
    state: DashboardUiState,
    cardNetwork: com.baraa.masroof.domain.model.CardNetwork?,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    val cardTransactions = DashboardSummaryTransactionFilter.forDebitCard(
        transactions = state.allTransactions,
        bank = debit.bank,
        last4 = debit.last4,
        debitSpendInvolvementByTransactionId = state.transactionDebitSpendInvolvement,
    )
    val title = debit.displayLabel

    DashboardSummaryScaffold(
        title = title,
        state = state,
        onBack = onBack,
        showPeriod = false,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
        ) {
            DebitCardDetailSummaryCard(
                debit = debit,
                network = cardNetwork,
                modifier = Modifier.fillMaxWidth(),
            )

            SpendingTrendSection(state.spendingTrends?.byCard?.get(CardOwnershipKey.of(debit)), androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.approved_card_daily))
            DashboardSummaryTransactionsSection(
                transactions = cardTransactions,
                onOpenTransaction = onOpenTransaction,
                ownedCards = state.ownedCards,
            )
        }
    }
}
