package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.ui.theme.MasroofSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofThemeExtras
import kotlinx.coroutines.launch
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
 * The two home heroes side by side: the period's net account movement first, then the current
 * balance of the accounts. Swipe between them or tap a dot.
 */
@Composable
fun DashboardHeroPager(
    accountsFleet: AccountsSummary,
    accounts: List<OwnedAccountUi>,
    onIncome: () -> Unit, onOutflow: () -> Unit, onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (accounts.isEmpty()) {
        DashboardHeroCard(accountsFleet, onIncome, onOutflow, modifier)
        return
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(modifier) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            pageSpacing = MasroofSpacing.cardInnerGap,
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().testTag("dashboard-hero-pager"),
        ) { page ->
            when (page) {
                0 -> DashboardHeroCard(accountsFleet, onIncome, onOutflow)
                else -> DashboardAccountBalancesCard(accounts, onOpenAccounts)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf(R.string.dashboard_hero_remaining_title, R.string.dashboard_balances_title).forEachIndexed { index, label ->
                val selected = pager.currentPage == index
                val description = stringResource(label)
                Box(
                    Modifier.size(MasroofSpacing.sectionGap * 2 + MasroofSpacing.cardInnerGap * 2, MasroofSpacing.sectionGap * 2 + MasroofSpacing.cardInnerGap * 2)
                        .clickable(onClickLabel = description) { scope.launch { pager.animateScrollToPage(index) } }
                        .semantics { contentDescription = description; this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) {
                    val color = when {
                        !selected -> MaterialTheme.colorScheme.outlineVariant
                        index == 0 -> MaterialTheme.colorScheme.primary
                        else -> MasroofThemeExtras.extendedColors.account
                    }
                    Box(Modifier.size(if (selected) MasroofSpacing.sectionGap * 2 else MasroofSpacing.cardInnerGap, MasroofSpacing.cardInnerGap)
                        .background(color, androidx.compose.foundation.shape.CircleShape))
                }
            }
        }
    }
}

/**
 * Current money across owned accounts: each set balance plus its movement since. An account
 * without a balance set in settings is left out rather than counted as zero.
 */
@Composable
fun DashboardAccountBalancesCard(accounts: List<OwnedAccountUi>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val total = AccountBalancesTotal.of(accounts)
    val value = total.amount?.let { formatLocalizedMoney(it) } ?: stringResource(R.string.dashboard_account_balance_unset)
    val coverage = when {
        total.counted == 0 -> stringResource(R.string.dashboard_balances_none_hint)
        total.counted < total.accounts -> stringResource(R.string.dashboard_balances_partial, total.counted, total.accounts)
        else -> stringResource(R.string.dashboard_balances_all, total.accounts)
    }
    com.baraa.masroof.presentation.common.MasroofHeroCard(
        title = stringResource(R.string.dashboard_balances_title),
        value = value,
        role = com.baraa.masroof.ui.theme.MasroofHeroRole.Account,
        modifier = modifier.clickable(onClick = onOpen),
        guidance = stringResource(R.string.dashboard_balances_hint),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
                Text(com.baraa.masroof.application.locale.AppDigits.display(coverage), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.dashboard_balances_no_reset), style = MaterialTheme.typography.labelSmall)
            }
            Text(stringResource(R.string.dashboard_balances_open), style = MaterialTheme.typography.labelLarge)
        }
    }
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
