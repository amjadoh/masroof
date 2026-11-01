package com.baraa.masroof.presentation.patterns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.*
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.settings.SettingsNavRow
import com.baraa.masroof.ui.theme.MasroofIconSizes
import com.baraa.masroof.ui.theme.MasroofSpacing

@Composable
internal fun patternStatusName(pattern: PatternDefinitionView): String = stringResource(when {
    !pattern.enabled -> R.string.teaching_pattern_disabled
    pattern.status == PatternApprovalStatus.PREVIEW_ONLY -> R.string.pattern_approval_waiting
    else -> R.string.pattern_approval_ready
})

/** Approval, on/off and conflict stay separate so each reads at a glance. */
@Composable
internal fun patternBadges(pattern: PatternDefinitionView, conflicting: Boolean): List<Pair<String, MasroofCardAccent>> = listOfNotNull(
    when (pattern.status) {
        PatternApprovalStatus.BUNDLED -> stringResource(R.string.pattern_badge_builtin) to MasroofCardAccent.None
        PatternApprovalStatus.PREVIEW_ONLY -> stringResource(R.string.pattern_badge_awaiting) to MasroofCardAccent.Liability
        PatternApprovalStatus.APPROVED -> stringResource(R.string.pattern_badge_approved) to MasroofCardAccent.Account
    },
    if (pattern.enabled) stringResource(R.string.pattern_badge_active) to MasroofCardAccent.Inflow
        else stringResource(R.string.teaching_pattern_disabled) to MasroofCardAccent.None,
    if (conflicting) stringResource(R.string.pattern_badge_conflict) to MasroofCardAccent.Outflow else null,
)

@Composable
internal fun PatternDetailCard(
    bank: PatternBankView, pattern: PatternDefinitionView, busy: Boolean,
    onApprove: (() -> Unit)?, onToggle: () -> Unit, onHistory: () -> Unit,
    onEdit: (() -> Unit)?, onDelete: () -> Unit, onCopy: (() -> Unit)? = null,
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
    }
    val builtInRule = pattern.recognitionPhrases.isNotEmpty()
    MasroofCard {
        MasroofSectionHeader(stringResource(if (builtInRule) R.string.pattern_builtin_phrases else R.string.pattern_template_preview),
            icon = MasroofIcons.preview)
        PatternTemplatePreview(pattern)
    }
    // A built-in rule has no template to select from; its fields are shown read-only and Edit teaches a replacement.
    if (builtInRule && pattern.fieldLabels.isNotEmpty()) MasroofCard(Modifier.testTag("pattern-builtin-fields")) {
        MasroofSectionHeader(stringResource(R.string.pattern_message_fields), icon = MasroofIcons.edit)
        Text(stringResource(R.string.pattern_builtin_fields_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        pattern.fieldLabels.forEach { (field, labels) ->
            Text("${patternFieldName(field)}: ${labels.joinToString("، ")}", style = MaterialTheme.typography.bodyMedium)
        }
        if (onEdit != null) {
            MasroofOutlinedButton(onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("pattern-edit")) {
                Text(stringResource(R.string.pattern_builtin_edit))
            }
            Text(stringResource(R.string.pattern_builtin_edit_help), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (onEdit != null) SettingsNavRow(
        icon = MasroofIcons.edit, title = stringResource(R.string.pattern_message_fields),
        subtitle = stringResource(R.string.pattern_message_fields_help), onClick = onEdit,
        enabled = !busy, modifier = Modifier.testTag("pattern-edit"),
    )
    if (pattern.canApprove && onApprove != null) {
        // Every built-in carries its rule's contract; only a saved account or card link makes it approved.
        val linked = pattern.approval?.accountLast4 != null || pattern.approval?.cardLast4 != null
        if (pattern.status == PatternApprovalStatus.APPROVED || builtInRule && linked) SettingsNavRow(
            icon = MasroofIcons.success, title = stringResource(R.string.pattern_financial_settings),
            subtitle = stringResource(R.string.pattern_financial_settings_help), onClick = onApprove,
            enabled = !busy,
        ) else MasroofButton(onClick = onApprove, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pattern_financial_approve))
        }
    }
    if (onCopy != null) MasroofOutlinedButton(onCopy, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("pattern-copy")) {
        Text(stringResource(R.string.pattern_copy))
    }
    SettingsNavRow(
        icon = MasroofIcons.clock, title = stringResource(R.string.pattern_approval_advanced),
        subtitle = stringResource(R.string.pattern_history_help), onClick = onHistory,
        enabled = !busy, modifier = Modifier.testTag("pattern-advanced"),
    )
    MasroofOutlinedButton(onDelete, enabled = !busy && !pattern.enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().testTag("pattern-delete")) {
        Icon(MasroofIcons.delete, null, Modifier.size(MasroofIconSizes.md))
        Spacer(Modifier.width(MasroofSpacing.inlineGap))
        Text(stringResource(R.string.pattern_delete_action))
    }
}

/** Read-only, always expanded; shared by details and the catalog's quick preview. */
@Composable
internal fun PatternTemplatePreview(pattern: PatternDefinitionView) {
    Column(Modifier.fillMaxWidth().testTag("pattern-preview"), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
        if (pattern.recognitionPhrases.isNotEmpty()) {
            Text(stringResource(R.string.pattern_builtin_phrases_help), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Column { pattern.recognitionPhrases.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) } } }
            return@Column
        }
        Text(stringResource(R.string.pattern_template_preview_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        var template = pattern.messageTemplate
        pattern.fields.forEach { field -> template = template.replace("{$field}", "[${patternFieldName(field)}]") }
        SelectionContainer { Text(template, style = MaterialTheme.typography.bodyMedium) }
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
