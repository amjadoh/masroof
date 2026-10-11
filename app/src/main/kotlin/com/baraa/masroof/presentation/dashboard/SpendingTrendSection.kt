package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.DailySpendingTrend
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofDetailRow
import com.baraa.masroof.presentation.common.MasroofInteractiveLineChart
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import java.time.format.DateTimeFormatter

/** The same accessible interaction and averaging window at every scope. */
@Composable
fun SpendingTrendSection(trend: DailySpendingTrend?, title: String, modifier: Modifier = Modifier,
    explanation: String = stringResource(R.string.approved_card_expense_basis)) {
    if (trend == null) return
    val locale = LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    var selected by rememberSaveable(trend.points.first().date, trend.points.last().date) { mutableIntStateOf(trend.points.lastIndex) }
    val index = selected.coerceIn(trend.points.indices)
    val point = trend.points[index]
    val pointLabels = trend.points.map { "${date.format(it.date)} · ${formatLocalizedMoney(it.spending)}" }
    MasroofCard(modifier = modifier) {
        com.baraa.masroof.presentation.common.MasroofGuidanceLabel(title, explanation,
            style = MaterialTheme.typography.titleMedium, contentColor = MaterialTheme.colorScheme.onSurface)
        MasroofDetailRow(stringResource(R.string.approved_daily_average), formatLocalizedMoney(trend.averageDailySpending),
            guidance = stringResource(R.string.approved_average_basis, trend.points.size))
        Text("${date.format(trend.points.first().date)} — ${date.format(trend.points.last().date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MasroofDetailRow(date.format(point.date), formatLocalizedMoney(point.spending))
        MasroofInteractiveLineChart(
            modifier = Modifier.testTag("daily-expenses-chart"),
            values = trend.points.map { it.spending.amount },
            referenceValue = trend.averageDailySpending.amount,
            selectedPointIndex = index,
            onPointSelected = { selected = it },
            pointLabel = { i -> pointLabels[i] },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(date.format(trend.points.first().date), Modifier.testTag("chart-first-date"), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.format(trend.points.last().date), Modifier.testTag("chart-last-date"), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
