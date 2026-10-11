package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofSpacing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.AccountsSummary
import com.baraa.masroof.presentation.common.MasroofCompactCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun DashboardHeroCard(
    accountsFleet: AccountsSummary,
    onIncome: () -> Unit, onOutflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unavailable = stringResource(R.string.dashboard_value_unavailable)
    com.baraa.masroof.presentation.common.MasroofHeroCard(
        title = stringResource(R.string.dashboard_hero_remaining_title),
        value = accountsFleet.totalRemaining?.let { formatLocalizedMoney(it) } ?: unavailable,
        role = com.baraa.masroof.presentation.theme.MasroofHeroRole.Brand,
        modifier = modifier,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.clickable(onClick = onIncome)) {
                Text(stringResource(R.string.dashboard_total_inflow), style = MaterialTheme.typography.labelSmall)
                Text(accountsFleet.totalInflow?.let { formatLocalizedMoney(it) } ?: unavailable, style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.clickable(onClick = onOutflow), horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.dashboard_total_spent), style = MaterialTheme.typography.labelSmall)
                Text(accountsFleet.totalOutflow?.let { formatLocalizedMoney(it) } ?: unavailable, style = MaterialTheme.typography.titleMedium)
            }
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
    val extended = MasroofThemeExtras.extendedColors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
        if (showExpense) MasroofCompactCard(stringResource(R.string.approved_total_expenses), formatLocalizedMoney(trend.totalSpending),
            resolveMetricToneColor(spendingMetricTone(trend.totalSpending)), MasroofIcons.netSpending, extended.outflowSoft, extended.outflow, modifier = Modifier.weight(1f))
        if (showIncome) MasroofCompactCard(stringResource(R.string.approved_daily_average), formatLocalizedMoney(trend.averageDailySpending),
            MaterialTheme.colorScheme.primary, MasroofIcons.analysis, MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
    }
}

@Composable
fun DashboardShortcuts(onAccounts: () -> Unit, onCards: () -> Unit, onLoans: () -> Unit, onCommitments: () -> Unit) {
    val extended = MasroofThemeExtras.extendedColors
    val items = listOf(
        Triple(R.string.dashboard_accounts_summary_title, MasroofIcons.moneyMovement, onAccounts),
        Triple(R.string.dashboard_cards_summary_title, MasroofIcons.cardPayment, onCards),
        Triple(R.string.dashboard_loans_summary_title, MasroofIcons.savings, onLoans),
        Triple(R.string.approved_commitments, MasroofIcons.calendar, onCommitments),
    )
    Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
                pair.forEach { (label, icon, onClick) ->
                    com.baraa.masroof.presentation.common.MasroofNavCard(onClick, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
                            Icon(icon, null, tint = when(label) {
                                R.string.dashboard_accounts_summary_title -> extended.account
                                R.string.dashboard_cards_summary_title -> extended.card
                                else -> extended.liability
                            })
                            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}
