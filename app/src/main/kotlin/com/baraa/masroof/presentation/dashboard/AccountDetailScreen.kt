package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import com.baraa.masroof.application.settings.BalanceCheckResult
import java.math.BigDecimal

/** The account page's balance check, bound to one account by the route. */
class BalanceCheckActions(
    val result: BalanceCheckResult?,
    val onCheck: (BigDecimal) -> Unit,
    val onAdopt: (BigDecimal) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
fun AccountDetailScreen(
    account: OwnedAccountUi,
    state: DashboardUiState,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    balanceCheck: BalanceCheckActions? = null,
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
    ) {
        if (summary != null) {
            AccountPeriodSummaryCard(
                summary = summary,
                title = account.displayLabel() + " · " + androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.dashboard_remaining_title),
                subtitle = com.baraa.masroof.presentation.settings.settingsBankLabel(account.bank),
                trend = state.spendingTrends?.byAccount?.get("${account.bank.id}:${account.maskedNumber}"),
                chartKey = "account:${account.bank.id}:${account.maskedNumber}",
                balance = account.balance,
            )
        }

        if (balanceCheck != null) {
            var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            com.baraa.masroof.presentation.common.MasroofOutlinedButton(
                onClick = { open = true },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().testTag("account-balance-check"),
            ) { com.baraa.masroof.presentation.common.MasroofText(androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.balance_check_action)) }
            if (open) {
                AccountBalanceCheckDialog(
                    accountLabel = title,
                    result = balanceCheck.result,
                    onCheck = balanceCheck.onCheck,
                    onAdoptBankBalance = { balanceCheck.onAdopt(it); open = false },
                    onDismiss = { balanceCheck.onDismiss(); open = false },
                )
            }
        }

        DashboardSummaryTransactionsSection(
            transactions = accountTransactions,
            onOpenTransaction = onOpenTransaction,
            ownedCards = state.ownedCards,
            title = androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.reference_account_movements),
        )
    }
}
