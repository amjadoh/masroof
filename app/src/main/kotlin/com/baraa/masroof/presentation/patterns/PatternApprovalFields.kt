package com.baraa.masroof.presentation.patterns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.*
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofSpacing

@Composable
internal fun PatternApprovalMessage(examples: List<PatternApprovalExample>, selected: Int?, body: String,
    readOnly: Boolean, enabled: Boolean, onSelect: (Int) -> Unit, onManual: () -> Unit, onBody: (String) -> Unit) {
    MasroofCard {
        MasroofSectionHeader(stringResource(if (selected != null || readOnly) R.string.pattern_approval_imported_message else R.string.pattern_message))
        if (examples.isNotEmpty() && !readOnly) {
            val names = examples.mapIndexed { index, example -> stringResource(R.string.pattern_approval_example,
                index + 1, example.fields["amount"]?.text.orEmpty()) }
            PatternChoice(names, selected?.let { names[it] } ?: stringResource(R.string.pattern_approval_choose_imported), enabled, onSelect)
        }
        if (selected != null || readOnly) {
            examples.getOrNull(selected ?: -1)?.fields?.get("amount")?.let { Text(it.text, style = MaterialTheme.typography.titleLarge) }
            OutlinedTextField(body, {}, readOnly = true, minLines = 3, maxLines = 8,
                label = { Text(stringResource(R.string.pattern_message)) }, modifier = Modifier.fillMaxWidth().testTag("pattern-financial-body"))
            if (!readOnly) MasroofOutlinedButton(onManual, enabled = enabled) { Text(stringResource(R.string.pattern_approval_manual_message)) }
        } else OutlinedTextField(body, onBody, label = { Text(stringResource(R.string.pattern_message)) },
            enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("pattern-financial-body"))
    }
}

@Composable
internal fun PatternApprovalAccounts(accounts: List<PatternAccountOption>, selectedId: String?, enabled: Boolean,
    onChoose: (String?) -> Unit, onConfirm: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var last4 by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var priorAccountId by remember { mutableStateOf<String?>(null) }
    val addLabel = stringResource(R.string.pattern_approval_add_account)
    val cancelLabel = stringResource(R.string.pattern_approval_cancel_account)
    val numberLabel = stringResource(R.string.pattern_financial_account_last4)
    val ownedLabel = stringResource(R.string.pattern_financial_account_owned)
    val confirmLabel = stringResource(R.string.pattern_financial_confirm_account)
    fun cancelAddition() {
        if (!enabled) return
        adding = false; last4 = ""; confirmed = false; onChoose(priorAccountId)
    }
    @Composable fun fields() {
        OutlinedTextField(last4, { last4 = it.filter(Char::isDigit).take(4); confirmed = false },
            label = { Text(numberLabel) }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("pattern-account-last4"))
        Row(Modifier.fillMaxWidth().toggleable(confirmed, enabled = enabled, role = Role.Checkbox) { confirmed = it },
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(confirmed, null, enabled = enabled)
            Text(ownedLabel, modifier = Modifier.weight(1f))
        }
    }
    LaunchedEffect(selectedId) { if (selectedId != null) { adding = false; last4 = ""; confirmed = false } }
    Text(stringResource(R.string.pattern_approval_account_question), style = MaterialTheme.typography.titleMedium)
    val names = accounts.map { "${it.name.orEmpty()} • ${it.last4}" }
    val choose = stringResource(R.string.pattern_financial_select_account)
    if (accounts.size == 1 && accounts.single().id == selectedId) {
        MasroofDetailRow(stringResource(R.string.pattern_approval_confirmed_account), names.single())
    } else if (accounts.isNotEmpty()) PatternChoice(listOf(choose) + names,
        accounts.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }?.let { names[it] } ?: choose, enabled) {
        onChoose(accounts.getOrNull(it - 1)?.id)
    }
    if (accounts.isNotEmpty()) MasroofOutlinedButton({ priorAccountId = selectedId; adding = true; onChoose(null) }, enabled = enabled) { Text(addLabel) }
    if (accounts.isEmpty()) {
        fields()
        MasroofOutlinedButton({ onConfirm(last4) }, enabled = enabled && confirmed && last4.length == 4) {
            Text(confirmLabel)
        }
    }
    if (adding) AlertDialog(onDismissRequest = ::cancelAddition, title = { Text(addLabel) },
        text = { Column { fields() } }, confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
                MasroofOutlinedButton(::cancelAddition, enabled = enabled, modifier = Modifier.weight(1f)) { Text(cancelLabel) }
                MasroofButton({ onConfirm(last4) }, enabled = enabled && confirmed && last4.length == 4, modifier = Modifier.weight(1f)) { Text(confirmLabel) }
            }
        })
}

@Composable
internal fun PatternApprovalReferences(pattern: PatternDefinitionView, operation: PatternOperation?, reference: String?,
    chosen: Boolean, enabled: Boolean, fields: Map<String, PatternText>, onChoose: (String?) -> Unit) {
    MasroofCard {
        Text(stringResource(R.string.pattern_approval_reference_question), style = MaterialTheme.typography.titleMedium)
        fields.filterKeys { it in pattern.referenceFields }.forEach { (key, value) ->
            MasroofDetailRow(patternFieldName(key), value.text)
        }
        PatternApprovalRadio(stringResource(R.string.pattern_approval_other_reference),
            stringResource(R.string.pattern_approval_other_reference_help), chosen && reference == null, enabled,
            "pattern-reference-context") { onChoose(null) }
        pattern.accountReferenceFields(operation).forEach { field ->
            PatternApprovalRadio(stringResource(R.string.pattern_approval_own_reference, patternFieldName(field)),
                stringResource(R.string.pattern_approval_own_reference_help), chosen && reference == field, enabled,
                "pattern-reference-own-$field") { onChoose(field) }
        }
    }
}

@Composable
private fun PatternApprovalRadio(title: String, description: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MasroofSpacing.minimumTouchTarget)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null, enabled = enabled)
        Column(Modifier.weight(1f).padding(vertical = MasroofSpacing.smallGap)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun patternApprovalProblemText(problem: PatternApprovalProblem): Int = when (problem) {
    PatternApprovalProblem.MESSAGE_MISMATCH -> R.string.pattern_approval_error_message
    PatternApprovalProblem.ACCOUNT_REQUIRED -> R.string.pattern_approval_need_account
    PatternApprovalProblem.CARD_MISMATCH -> R.string.pattern_approval_error_card
    PatternApprovalProblem.REFERENCE_ROLE -> R.string.pattern_approval_need_reference
    PatternApprovalProblem.REFERENCE_MISMATCH -> R.string.pattern_approval_error_reference
    PatternApprovalProblem.CURRENCY_REQUIRED -> R.string.pattern_approval_error_currency
    PatternApprovalProblem.CURRENCY_CONFLICT -> R.string.pattern_approval_error_currency_conflict
    PatternApprovalProblem.INVALID_AMOUNT -> R.string.pattern_approval_error_amount
    PatternApprovalProblem.INVALID_TIME -> R.string.pattern_approval_error_time
    PatternApprovalProblem.PATTERN_CONFLICT -> R.string.pattern_approval_error_conflict
    PatternApprovalProblem.NON_FINANCIAL -> R.string.pattern_approval_error_non_financial
    PatternApprovalProblem.CONFIGURATION_CHANGED -> R.string.pattern_approval_error_changed
    PatternApprovalProblem.VALIDATION -> R.string.pattern_financial_blocked
}
