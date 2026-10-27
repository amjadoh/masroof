package com.baraa.masroof.presentation.patterns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.*
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One page: imported example, confirmed account, explicit reference meaning, then approval. */
@Composable
internal fun PatternFinancialApprovalScreen(workflow: MessagePatternsWorkflow, generation: Long, pattern: PatternDefinitionView,
    initialBody: String, readOnlyBody: Boolean, onSaved: (PatternCatalog) -> Unit, onBack: () -> Unit) {
    var accounts by remember { mutableStateOf(emptyList<PatternAccountOption>()) }
    var cards by remember { mutableStateOf(emptyList<PatternAccountOption>()) }
    var examples by remember { mutableStateOf(emptyList<PatternApprovalExample>()) }
    var exampleIndex by remember { mutableStateOf<Int?>(null) }
    var cardId by remember { mutableStateOf<String?>(null) }
    var accountId by remember { mutableStateOf<String?>(null) }
    var operation by remember { mutableStateOf(pattern.approval?.operation ?: PatternOperation.entries.firstOrNull { it.name == pattern.label }) }
    var body by remember { mutableStateOf(initialBody) }
    var reference by remember { mutableStateOf(pattern.approval?.referenceField) }
    var referenceChosen by remember { mutableStateOf(pattern.referenceFields.isEmpty() || pattern.approval != null) }
    var currency by remember { mutableStateOf(pattern.approval?.currency) }
    var preview by remember { mutableStateOf<PatternFinancialPreview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var validating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<PatternApprovalProblem?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val operationNames = stringArrayResource(R.array.pattern_operations)
    val contextFields = if (referenceChosen) pattern.referenceFields.toSet() - setOfNotNull(reference) else emptySet()
    val ready = !loading && operation != null && (accountId != null || cardId != null) &&
        ("card" !in pattern.fields || cardId != null) && referenceChosen && body.isNotBlank()
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
        try {
            accounts = workflow.patternAccounts(pattern.bankId)
            cards = workflow.patternCards(pattern.bankId)
            accountId = accounts.singleOrNull { it.last4 == pattern.approval?.accountLast4 }?.id
                ?: accounts.singleOrNull()?.takeIf { "card" !in pattern.fields }?.id
            if ("card" in pattern.fields) cardId = cards.singleOrNull()?.id
            if (!readOnlyBody) {
                examples = workflow.approvalExamples(pattern.bankId, generation, pattern.id)
                if (body.isBlank() && examples.isNotEmpty()) { exampleIndex = 0; body = examples.first().body }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    // Validation is read-only. Every edit invalidates the preview before Save can be used.
    LaunchedEffect(ready, body, operation, accountId, cardId, reference, referenceChosen, currency) {
        preview = null; problem = null
        if (ready) {
            validating = true
            try { preview = workflow.previewFinancial(generation, pattern.bankId, pattern.id, requireNotNull(operation), accountId,
                body, reference, contextFields, currency, cardId) }
            catch (e: CancellationException) { throw e }
            catch (e: PatternApprovalException) { problem = e.problem }
            catch (_: Exception) { problem = PatternApprovalProblem.VALIDATION }
            finally { validating = false }
        }
    }
    BackHandler { if (!busy) onBack() }
    MasroofSecondaryScaffold(stringResource(R.string.pattern_approval_title), { if (!busy) onBack() }, stringResource(R.string.settings_back)) { modifier ->
        Column(modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            if (loading || busy) CircularProgressIndicator()
            if (failed) {
                Text(stringResource(R.string.pattern_financial_blocked), color = MaterialTheme.colorScheme.error)
                MasroofTextButton({ work {
                    accounts = workflow.patternAccounts(pattern.bankId)
                    cards = workflow.patternCards(pattern.bankId)
                    examples = workflow.approvalExamples(pattern.bankId, generation, pattern.id)
                    if (body.isBlank() && examples.isNotEmpty()) { exampleIndex = 0; body = examples.first().body }
                } }) { Text(stringResource(R.string.dashboard_retry)) }
            }
            PatternApprovalMessage(examples, exampleIndex, body, readOnlyBody, !busy && !loading,
                onSelect = { index -> exampleIndex = index; body = examples[index].body; preview = null },
                onManual = { exampleIndex = null; body = ""; preview = null },
                onBody = { body = it; preview = null })
            MasroofCard {
                val select = stringResource(R.string.pattern_financial_select_operation)
                PatternChoice(listOf(select) + operationNames, operation?.let { operationNames[it.ordinal] } ?: select, !busy && !loading) {
                    operation = PatternOperation.entries.getOrNull(it - 1); reference = null
                    referenceChosen = pattern.referenceFields.isEmpty(); preview = null
                }
                PatternApprovalAccounts(accounts, accountId, !busy && !loading,
                    onChoose = { accountId = it; preview = null }, onConfirm = { last4 -> work {
                        val account = workflow.confirmPatternAccount(pattern.bankId, last4)
                        accounts = workflow.patternAccounts(pattern.bankId); accountId = account.id; preview = null
                    } })
                if ("card" in pattern.fields) {
                    val choose = stringResource(R.string.settings_pattern_select_card)
                    val names = cards.map { "${it.name.orEmpty()} • ${it.last4}" }
                    PatternChoice(listOf(choose) + names, cards.indexOfFirst { it.id == cardId }.takeIf { it >= 0 }?.let { names[it] } ?: choose, !busy) {
                        cardId = cards.getOrNull(it - 1)?.id; preview = null
                    }
                    if (cards.isEmpty()) Text(stringResource(R.string.settings_pattern_add_card_hint))
                }
            }
            if (pattern.referenceFields.isNotEmpty()) PatternApprovalReferences(pattern, operation, reference, referenceChosen,
                !busy && !loading, examples.getOrNull(exampleIndex ?: -1)?.fields.orEmpty()) {
                reference = it; referenceChosen = true; preview = null
            }
            MasroofCard {
                val fromMessage = stringResource(R.string.pattern_financial_currency_from_message)
                PatternChoice(listOf(fromMessage) + Currency.entries.map { it.name }, currency?.name ?: fromMessage, !busy && !loading) {
                    currency = Currency.entries.getOrNull(it - 1); preview = null
                }
                if (validating) CircularProgressIndicator()
                problem?.let { Text(stringResource(patternApprovalProblemText(it)), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("pattern-approval-error")) }
                preview?.let { validated ->
                    MasroofAmountText("${validated.amount.amount.toPlainString()} ${validated.amount.currency}", MasroofAmountRole.Card)
                    Text(validated.occurredAt)
                    accounts.singleOrNull { it.id == accountId }?.let { Text(stringResource(R.string.pattern_approval_account_summary, it.last4)) }
                    Text(stringResource(R.string.pattern_approval_save_scope), style = MaterialTheme.typography.bodySmall)
                }
                val reason = when {
                    loading -> null
                    body.isBlank() -> R.string.pattern_approval_need_message
                    accountId == null && cardId == null -> R.string.pattern_approval_need_account
                    !referenceChosen -> R.string.pattern_approval_need_reference
                    else -> null
                }
                reason?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                MasroofButton(onClick = { preview?.let { validated -> work {
                    workflow.saveFinancial(validated); onSaved(workflow.catalog())
                } } }, enabled = !busy && !validating && ready && preview != null,
                    modifier = Modifier.fillMaxWidth().testTag("pattern-financial-save")) { Text(stringResource(R.string.pattern_financial_approve)) }
            }
        }
    }
}
