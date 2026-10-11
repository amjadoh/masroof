package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofChoice
import com.baraa.masroof.presentation.common.MasroofHeroCard
import com.baraa.masroof.presentation.common.MasroofMetric
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofReference
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import com.baraa.masroof.presentation.theme.MasroofSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DailyExpensesScreen(state: DashboardUiState, onBack: () -> Unit, onOpenTransaction: (String) -> Unit) {
    val ordinary = state.dailySpendingTrend ?: return
    if (ordinary.points.isEmpty()) {
        DashboardSummaryScaffold(stringResource(R.string.reference_daily_title), state, onBack) { modifier ->
            com.baraa.masroof.presentation.common.MasroofCard(modifier) {
                com.baraa.masroof.presentation.common.MasroofText(stringResource(R.string.dashboard_chart_no_elapsed_days),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    var scope by rememberSaveable { mutableStateOf("all") }
    var day by rememberSaveable { mutableStateOf(ordinary.points.last().date.toString()) }
    val trend = if (scope == "all") ordinary else state.spendingTrends?.byCard?.get(scope) ?: ordinary
    val point = trend.points.firstOrNull { it.date.toString() == day } ?: trend.points.last()
    val date = DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0])
    val names = state.ownedCards.associate { CardOwnershipKey.of(it.bank, it.last4) to
        (it.displayName ?: com.baraa.masroof.presentation.common.formatCardLast4(it.last4)) }
    val allLabel = stringResource(R.string.approved_all_expenses)
    DashboardSummaryScaffold(stringResource(R.string.reference_daily_title), state, onBack) { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofReference.metricGap)) {
                val keys = listOf("all") + names.keys.toList()
                com.baraa.masroof.presentation.common.MasroofSelect(names[scope] ?: allLabel,
                    keys.map { names[it] ?: allLabel }, { scope = keys[it] }, Modifier.weight(1f), label = allLabel)
                com.baraa.masroof.presentation.common.MasroofSelect(date.format(point.date),
                    trend.points.map { date.format(it.date) }, { day = trend.points[it].date.toString() }, Modifier.weight(1f),
                    label = stringResource(R.string.reference_day_view))
            }
            MasroofHeroCard(stringResource(R.string.reference_daily_net, date.format(point.date)), formatLocalizedMoney(point.spending))
            SpendingTrendSection(trend, stringResource(R.string.reference_daily_title), selectedDate = point.date,
                onDaySelected = { day = it.toString() })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofReference.metricGap)) {
                MasroofMetric(stringResource(R.string.reference_before_refunds), formatLocalizedMoney(point.beforeRefunds),
                    MasroofThemeExtras.extendedColors.outflow, Modifier.weight(1f))
                MasroofMetric(stringResource(R.string.reference_day_refunds), formatLocalizedMoney(point.refunds),
                    MasroofThemeExtras.extendedColors.inflow, Modifier.weight(1f))
            }
            val ids = trend.transactionIdsByDate[point.date].orEmpty()
            DashboardSummaryTransactionsSection(state.allTransactions.filter { it.id in ids }, onOpenTransaction,
                ownedCards = state.ownedCards, title = stringResource(R.string.reference_day_transactions))
        }
    }
}
