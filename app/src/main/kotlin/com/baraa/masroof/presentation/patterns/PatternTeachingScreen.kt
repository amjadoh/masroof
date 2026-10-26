package com.baraa.masroof.presentation.patterns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.*
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** All choices are local until the exact generated preview is explicitly saved. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PatternTeachingScreen(
    workflow: MessagePatternsWorkflow, generation: Long, bank: PatternBankView,
    source: PatternTeachingSource?, onSaved: (PatternTeachingPreview, String) -> Unit, onBack: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var operation by remember { mutableStateOf(PatternOperation.PURCHASE) }
    var sender by remember { mutableStateOf(source?.sender ?: bank.senders.first()) }
    var body by remember { mutableStateOf(TextFieldValue(source?.body.orEmpty())) }
    var fieldIndex by remember { mutableIntStateOf(0) }
    var bindings by remember { mutableStateOf(emptyList<PatternBinding>()) }
    var candidates by remember { mutableStateOf(emptyList<PatternText>()) }
    var preview by remember { mutableStateOf<PatternTeachingPreview?>(null) }
    var sample by remember { mutableStateOf("") }
    var sampleResult by remember { mutableStateOf<Map<String, PatternText>?>(null) }
    var sampleChecked by remember { mutableStateOf(false) }
    var showSample by remember { mutableStateOf(false) }
    var showIgnored by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val operations = stringArrayResource(R.array.pattern_operations)
    val field = operation.fields[fieldIndex]
    fun back() { if (!busy) { if (step > 0) { step--; preview = null } else onBack() } }
    fun work(action: suspend () -> Unit) {
        if (busy) return
        keyboard?.hide(); focus.clearFocus()
        busy = true; failed = false
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    fun buildPreview() = work {
        preview = workflow.teach(generation, bank.bankId, sender, operation, body.text, bindings)
        sampleChecked = false; sampleResult = null; step = 2
    }
    fun bindSelection(key: String, ignored: Boolean = false) {
        val start = body.selection.min
        val end = body.selection.max
        bindings = bindings.filterNot { it.field == key || it.start < end && start < it.end } + PatternBinding(key, start, end, ignored)
        preview = null
        body = body.copy(selection = TextRange.Zero)
    }
    LaunchedEffect(body.text) {
        try { candidates = workflow.candidates(body.text) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    BackHandler { back() }
    MasroofSecondaryScaffold(stringResource(R.string.teaching_title), ::back, stringResource(R.string.review_back)) { modifier ->
        Column(modifier.imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
                listOf(R.string.teaching_step_kind, R.string.teaching_step_fields, R.string.teaching_step_confirm).forEachIndexed { index, label ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
                        LinearProgressIndicator(progress = { if (index <= step) 1f else 0f }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (busy) CircularProgressIndicator()
            if (failed) MasroofGuidanceLabel(stringResource(R.string.review_action_failed), stringResource(R.string.pattern_failed), contentColor = MaterialTheme.colorScheme.error)
            when (step) {
                0 -> {
                    MasroofGuidanceLabel(stringResource(R.string.teaching_kind_title), stringResource(R.string.teaching_choose_message_help),
                        style = MaterialTheme.typography.titleLarge, contentColor = MaterialTheme.colorScheme.onSurface)
                    Text(bank.displayName ?: bank.bankId, style = MaterialTheme.typography.titleMedium)
                    if (bank.senders.size > 1) PatternChoice(bank.senders, sender, !busy && source == null) { sender = bank.senders[it] }
                    TeachingBody(body, source != null, !busy) {
                        if (it.text != body.text) { bindings = emptyList(); preview = null }
                        body = it
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap),
                        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
                        PatternOperation.entries.forEach { choice ->
                            MasroofChoice(operations[choice.ordinal], operation == choice, {
                                operation = choice; fieldIndex = 0; bindings = emptyList(); preview = null
                            }, enabled = !busy, modifier = Modifier.testTag("pattern-operation-${choice.name}"))
                        }
                    }
                    MasroofButton(onClick = { keyboard?.hide(); focus.clearFocus(); step = 1 }, enabled = !busy && body.text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.teaching_next))
                    }
                }
                1 -> {
                    MasroofGuidanceLabel(stringResource(R.string.teaching_field_title, patternFieldName(field)),
                        stringResource(R.string.teaching_field_help), style = MaterialTheme.typography.titleLarge,
                        contentColor = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.teaching_field_progress, fieldIndex + 1, operation.fields.size), style = MaterialTheme.typography.bodySmall)
                    TeachingBody(body, true, !busy) { body = it }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap),
                        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
                        candidates.forEach { candidate ->
                            MasroofChoice(candidate.text, body.selection.min == candidate.start && body.selection.max == candidate.end,
                                { body = body.copy(selection = TextRange(candidate.start, candidate.end)) }, enabled = !busy,
                                modifier = Modifier.testTag("pattern-candidate-${candidate.start}-${candidate.end}"))
                        }
                    }
                    if (!body.selection.collapsed) Text(body.text.substring(body.selection.min, body.selection.max), style = MaterialTheme.typography.titleMedium)
                    MasroofButton(onClick = {
                        bindSelection(field)
                        if (fieldIndex < operation.fields.lastIndex) fieldIndex++ else buildPreview()
                    }, enabled = !busy && !body.selection.collapsed, modifier = Modifier.fillMaxWidth().testTag("pattern-bind")) {
                        Text(stringResource(R.string.teaching_confirm_field))
                    }
                    if (field != "amount") MasroofTextButton(onClick = {
                        bindings = bindings.filterNot { it.field == field }
                        if (fieldIndex < operation.fields.lastIndex) fieldIndex++ else buildPreview()
                    }, enabled = !busy) { Text(stringResource(R.string.teaching_skip_field)) }
                    if (fieldIndex > 0) MasroofTextButton(onClick = { fieldIndex--; body = body.copy(selection = TextRange.Zero) }, enabled = !busy) {
                        Text(stringResource(R.string.teaching_previous))
                    }
                    bindings.forEach { binding ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${if (binding.ignored) stringResource(R.string.pattern_ignore) else patternFieldName(binding.field)}: ${body.text.substring(binding.start, binding.end)}", Modifier.weight(1f))
                            MasroofTextButton(onClick = { bindings = bindings - binding }, enabled = !busy) { Text(stringResource(R.string.pattern_remove_binding)) }
                        }
                    }
                    MasroofTextButton(onClick = { showIgnored = !showIgnored }, enabled = !busy) { Text(stringResource(R.string.teaching_other_values)) }
                    if (showIgnored) MasroofOutlinedButton(onClick = {
                        val key = (1..16).firstOrNull { index -> bindings.none { it.field == "ignored_$index" } }
                        if (key != null) bindSelection("ignored_$key", ignored = true)
                    }, enabled = !busy && !body.selection.collapsed && bindings.size < 16) { Text(stringResource(R.string.pattern_ignore)) }
                    MasroofOutlinedButton(onClick = ::buildPreview, enabled = !busy && bindings.any { it.field == "amount" }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pattern_preview))
                    }
                }
                2 -> preview?.let { reviewed ->
                    Text(stringResource(R.string.teaching_preview_title), style = MaterialTheme.typography.titleLarge)
                    MasroofCard {
                        Text(operations[operation.ordinal], style = MaterialTheme.typography.titleMedium)
                        reviewed.fields.forEach { (key, value) -> Text("${patternFieldName(key)}: ${value.text}") }
                    }
                    MasroofGuidanceLabel(stringResource(R.string.teaching_pattern_preview), stringResource(R.string.pattern_preview_only))
                    MasroofTextButton(onClick = { showSample = !showSample }, enabled = !busy) { Text(stringResource(R.string.teaching_try_another)) }
                    if (showSample) {
                        OutlinedTextField(sample, { sample = it; sampleChecked = false; sampleResult = null },
                            label = { Text(stringResource(R.string.pattern_other_message)) }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().testTag("pattern-sample"))
                        MasroofOutlinedButton(onClick = { work {
                            sampleResult = workflow.tryPreview(reviewed, sender, sample); sampleChecked = true
                        } }, enabled = !busy && sample.isNotBlank()) { Text(stringResource(R.string.pattern_test)) }
                        if (sampleChecked && sampleResult == null) Text(stringResource(R.string.pattern_no_match))
                        sampleResult?.forEach { (key, value) -> Text("${patternFieldName(key)}: ${value.text}") }
                    }
                    MasroofButton(onClick = { work { workflow.saveTeaching(reviewed); onSaved(reviewed, body.text) } },
                        enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pattern_save)) }
                    MasroofOutlinedButton(onClick = { step = 1; preview = null }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.teaching_previous))
                    }
                }
            }
        }
    }
}

@Composable
private fun TeachingBody(body: TextFieldValue, readOnly: Boolean, enabled: Boolean, onChange: (TextFieldValue) -> Unit) {
    OutlinedTextField(body, onChange, readOnly = readOnly, enabled = enabled,
        label = { Text(stringResource(R.string.pattern_message)) }, modifier = Modifier.fillMaxWidth().testTag("pattern-body"))
}

@Composable
internal fun patternFieldName(key: String): String {
    val keys = stringArrayResource(R.array.pattern_field_keys)
    return stringArrayResource(R.array.pattern_field_names).getOrNull(keys.indexOf(key)) ?: key
}

@Composable
internal fun patternOperationName(label: String): String {
    val index = PatternOperation.entries.indexOfFirst { it.name == label }
    return stringArrayResource(R.array.pattern_operations).getOrNull(index) ?: when (label) {
        "FINANCING_INSTALLMENT" -> stringResource(R.string.review_family_financing_installment)
        "FEE" -> stringResource(R.string.review_family_fee)
        "NON_FINANCIAL" -> stringResource(R.string.review_reason_non_financial)
        "BALANCE_NOTICE" -> stringResource(R.string.pattern_balance_notice)
        else -> label
    }
}
