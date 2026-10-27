package com.baraa.masroof.presentation.patterns

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.*
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofIconSizes
import com.baraa.masroof.ui.theme.MasroofSpacing

@Composable
internal fun patternStatusName(pattern: PatternDefinitionView): String = stringResource(when {
    !pattern.enabled -> R.string.teaching_pattern_disabled
    pattern.status == PatternApprovalStatus.PREVIEW_ONLY -> R.string.pattern_approval_waiting
    else -> R.string.pattern_approval_ready
})

@Composable
internal fun PatternDetailCard(
    bank: PatternBankView, pattern: PatternDefinitionView, busy: Boolean,
    onApprove: (() -> Unit)?, onToggle: () -> Unit, onHistory: () -> Unit,
    onEdit: (() -> Unit)?, onDelete: () -> Unit,
) {
    MasroofCard {
        val toggleLabel = stringResource(if (pattern.enabled) R.string.pattern_disable else R.string.pattern_enable)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bank.displayName ?: bank.bankId, style = MaterialTheme.typography.bodySmall)
                Text(patternOperationName(pattern.label), style = MaterialTheme.typography.titleLarge)
            }
            Switch(pattern.enabled, { onToggle() }, enabled = !busy,
                modifier = Modifier.testTag("pattern-toggle-${bank.bankId}-${pattern.id}").semantics { contentDescription = toggleLabel })
        }
        val explanation = stringResource(when (pattern.status) {
            PatternApprovalStatus.BUNDLED -> R.string.pattern_bundled_status
            PatternApprovalStatus.PREVIEW_ONLY -> R.string.pattern_preview_only
            PatternApprovalStatus.APPROVED -> R.string.pattern_financial_approved_status
        })
        MasroofGuidanceLabel(patternStatusName(pattern), explanation)
        if (pattern.canApprove && onApprove != null) {
            if (pattern.status == PatternApprovalStatus.APPROVED) MasroofOutlinedButton(onClick = onApprove,
                enabled = !busy && pattern.enabled) { Text(stringResource(R.string.pattern_approval_edit)) }
            else MasroofButton(onClick = onApprove, enabled = !busy && pattern.enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(MasroofIcons.success, null, Modifier.size(MasroofIconSizes.md))
                Spacer(Modifier.width(MasroofSpacing.inlineGap))
                Text(stringResource(R.string.pattern_financial_approve))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
            if (onEdit != null) MasroofOutlinedButton(onClick = onEdit, enabled = !busy,
                modifier = Modifier.weight(1f).testTag("pattern-edit")) {
                Icon(MasroofIcons.edit, null, Modifier.size(MasroofIconSizes.md))
                Spacer(Modifier.width(MasroofSpacing.inlineGap))
                Text(stringResource(R.string.pattern_edit))
            }
            MasroofOutlinedButton(onDelete, enabled = !busy && !pattern.enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f).testTag("pattern-delete")) {
                Icon(MasroofIcons.delete, null, Modifier.size(MasroofIconSizes.md))
                Spacer(Modifier.width(MasroofSpacing.inlineGap))
                Text(stringResource(R.string.pattern_delete))
            }
        }
        if (pattern.enabled) Text(stringResource(R.string.pattern_delete_disable_first), style = MaterialTheme.typography.bodySmall)
        MasroofOutlinedButton(onHistory, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("pattern-advanced")) {
            Icon(MasroofIcons.clock, null, Modifier.size(MasroofIconSizes.md))
            Spacer(Modifier.width(MasroofSpacing.inlineGap))
            Text(stringResource(R.string.pattern_approval_advanced))
        }
    }
}

@Composable
internal fun PatternApplyResult(result: PendingPatternApplyReport, singleMessage: Boolean = false) {
    if (result.incomplete) MasroofGuidanceLabel(stringResource(R.string.review_still_pending), stringResource(R.string.pattern_financial_incomplete), contentColor = MaterialTheme.colorScheme.error)
    if (singleMessage) Text(stringResource(if (result.posted > 0) R.string.teaching_apply_posted else R.string.teaching_no_match))
    Text(stringResource(R.string.pattern_apply_result, result.processed, result.posted,
        result.failed?.toString() ?: stringResource(R.string.pattern_failure_count_unknown)), modifier = Modifier.testTag("pattern-apply-result"))
    if (result.reviewsBefore != null && result.reviewsAfter != null) Text(stringResource(R.string.pattern_approval_review_change,
        result.reviewsBefore, result.reviewsAfter), modifier = Modifier.testTag("pattern-review-change"))
    if (result.posted > 0) Text(stringResource(R.string.pattern_approval_dashboard_scope), style = MaterialTheme.typography.bodySmall)
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
                if (onOpenReview != null) MasroofOutlinedButton(onClick = { onOpenReview(row.reviewId) }, enabled = !busy) {
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
