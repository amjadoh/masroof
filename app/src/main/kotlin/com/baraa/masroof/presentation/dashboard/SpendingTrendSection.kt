package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.DailySpendingTrend
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofGuidanceLabel
import com.baraa.masroof.presentation.common.MasroofInteractiveLineChart
import com.baraa.masroof.presentation.common.MasroofReferenceMoney
import com.baraa.masroof.presentation.common.MasroofText
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofHeroRole
import com.baraa.masroof.presentation.theme.MasroofReference
import com.baraa.masroof.presentation.theme.MasroofSpacing
import java.time.format.DateTimeFormatter

@Composable
fun SpendingTrendSection(trend: DailySpendingTrend?, title: String, modifier: Modifier = Modifier,
    explanation: String = stringResource(R.string.approved_card_expense_basis),
    role: MasroofHeroRole = MasroofHeroRole.Brand, onDaySelected: ((java.time.LocalDate) -> Unit)? = null,
    selectedDate: java.time.LocalDate? = null) {
    if (trend == null || trend.points.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofPattern("d MMM", locale)
    val tick = DateTimeFormatter.ofPattern("d/M", locale)
    var selected by rememberSaveable(trend.points.first().date, trend.points.last().date) { mutableIntStateOf(trend.points.lastIndex) }
    val index = selectedDate?.let { day -> trend.points.indexOfFirst { it.date == day }.takeIf { it >= 0 } }
        ?: selected.coerceIn(trend.points.indices)
    val point = trend.points[index]
    val labels = trend.points.map { "${date.format(it.date)} · ${formatLocalizedMoney(it.spending)}" }
    val currency = MoneyUiFormatter.currencyLabel(point.spending.currency, locale.toLanguageTag())
    MasroofCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
            MasroofGuidanceLabel(title, explanation, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                contentColor = MaterialTheme.colorScheme.onSurface)
            Icon(com.baraa.masroof.presentation.common.MasroofIcons.analysis, null, Modifier.size(MasroofReference.icon))
        }
        Column(Modifier.fillMaxWidth().paddingForReferenceChartHead(),
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.none)) {
            MasroofText(date.format(point.date), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            MasroofReferenceMoney(formatLocalizedMoney(point.spending), style = MasroofReference.selectedAmount)
        }
        MasroofInteractiveLineChart(trend.points.map { it.spending.amount }, trend.averageDailySpending.amount,
            Modifier.testTag("daily-expenses-chart"), index,
            onPointSelected = { selected = it; onDaySelected?.invoke(trend.points[it].date) },
            pointLabel = { labels[it] }, role = role, axisLabel = { tick.format(trend.points[it].date) },
            currencyLabel = currency)
    }
}

private fun Modifier.paddingForReferenceChartHead(): Modifier =
    this.then(Modifier.padding(top = MasroofReference.chartHeadGap))
