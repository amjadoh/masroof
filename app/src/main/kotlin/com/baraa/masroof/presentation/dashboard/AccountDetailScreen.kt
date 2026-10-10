package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable

@Composable
fun AccountDetailScreen(
    account: OwnedAccountUi,
    state: DashboardUiState,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    val summary = account.periodSummary
    val accountTransactions = DashboardSummaryTransactionFilter.forAccount(
        transactions = state.allTransactions,
        bank = account.bank,
        maskedNumber = account.maskedNumber,
        involvementByTransactionId = state.transactionAccountInvolvement,
    )
    val title = account.displayLabel()

    DashboardSummaryScaffold(
        title = title,
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
        ) {
            if (summary != null) {
                AccountPeriodSummaryCard(
                    summary = summary,
                )
            }

            SpendingTrendSection(state.spendingTrends?.byAccount?.get("${account.bank.id}:${account.maskedNumber}"), androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.approved_account_daily))
            DashboardSummaryTransactionsSection(
                transactions = accountTransactions,
                onOpenTransaction = onOpenTransaction,
                ownedCards = state.ownedCards,
            )
        }
    }
}
