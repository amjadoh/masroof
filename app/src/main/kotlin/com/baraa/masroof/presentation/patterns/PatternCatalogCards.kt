package com.baraa.masroof.presentation.patterns

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.*
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text

@Composable
internal fun patternStatusName(pattern: PatternDefinitionView): String = stringResource(when {
    !pattern.enabled -> R.string.teaching_pattern_disabled
    pattern.status == PatternApprovalStatus.PREVIEW_ONLY -> R.string.teaching_pattern_preview
    else -> R.string.teaching_pattern_ready
})

@Composable
internal fun PatternDetailCard(
    bank: PatternBankView, pattern: PatternDefinitionView, busy: Boolean,
    onApprove: (() -> Unit)?, onToggle: () -> Unit, onRestore: (Int) -> Unit,
) {
    MasroofCard {
        Text(bank.displayName ?: bank.bankId, style = MaterialTheme.typography.bodySmall)
        Text(patternOperationName(pattern.label), style = MaterialTheme.typography.titleLarge)
        val explanation = stringResource(when (pattern.status) {
            PatternApprovalStatus.BUNDLED -> R.string.pattern_bundled_status
            PatternApprovalStatus.PREVIEW_ONLY -> R.string.pattern_preview_only
            PatternApprovalStatus.APPROVED -> R.string.pattern_financial_approved_status
        })
        MasroofGuidanceLabel(patternStatusName(pattern), explanation)
        Text(pattern.example)
        if (pattern.fields.isNotEmpty()) {
            MasroofSectionHeader(stringResource(R.string.teaching_step_fields))
            pattern.fields.forEach { Text(patternFieldName(it)) }
        }
        Text(stringResource(R.string.pattern_revision, patternOperationName(pattern.label), pattern.revision), style = MaterialTheme.typography.bodySmall)
        if (pattern.canApprove && onApprove != null) MasroofButton(onClick = onApprove,
            enabled = !busy && pattern.enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pattern_financial_approve)) }
        MasroofOutlinedButton(onClick = onToggle, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("pattern-toggle-${bank.bankId}-${pattern.id}")) {
            Text(stringResource(if (pattern.enabled) R.string.pattern_disable else R.string.pattern_enable))
        }
        pattern.priorRevisions.forEach { revision ->
            MasroofTextButton(onClick = { onRestore(revision) }, enabled = !busy,
                modifier = Modifier.testTag("pattern-restore-${bank.bankId}-${pattern.id}-$revision")) {
                Text(stringResource(R.string.pattern_restore_revision, revision))
            }
        }
    }
}

@Composable
internal fun PatternApplyResult(result: PendingPatternApplyReport, singleMessage: Boolean = false) {
    when {
        result.incomplete -> MasroofGuidanceLabel(stringResource(R.string.review_still_pending), stringResource(R.string.pattern_financial_incomplete), contentColor = MaterialTheme.colorScheme.error)
        singleMessage -> Text(stringResource(if (result.posted > 0) R.string.teaching_apply_posted else R.string.teaching_no_match))
        else -> Text(stringResource(R.string.pattern_apply_result, result.processed, result.posted,
            result.failed?.toString() ?: stringResource(R.string.pattern_failure_count_unknown)))
    }
    if (result.limited) MasroofGuidanceLabel(stringResource(R.string.pattern_pending_title), stringResource(R.string.pattern_pending_limited))
}

@Composable
internal fun PatternPendingCard(
    report: PendingPatternReport?, busy: Boolean, applied: PendingPatternApplyReport?,
    onRefresh: () -> Unit, onApply: (() -> Unit)?, onOpenReview: ((String) -> Unit)?,
) {
    MasroofCard {
        MasroofGuidanceLabel(stringResource(R.string.pattern_pending_title), stringResource(R.string.teaching_pending_help),
            style = MaterialTheme.typography.titleMedium)
        MasroofOutlinedButton(onClick = onRefresh, enabled = !busy) { Text(stringResource(R.string.pattern_pending_refresh)) }
        if (onApply != null) MasroofButton(onClick = onApply, enabled = !busy) { Text(stringResource(R.string.pattern_apply_pending)) }
        applied?.let { PatternApplyResult(it) }
        if (report != null) {
            val statusNames = stringArrayResource(R.array.pattern_pending_statuses)
            PendingPatternStatus.entries.forEach { status -> Text("${statusNames[status.ordinal]}: ${report.rows.count { it.status == status }}") }
            if (report.limited || report.rows.size > 50) MasroofGuidanceLabel(stringResource(R.string.pattern_pending_title), stringResource(R.string.pattern_pending_limited))
            report.rows.take(50).forEachIndexed { index, row ->
                Text(stringResource(R.string.pattern_pending_row, index + 1, statusNames[row.status.ordinal]), style = MaterialTheme.typography.titleSmall)
                if (onOpenReview != null) MasroofTextButton(onClick = { onOpenReview(row.reviewId) }, enabled = !busy) {
                    Text(stringResource(R.string.pattern_pending_open))
                }
                row.matches.forEach { match ->
                    Text(stringResource(R.string.pattern_revision, patternOperationName(match.operation), match.revision))
                    match.fields.forEach { (key, value) -> Text("${patternFieldName(key)}: ${value.text}") }
                }
            }
        }
    }
}
