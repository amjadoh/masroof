package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.baraa.masroof.presentation.common.MasroofSectionHeader

import com.baraa.masroof.ui.theme.MasroofSpacing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.cashPosition
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.ui.theme.MasroofThemeExtras

@Composable
fun AccountsSummaryRoute(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedAccountKey by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedAccount = selectedAccountKey?.let { key ->
        state.ownedAccounts.find { ownedAccountKey(it) == key }
    }

    BackHandler {
        when {
            selectedAccount != null -> selectedAccountKey = null
            else -> onBack()
        }
    }

    when {
        selectedAccount != null -> {
            AccountDetailScreen(
                account = selectedAccount,
                state = state,
                onBack = { selectedAccountKey = null },
                onOpenTransaction = onOpenTransaction,
            )
        }

        else -> {
            AccountsSummaryScreen(
                state = state,
                onBack = onBack,
                onOpenAccount = { account -> selectedAccountKey = ownedAccountKey(account) },
            )
        }
    }
}

private fun ownedAccountKey(account: OwnedAccountUi): String =
    "${account.bank.id}:${account.maskedNumber}"

@Composable
fun AccountsSummaryScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onOpenAccount: (OwnedAccountUi) -> Unit,
) {
    DashboardSummaryScaffold(
        title = stringResource(R.string.dashboard_accounts_summary_screen_title),
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
        ) {
            DashboardSummaryHeroCard(
                spec = accountsSummaryHeroSpec(
                    fleet = resolveDashboardAccountsFleet(
                        ownedAccounts = state.ownedAccounts,
                        fleet = state.accountsFleet,
                    ),
                ),
            )

            SpendingTrendSection(state.spendingTrends?.accounts, stringResource(R.string.approved_accounts_daily),
                explanation = stringResource(R.string.reference_accounts_scope), role = com.baraa.masroof.ui.theme.MasroofHeroRole.Account, chartKey = "accounts")
            MasroofSectionHeader(title = stringResource(R.string.reference_followed_accounts))

            val hierarchy = state.bankHierarchy
            when {
                state.ownedAccounts.isEmpty() -> {
                    Text(
                        stringResource(R.string.dashboard_accounts_summary_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                hierarchy != null && hierarchy.hasContent -> {
                    hierarchy.banks.forEach { bankTree ->
                        bankTree.savingsAccounts.forEach { node ->
                            resolveOwnedAccountUi(state.ownedAccounts, node.bank, node.maskedNumber)?.let { account ->
                                AccountsSummaryAccountCard(
                                    account = account,
                                    trend = state.spendingTrends?.byAccount?.get(ownedAccountKey(account)),
                                    onClick = { onOpenAccount(account) },
                                )
                            }
                        }
                        bankTree.walletAccounts.forEach { node ->
                            resolveOwnedAccountUi(state.ownedAccounts, node.bank, node.maskedNumber)?.let { account ->
                                AccountsSummaryAccountCard(
                                    account = account,
                                    trend = state.spendingTrends?.byAccount?.get(ownedAccountKey(account)),
                                    onClick = { onOpenAccount(account) },
                                )
                            }
                        }
                        bankTree.currentAccounts.forEach { node ->
                            resolveOwnedAccountUi(state.ownedAccounts, node.bank, node.maskedNumber)?.let { account ->
                                AccountsSummaryAccountCard(
                                    account = account,
                                    trend = state.spendingTrends?.byAccount?.get(ownedAccountKey(account)),
                                    onClick = { onOpenAccount(account) },
                                )
                            }
                        }
                    }
                }

                else -> {
                    state.ownedAccounts.forEach { account ->
                        AccountsSummaryAccountCard(
                            account = account,
                            trend = state.spendingTrends?.byAccount?.get(ownedAccountKey(account)),
                            onClick = { onOpenAccount(account) },
                        )
                    }
                }
            }
        }
    }
}

private fun resolveOwnedAccountUi(
    ownedAccounts: List<OwnedAccountUi>,
    bank: Bank,
    maskedNumber: String,
): OwnedAccountUi? = ownedAccounts.find { it.bank == bank && it.maskedNumber == maskedNumber }

@Composable
private fun AccountsSummaryAccountCard(account: OwnedAccountUi, onClick: () -> Unit,
    trend: com.baraa.masroof.application.dashboard.DailySpendingTrend? = null) {
    val movement = account.periodSummary?.cashPosition()
    val unavailable = stringResource(R.string.dashboard_value_unavailable)
    EntitySummaryCard(account.displayLabel(), "",
        MasroofIcons.moneyMovement, MasroofThemeExtras.extendedColors.account, onClick = onClick,
        guidance = stringResource(R.string.dashboard_account_remaining_calculated_hint), actionLabel = stringResource(R.string.reference_account_details), bankLabel = com.baraa.masroof.presentation.settings.settingsBankLabel(account.bank)) {
        com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.dashboard_remaining_title), movement?.remaining?.let { formatLocalizedMoney(it) } ?: unavailable, dense = true)
        com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.dashboard_total_inflow), movement?.inflow?.let { formatLocalizedMoney(it) } ?: unavailable, dense = true)
        com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.dashboard_total_spent), movement?.outflow?.let { formatLocalizedMoney(it) } ?: unavailable, dense = true)
    }
}
