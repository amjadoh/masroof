package com.baraa.masroof.presentation.patterns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.*
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PatternFinancialApprovalScreen(workflow: MessagePatternsWorkflow, generation: Long, pattern: PatternDefinitionView,
    initialBody: String, readOnlyBody: Boolean, onSaved: (PatternCatalog) -> Unit, onBack: () -> Unit) {
    var accounts by remember { mutableStateOf(emptyList<PatternAccountOption>()) }
    var accountId by remember { mutableStateOf<String?>(null) }
    var newAccount by remember { mutableStateOf("") }
    var ownershipConfirmed by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf(PatternOperation.entries.firstOrNull { it.name == pattern.label }) }
    var body by remember { mutableStateOf(initialBody) }
    var reference by remember { mutableStateOf<String?>(null) }
    var contextFields by remember { mutableStateOf(emptySet<String>()) }
    var currency by remember { mutableStateOf<Currency?>(null) }
    var preview by remember { mutableStateOf<PatternFinancialPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val operationNames = stringArrayResource(R.array.pattern_operations)
    val fieldKeys = stringArrayResource(R.array.pattern_field_keys)
    val fieldNames = stringArrayResource(R.array.pattern_field_names)
    fun fieldName(key: String) = fieldNames.getOrNull(fieldKeys.indexOf(key)) ?: key
    val references = pattern.referenceFields
    val bindingOptions = pattern.accountReferenceFields(operation)
    fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    LaunchedEffect(workflow, pattern.bankId) {
        try { accounts = workflow.patternAccounts(pattern.bankId) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    BackHandler { if (!busy) onBack() }
    MasroofSecondaryScaffold(stringResource(R.string.pattern_financial_approve), { if (!busy) onBack() }, stringResource(R.string.settings_back)) { modifier ->
        Column(modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofGuidanceLabel(stringResource(R.string.pattern_financial_approve), stringResource(R.string.pattern_financial_explanation))
            if (busy) CircularProgressIndicator()
            if (failed) MasroofGuidanceLabel(stringResource(R.string.review_action_failed), stringResource(R.string.pattern_financial_blocked), contentColor = MaterialTheme.colorScheme.error)
            MasroofCard {
                val select = stringResource(R.string.pattern_financial_select_operation)
                PatternChoice(listOf(select) + operationNames, operation?.let { operationNames[it.ordinal] } ?: select, !busy) {
                    operation = PatternOperation.entries.getOrNull(it - 1); reference = null; contextFields = emptySet(); preview = null
                }
                val chooseAccount = stringResource(R.string.pattern_financial_select_account)
                val names = accounts.map { "${it.name.orEmpty()} • ${it.last4}" }
                PatternChoice(listOf(chooseAccount) + names, accounts.indexOfFirst { it.id == accountId }.takeIf { it >= 0 }?.let { names[it] } ?: chooseAccount, !busy) {
                    accountId = accounts.getOrNull(it - 1)?.id; preview = null
                }
                MasroofGuidanceButton(stringResource(R.string.pattern_financial_select_account), stringResource(R.string.pattern_financial_account_help))
                OutlinedTextField(newAccount, { newAccount = it.filter(Char::isDigit).take(4); ownershipConfirmed = false },
                    label = { Text(stringResource(R.string.pattern_financial_account_last4)) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("pattern-account-last4"))
                Row(Modifier.fillMaxWidth().toggleable(ownershipConfirmed, enabled = !busy, role = Role.Checkbox) { ownershipConfirmed = it }) {
                    Checkbox(ownershipConfirmed, null, enabled = !busy)
                    Text(stringResource(R.string.pattern_financial_account_owned))
                }
                MasroofOutlinedButton(onClick = { work {
                    val account = workflow.confirmPatternAccount(pattern.bankId, newAccount)
                    accounts = workflow.patternAccounts(pattern.bankId); accountId = account.id; preview = null; newAccount = ""; ownershipConfirmed = false
                } }, enabled = !busy && ownershipConfirmed && newAccount.length == 4) { Text(stringResource(R.string.pattern_financial_confirm_account)) }
            }
            MasroofCard {
                OutlinedTextField(body, { body = it; preview = null }, label = { Text(stringResource(R.string.pattern_message)) },
                    enabled = !busy, readOnly = readOnlyBody, modifier = Modifier.fillMaxWidth().testTag("pattern-financial-body"))
                val fromMessage = stringResource(R.string.pattern_financial_currency_from_message)
                PatternChoice(listOf(fromMessage) + Currency.entries.map { it.name }, currency?.name ?: fromMessage, !busy) {
                    currency = Currency.entries.getOrNull(it - 1); preview = null
                }
                MasroofGuidanceButton(stringResource(R.string.pattern_financial_fixed_account), stringResource(R.string.pattern_financial_reference_help))
                val constant = stringResource(R.string.pattern_financial_fixed_account)
                PatternChoice(listOf(constant) + bindingOptions.map(::fieldName), reference?.let(::fieldName) ?: constant, !busy) {
                    reference = bindingOptions.getOrNull(it - 1); contextFields = contextFields - setOfNotNull(reference); preview = null
                }
                references.filter { it != reference }.forEach { field ->
                    Row(Modifier.fillMaxWidth().toggleable(field in contextFields, enabled = !busy, role = Role.Checkbox) { checked ->
                        contextFields = if (checked) contextFields + field else contextFields - field; preview = null
                    }) {
                        Checkbox(field in contextFields, null, enabled = !busy)
                        Text(stringResource(R.string.pattern_financial_context_reference, fieldName(field)))
                    }
                }
                MasroofButton(onClick = { work {
                    preview = workflow.previewFinancial(generation, pattern.bankId, pattern.id, requireNotNull(operation), requireNotNull(accountId),
                        body, reference, contextFields, currency)
                } }, enabled = !busy && operation != null && accountId != null && body.isNotBlank()) { Text(stringResource(R.string.pattern_financial_validate)) }
            }
            preview?.let { validated ->
                MasroofCard {
                    MasroofAmountText("${validated.amount.amount.toPlainString()} ${validated.amount.currency}", MasroofAmountRole.Card)
                    Text(validated.occurredAt)
                    validated.reference?.let { Text(stringResource(R.string.pattern_financial_mapping, it, accounts.single { a -> a.id == accountId }.last4)) }
                    MasroofGuidanceLabel(stringResource(R.string.pattern_financial_approve), stringResource(R.string.pattern_financial_save_help))
                    MasroofButton(onClick = { work { workflow.saveFinancial(validated); onSaved(workflow.catalog()) } }, enabled = !busy,
                        modifier = Modifier.testTag("pattern-financial-save")) { Text(stringResource(R.string.pattern_financial_approve)) }
                }
            }
        }
    }
}
