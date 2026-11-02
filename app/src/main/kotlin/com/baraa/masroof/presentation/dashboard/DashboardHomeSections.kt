package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.ui.theme.MasroofSpacing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.AccountsSummary
import com.baraa.masroof.presentation.common.MasroofMetric
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.locale.formatLocalizedMoney

@Composable
fun DashboardHeroCard(
    accountsFleet: AccountsSummary,
    onIncome: () -> Unit, onOutflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unavailable = stringResource(R.string.dashboard_value_unavailable)
    val state = ProductionDashboardVisualMapper.hero(
        stringResource(R.string.dashboard_hero_remaining_title),
        accountsFleet.totalRemaining?.let { formatLocalizedMoney(it) } ?: unavailable,
        stringResource(R.string.reference_inflow_short),
        accountsFleet.totalInflow?.let { formatLocalizedMoney(it) } ?: unavailable,
        stringResource(R.string.reference_outflow_short),
        accountsFleet.totalOutflow?.let { formatLocalizedMoney(it) } ?: unavailable)
    com.baraa.masroof.ui.dashboard.DashboardHomeHero(state, MasroofIcons.appLogo, onIncome, onOutflow, modifier)
}

/**
 * Current money across owned accounts: each set balance plus its movement since. An account
 * without a balance set in settings is left out rather than counted as zero.
 */
@Composable
fun DashboardAccountBalancesCard(accounts: List<OwnedAccountUi>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val total = AccountBalancesTotal.of(accounts)
    val value = total.amount?.let { formatLocalizedMoney(it) } ?: stringResource(R.string.dashboard_account_balance_unset)
    val subtitle = when {
        total.counted == 0 -> stringResource(R.string.dashboard_balances_none_hint)
        total.counted < total.accounts -> stringResource(R.string.dashboard_balances_partial, total.counted, total.accounts)
        else -> null
    }
    com.baraa.masroof.presentation.common.MasroofHeroCard(
        title = stringResource(R.string.dashboard_balances_title),
        value = value,
        subtitle = subtitle,
        role = com.baraa.masroof.ui.theme.MasroofHeroRole.Account,
        modifier = modifier.clickable(onClick = onOpen),
        guidance = stringResource(R.string.dashboard_balances_hint),
    )
}

/** Sum of the accounts that have a balance; [amount] is null when none has one. */
data class AccountBalancesTotal(val amount: com.baraa.masroof.application.dashboard.SignedMoneyAmount?, val counted: Int, val accounts: Int) {
    companion object {
        fun of(accounts: List<OwnedAccountUi>): AccountBalancesTotal {
            val balances = accounts.mapNotNull { it.balance?.balance }
            val currency = balances.firstOrNull()?.currency
            val same = balances.filter { it.currency == currency }
            val amount = same.reduceOrNull { sum, next ->
                com.baraa.masroof.application.dashboard.SignedMoneyAmount(sum.amount.add(next.amount), sum.currency)
            }
            return AccountBalancesTotal(amount, same.size, accounts.size)
        }
    }
}

@Composable
fun DashboardQuickSummaryRow(
    trend: com.baraa.masroof.application.dashboard.DailySpendingTrend?,
    showExpense: Boolean = true, showIncome: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (trend == null) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
        if (showExpense) MasroofMetric(stringResource(R.string.approved_total_expenses), formatLocalizedMoney(trend.totalSpending),
            resolveMetricToneColor(spendingMetricTone(trend.totalSpending)), modifier = Modifier.weight(1f))
        if (showIncome) MasroofMetric(stringResource(R.string.approved_daily_average), formatLocalizedMoney(trend.averageDailySpending),
            MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
            guidance = stringResource(R.string.approved_average_basis, trend.points.size))
    }
}

@Composable
fun DashboardShortcuts(onAccounts: () -> Unit, onCards: () -> Unit, onLoans: () -> Unit, onCommitments: () -> Unit) {
    com.baraa.masroof.presentation.common.MasroofShortcutStrip(listOf(
        com.baraa.masroof.presentation.common.MasroofShortcutItem(stringResource(R.string.reference_shortcut_accounts),
            MasroofIcons.moneyMovement, com.baraa.masroof.presentation.common.MasroofCardAccent.Account, onAccounts),
        com.baraa.masroof.presentation.common.MasroofShortcutItem(stringResource(R.string.reference_shortcut_cards),
            MasroofIcons.cardPayment, com.baraa.masroof.presentation.common.MasroofCardAccent.Credit, onCards),
        com.baraa.masroof.presentation.common.MasroofShortcutItem(stringResource(R.string.reference_shortcut_loans),
            MasroofIcons.savings, com.baraa.masroof.presentation.common.MasroofCardAccent.Liability, onLoans),
        com.baraa.masroof.presentation.common.MasroofShortcutItem(stringResource(R.string.approved_commitments),
            MasroofIcons.calendar, com.baraa.masroof.presentation.common.MasroofCardAccent.Liability, onCommitments),
    ))
}
