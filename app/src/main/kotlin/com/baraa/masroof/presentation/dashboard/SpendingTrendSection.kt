package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.material3.TextButton
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
fun SpendingTrendSection(trend: DailySpendingTrend?, title: String, modifier: Modifier = Modifier) {
    if (trend == null) return
    val locale = LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    var selected by rememberSaveable(trend.points.first().date, trend.points.last().date) { mutableIntStateOf(trend.points.lastIndex) }
    val index = selected.coerceIn(trend.points.indices)
    val point = trend.points[index]
    val pointLabels = trend.points.map { "${date.format(it.date)} · ${formatLocalizedMoney(it.spending)}" }
    MasroofCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        MasroofDetailRow(stringResource(R.string.approved_daily_average), formatLocalizedMoney(trend.averageDailySpending))
        Text("${date.format(trend.points.first().date)} — ${date.format(trend.points.last().date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.approved_average_basis, trend.points.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            TextButton(onClick = { selected = (index - 1).coerceAtLeast(0) }, enabled = index > 0) { Text(stringResource(R.string.chart_previous_point)) }
            TextButton(onClick = { selected = (index + 1).coerceAtMost(trend.points.lastIndex) }, enabled = index < trend.points.lastIndex) { Text(stringResource(R.string.chart_next_point)) }
        }
    }
}
