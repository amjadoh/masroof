package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.DashboardSpendingTrends
import com.baraa.masroof.presentation.common.MasroofChartScopeTabs

/** One Home chart. The selected scope owns its series, average and contextual guidance. */
@Composable
fun HomeSpendingTrendSection(trends: DashboardSpendingTrends) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(stringResource(R.string.dashboard_daily_purchases), stringResource(R.string.approved_all_expenses))
    SpendingTrendSection(
        trend = if (selected == 0) trends.purchases else trends.overall,
        title = labels[selected],
        explanation = stringResource(if (selected == 0) R.string.dashboard_purchases_trend_basis else R.string.dashboard_all_trend_basis),
        showAverage = true,
        controls = { MasroofChartScopeTabs(labels, selected, onSelected = { selected = it }) },
    )
}
