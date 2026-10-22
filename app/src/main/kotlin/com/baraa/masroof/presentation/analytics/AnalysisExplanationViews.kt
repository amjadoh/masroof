package com.baraa.masroof.presentation.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.analytics.*
import com.baraa.masroof.application.dashboard.AnalysisSpendingGroup
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.locale.formatLocalizedMoney

@Composable internal fun spendingGroupName(group: AnalysisSpendingGroup): String = group.name ?: stringResource(when (group.key) {
    "CASH" -> R.string.lab_analysis_component_cash
    "TRANSFERS" -> R.string.lab_analysis_component_transfers
    "REFUNDS" -> R.string.lab_analysis_component_refunds
    else -> R.string.lab_analysis_component_purchases
})

@Composable internal fun AnalysisSpendingExplanation(snapshot: AnalysisSnapshot, workflow: LabAnalyticsWorkflow, onOpenTransaction: (String) -> Unit) {
    val insights = snapshot.insights ?: return
    var expanded by rememberSaveable(snapshot.periodStart) { mutableStateOf(false) }
    MasroofCard(Modifier.testTag("analysis-spending-explanation")) {
        MasroofSectionHeader(stringResource(R.string.lab_analysis_insights), icon = MasroofIcons.analysis)
        insights.components.forEach { group ->
            MasroofMoneyRow(spendingGroupName(group), formatLocalizedMoney(analysisAmount(group.amount)))
        }
        val comparison = insights.comparison
        if (comparison == null) Text(stringResource(R.string.lab_analysis_comparison_missing), style = MaterialTheme.typography.bodySmall)
        else {
            Text(stringResource(R.string.lab_analysis_comparison_dates, day(comparison.from), day(comparison.through),
                day(comparison.previousFrom), day(comparison.previousThrough)), style = MaterialTheme.typography.bodySmall)
            MasroofMoneyRow(stringResource(R.string.lab_analysis_comparison_current), formatLocalizedMoney(analysisAmount(comparison.current)))
            MasroofMoneyRow(stringResource(R.string.lab_analysis_comparison_previous), formatLocalizedMoney(analysisAmount(comparison.previous)))
            Text(stringResource(R.string.lab_analysis_comparison_delta, formatLocalizedMoney(analysisAmount(comparison.delta))))
            comparison.percent?.let { Text(stringResource(R.string.lab_analysis_comparison_percent, it), style = MaterialTheme.typography.bodySmall) }
            comparison.changes.firstOrNull()?.let { change -> Text(stringResource(R.string.lab_analysis_change_value,
                spendingGroupName(change.group), formatLocalizedMoney(analysisAmount(change.delta))), style = MaterialTheme.typography.bodySmall) }
        }
        MasroofTextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("analysis-expand-insights")) {
            Text(stringResource(if (expanded) R.string.lab_analysis_hide_data else R.string.lab_analysis_change_evidence))
        }
        if (expanded) {
            insights.components.forEach { group ->
                EvidenceGroup(group.ids, "${spendingGroupName(group)} · ${formatLocalizedMoney(analysisAmount(group.amount))}",
                    workflow, snapshot.sourceHash, onOpenTransaction, tag = "analysis-component-${group.key}")
            }
            if (comparison != null && comparison.changes.isNotEmpty()) {
                MasroofGuidanceLabel(stringResource(R.string.lab_analysis_changes), stringResource(R.string.lab_analysis_changes_hint))
                comparison.changes.forEach { change ->
                    EvidenceGroup(change.group.ids + change.previousIds,
                        stringResource(R.string.lab_analysis_change_value, spendingGroupName(change.group), formatLocalizedMoney(analysisAmount(change.delta))),
                        workflow, snapshot.sourceHash, onOpenTransaction)
                }
            }
            Text(stringResource(R.string.lab_analysis_top_merchants), style = MaterialTheme.typography.titleSmall)
            if (insights.merchants.isEmpty()) Text(stringResource(R.string.lab_analysis_no_merchants), style = MaterialTheme.typography.bodySmall)
            insights.merchants.forEach { group ->
                EvidenceGroup(group.ids, "${group.name} · ${formatLocalizedMoney(analysisAmount(group.amount))}", workflow, snapshot.sourceHash, onOpenTransaction)
            }
        }
    }
}

@Composable internal fun AnalysisDataQuality(snapshot: AnalysisSnapshot, workflow: LabAnalyticsWorkflow, onOpenTransaction: (String) -> Unit,
    onOpenReview: () -> Unit, onRequestSmsPermission: () -> Unit, onRescan: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val quality = snapshot.quality
    MasroofCard(Modifier.testTag("analysis-data-quality")) {
        MasroofGuidanceLabel(stringResource(R.string.lab_analysis_quality_title), stringResource(R.string.lab_analysis_quality_hint),
            style = MaterialTheme.typography.titleSmall, contentColor = MaterialTheme.colorScheme.onSurface)
        MasroofGuidanceLabel(stringResource(when {
            quality.blocked -> R.string.lab_analysis_quality_warning
            snapshot.status != "READY" -> R.string.lab_analysis_quality_insufficient
            snapshot.method == "WEEKDAY_MEDIAN" -> R.string.lab_analysis_quality_initial
            else -> R.string.lab_analysis_quality_personal
        }), if (snapshot.status != "READY") stringResource(R.string.lab_analysis_need_data) else null,
            style = MaterialTheme.typography.titleSmall, contentColor = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.lab_analysis_quality_coverage, quality.coveredDays, snapshot.completedPeriods), style = MaterialTheme.typography.bodySmall)
        if (snapshot.status == "READY") Text(stringResource(when (snapshot.modelReason) {
            "PERSONAL_MODEL" -> R.string.lab_analysis_ready
            "NEEDS_EVALUATIONS" -> R.string.lab_analysis_quality_evaluation_reason
            "BASELINE_BETTER" -> R.string.lab_analysis_quality_baseline_reason
            "MODEL_UNAVAILABLE" -> R.string.lab_analysis_quality_model_retry
            "COVERAGE_GAPS" -> R.string.lab_analysis_quality_gaps
            "NEEDS_PERIODS" -> R.string.lab_analysis_quality_periods_reason
            "NEEDS_PURCHASE_DAYS" -> R.string.lab_analysis_quality_purchase_days_reason
            else -> R.string.lab_analysis_quality_history_reason
        }), style = MaterialTheme.typography.bodySmall)
        if (quality.pendingReviews > 0) MasroofTextButton(onClick = onOpenReview, modifier = Modifier.testTag("analysis-open-review")) {
            Text(stringResource(R.string.lab_analysis_quality_open_review) + " (${quality.pendingReviews})")
        } else Text(stringResource(R.string.lab_analysis_quality_no_reviews), style = MaterialTheme.typography.bodySmall)
        quality.reasons.forEach { reason ->
            when (reason) {
                "SMS_PERMISSION" -> {
                    Text(stringResource(R.string.lab_analysis_quality_permission), style = MaterialTheme.typography.bodySmall)
                    MasroofTextButton(onClick = onRequestSmsPermission) { Text(stringResource(R.string.lab_analysis_quality_permission_action)) }
                }
                "PROCESSING_RETRY" -> {
                    Text(stringResource(R.string.lab_analysis_quality_retry), style = MaterialTheme.typography.bodySmall)
                    MasroofTextButton(onClick = onRescan) { Text(stringResource(R.string.lab_analysis_quality_retry_action)) }
                }
                "IMPORT_PENDING", "IMPORT_RUNNING" -> {
                    Text(stringResource(R.string.lab_analysis_quality_import), style = MaterialTheme.typography.bodySmall)
                    if (reason == "IMPORT_PENDING") MasroofTextButton(onClick = onRescan) { Text(stringResource(R.string.lab_analysis_quality_retry_action)) }
                }
                "COVERAGE_GAPS" -> Text(stringResource(R.string.lab_analysis_quality_gaps), style = MaterialTheme.typography.bodySmall)
            }
        }
        // Missing money/FX requires attention. Scope and large amounts are explanations, not errors.
        quality.excluded.filterKeys { it !in setOf("OUTSIDE_PURCHASE_SCOPE", "DUPLICATE_ID") }.forEach { (reason, ids) ->
            EvidenceGroup(ids, stringResource(if (reason == "FUTURE_DATE") R.string.lab_analysis_excluded_future else R.string.lab_analysis_excluded_amount),
                workflow, snapshot.sourceHash, onOpenTransaction)
        }
        if (quality.unusual.isNotEmpty()) EvidenceGroup(quality.unusual, stringResource(R.string.lab_analysis_unusual), workflow, snapshot.sourceHash, onOpenTransaction)
        quality.excluded["OUTSIDE_PURCHASE_SCOPE"]?.let { EvidenceGroup(it, stringResource(R.string.lab_analysis_excluded_scope), workflow, snapshot.sourceHash, onOpenTransaction) }
        MasroofTextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.lab_analysis_hide_data else R.string.lab_analysis_show_data)) }
        if (expanded) {
            MasroofGuidanceLabel(
                stringResource(R.string.lab_analysis_quality_counts, quality.used, quality.coveredDays, snapshot.completedPeriods, quality.pendingReviews),
                stringResource(R.string.lab_analysis_quality_counts_hint), style = MaterialTheme.typography.bodySmall)
            MasroofGuidanceLabel(stringResource(R.string.lab_analysis_scope_title), stringResource(R.string.lab_analysis_scope))
            if (quality.historyFrom != null && quality.historyThrough != null) Text(stringResource(R.string.lab_analysis_history_range,
                day(quality.historyFrom), day(quality.historyThrough)), style = MaterialTheme.typography.bodySmall)
            if (!quality.coverageKnown) Text(stringResource(R.string.lab_analysis_coverage_unknown), style = MaterialTheme.typography.bodySmall)
            if (snapshot.trainingFrom != null && snapshot.trainingThrough != null) MasroofGuidanceLabel(
                stringResource(R.string.lab_analysis_training_range, day(snapshot.trainingFrom), day(snapshot.trainingThrough)), stringResource(R.string.lab_analysis_training_hint))
            quality.excluded["DUPLICATE_ID"]?.let { EvidenceGroup(it, stringResource(R.string.lab_analysis_excluded_duplicate), workflow, snapshot.sourceHash, onOpenTransaction) }
            MasroofGuidanceLabel(stringResource(R.string.lab_analysis_evaluation), stringResource(R.string.lab_analysis_evaluation_hint))
            if (snapshot.evaluations.isEmpty()) Text(stringResource(R.string.lab_analysis_evaluation_missing), style = MaterialTheme.typography.bodySmall)
            snapshot.evaluations.forEach { e -> Text(stringResource(R.string.lab_analysis_evaluation_row, e.horizon, e.count,
                formatLocalizedMoney(analysisAmount(e.mae)), formatLocalizedMoney(analysisAmount(e.bias))), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable internal fun PublishedForecastHistory(state: AnalysisUiState) {
    if (state.history.isEmpty()) return
    MasroofCard {
        MasroofGuidanceLabel(stringResource(R.string.lab_analysis_history), stringResource(R.string.lab_analysis_history_hint),
            style = MaterialTheme.typography.titleSmall, contentColor = MaterialTheme.colorScheme.onSurface)
        state.forecastHistoryGroups().take(8).forEach { group ->
            val past = group.snapshots.first()
            var expanded by rememberSaveable(past.issuedAt) { mutableStateOf(false) }
            Text(timestamp(past.issuedAt), style = MaterialTheme.typography.bodySmall)
            past.total?.let { Amount(stringResource(R.string.lab_analysis_expected_total), it) }
            state.historyActuals[past.periodStart]?.let { Amount(stringResource(R.string.lab_analysis_final_actual), it) }
            if (group.snapshots.size > 1) {
                Text(stringResource(R.string.lab_analysis_history_group, group.snapshots.size), style = MaterialTheme.typography.bodySmall)
                MasroofTextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.lab_analysis_history_collapse else R.string.lab_analysis_history_expand)) }
                if (expanded) group.snapshots.forEach { Text(timestamp(it.issuedAt), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
