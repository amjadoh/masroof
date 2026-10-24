package com.baraa.masroof.presentation.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.analytics.*
import com.baraa.masroof.application.dashboard.AnalysisSpendingGroup
import com.baraa.masroof.application.locale.AppDigits
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.ui.components.MasroofValueBarRow
import com.baraa.masroof.ui.components.MasroofDivergingBarRow
import com.baraa.masroof.ui.components.MasroofAnalyticsChartMath
import com.baraa.masroof.ui.theme.MasroofSpacing
import java.math.BigDecimal

@Composable internal fun visualMoney(value: Long) = formatLocalizedMoney(analysisAmount(value))
internal fun fraction(value: Long, maximum: Long) = com.baraa.masroof.ui.components.MasroofBarChart.progress(BigDecimal.valueOf(value), BigDecimal.valueOf(maximum))

@Composable internal fun AnalysisComparisonCard(snapshot: AnalysisSnapshot) {
    val comparison = snapshot.insights?.comparison
    MasroofCard {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofSectionHeader(stringResource(R.string.lab_analysis_insights), icon = MasroofIcons.analysis)
            if (comparison == null) Text(stringResource(R.string.lab_analysis_comparison_missing), style = MaterialTheme.typography.bodySmall)
            else {
                Text(stringResource(R.string.lab_analysis_comparison_dates, day(comparison.from), day(comparison.through),
                    day(comparison.previousFrom), day(comparison.previousThrough)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Amount(stringResource(if (comparison.delta < 0) R.string.analysis_decrease else R.string.analysis_increase, ""), comparison.delta)
                comparison.percent?.let { Text(stringResource(R.string.lab_analysis_comparison_percent, it), style = MaterialTheme.typography.labelMedium) }
                val maximum = maxOf(comparison.current, comparison.previous, 0)
                MasroofValueBarRow(stringResource(R.string.lab_analysis_comparison_current), visualMoney(comparison.current), fraction(comparison.current, maximum))
                MasroofValueBarRow(stringResource(R.string.lab_analysis_comparison_previous), visualMoney(comparison.previous), fraction(comparison.previous, maximum), previous = true)
            }
        }
    }
}

@Composable internal fun AnalysisComponentBars(snapshot: AnalysisSnapshot, onOpenGroup: (String) -> Unit) {
    val groups = snapshot.insights?.components.orEmpty()
    val maximum = groups.maxOfOrNull { it.amount } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        MasroofSectionHeader(stringResource(R.string.analysis_composition))
        groups.forEach { group ->
            MasroofValueBarRow(spendingGroupName(group), visualMoney(group.amount), fraction(if (group.amount < 0) 0 else group.amount, maximum),
                modifier = Modifier.testTag("analysis-component-${group.key}"),
                style = if (group.amount < 0) com.baraa.masroof.ui.components.MasroofHorizontalBarStyle.Inflow else com.baraa.masroof.ui.components.MasroofHorizontalBarStyle.Outflow,
                onClick = { onOpenGroup(group.key) })
        }
    }
}

@Composable internal fun AnalysisChangeCard(snapshot: AnalysisSnapshot, onOpenGroup: (String) -> Unit) {
    val changes = snapshot.insights?.comparison?.changes.orEmpty()
    if (changes.isEmpty()) return
    val maximum = changes.maxOf { BigDecimal.valueOf(it.delta).abs() }
    MasroofCard {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofGuidanceLabel(stringResource(R.string.analysis_change_title), stringResource(R.string.lab_analysis_changes_hint), style = MaterialTheme.typography.titleMedium)
            changes.forEach { change ->
                val magnitude = BigDecimal.valueOf(change.delta).abs()
                val label = stringResource(if (change.delta < 0) R.string.analysis_decrease else R.string.analysis_increase,
                    formatLocalizedMoney(analysisAmount(magnitude.movePointLeft(2))))
                MasroofDivergingBarRow(AppDigits.display(spendingGroupName(change.group)), AppDigits.display(label),
                    MasroofAnalyticsChartMath.signedFraction(BigDecimal.valueOf(change.delta), maximum),
                    Modifier.testTag("analysis-change-${change.group.key}").clickable { onOpenGroup("change:${change.group.key}") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.analysis_increase_axis), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.analysis_decrease_axis), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable internal fun AnalysisMerchantCard(snapshot: AnalysisSnapshot, onOpenGroup: (String) -> Unit,
    onViewAll: (() -> Unit)? = null, showAll: Boolean = false) {
    val merchants = snapshot.insights?.merchants.orEmpty()
    MasroofCard {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofSectionHeader(stringResource(R.string.analysis_merchants), icon = MasroofIcons.merchant,
                onViewAll = onViewAll, viewAllLabel = stringResource(R.string.dashboard_merchants_view_all))
            Text(stringResource(R.string.analysis_merchants_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (merchants.isEmpty()) Text(stringResource(R.string.lab_analysis_no_merchants))
            (if (showAll) merchants else merchants.take(5)).forEach { group ->
                MasroofValueBarRow(AppDigits.display(group.name.orEmpty()), visualMoney(group.amount), fraction(group.amount, merchants.first().amount),
                    modifier = Modifier.testTag("analysis-merchant-${group.key}"),
                    subtitle = AppDigits.display(stringResource(R.string.analysis_transaction_count, group.ids.size)), onClick = { onOpenGroup(group.key) })
            }
        }
    }
}
