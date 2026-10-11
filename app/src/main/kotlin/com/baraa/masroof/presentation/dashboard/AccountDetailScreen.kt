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
        title = androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.reference_account_detail_title),
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
                    title = account.displayLabel() + " · " + androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.dashboard_remaining_title),
                    subtitle = com.baraa.masroof.presentation.settings.settingsBankLabel(account.bank),
                    trend = state.spendingTrends?.byAccount?.get("${account.bank.id}:${account.maskedNumber}"),
                )
            }

            DashboardSummaryTransactionsSection(
                transactions = accountTransactions,
                onOpenTransaction = onOpenTransaction,
                ownedCards = state.ownedCards,
                title = androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.reference_account_movements),
            )
        }
    }
}
