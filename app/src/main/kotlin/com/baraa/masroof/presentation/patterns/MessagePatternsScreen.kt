package com.baraa.masroof.presentation.patterns

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.*
import com.baraa.masroof.engine.bank.patterns.MessagePatternConfiguration
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Drafts live only on this screen; leaving discards them. Saving requires a generated preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagePatternsScreen(workflow: MessagePatternsWorkflow, initialBankId: String? = null, source: PatternTeachingSource? = null, onOpenReview: ((String) -> Unit)? = null, onBack: () -> Unit) {
    val bankScope = source?.bankId ?: initialBankId
    var snapshot by remember { mutableStateOf<MessagePatternConfiguration?>(null) }
    var selectedBank by remember { mutableStateOf(bankScope) }
    var teaching by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf(source?.sender.orEmpty()) }
    var body by remember { mutableStateOf(TextFieldValue(source?.body.orEmpty())) }
    var operation by remember { mutableStateOf(PatternOperation.PURCHASE) }
    var role by remember { mutableStateOf("amount") }
    var bindings by remember { mutableStateOf(emptyList<PatternBinding>()) }
    var candidates by remember { mutableStateOf(emptyList<PatternText>()) }
    var preview by remember { mutableStateOf<PatternTeachingPreview?>(null) }
    var sample by remember { mutableStateOf("") }
    var sampleResult by remember { mutableStateOf<Map<String, PatternText>?>(null) }
    var sampleChecked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var approving by remember { mutableStateOf<String?>(null) }
    var applied by remember { mutableStateOf<PendingPatternApplyReport?>(null) }
    var imported by remember { mutableStateOf<com.baraa.masroof.application.sms.SmsScanResult?>(null) }
    var importRequested by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val operationNames = stringArrayResource(R.array.pattern_operations)
    val fieldKeys = stringArrayResource(R.array.pattern_field_keys)
    val fieldNames = stringArrayResource(R.array.pattern_field_names)
    fun fieldName(key: String) = fieldNames.getOrNull(fieldKeys.indexOf(key)) ?: key
    fun resetTeaching() {
        teaching = false; body = TextFieldValue(source?.body.orEmpty()); bindings = emptyList(); candidates = emptyList()
        preview = null; sample = ""; sampleResult = null; sampleChecked = false; failed = false
    }
    fun back() { if (!busy) { if (teaching && source == null) resetTeaching() else onBack() } }
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
    val readSmsPermission = if (workflow.canImportBank) rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val bankId = importRequested
        importRequested = null
        if (granted && bankId != null) work { imported = workflow.importBank(bankId) }
        else imported = com.baraa.masroof.application.sms.SmsScanResult(failure = com.baraa.masroof.application.sms.SmsScanFailure.PermissionDenied)
    } else null
    LaunchedEffect(workflow) {
        try { snapshot = workflow.load() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    BackHandler { back() }
    val bank = snapshot?.banks?.singleOrNull { it.bankId == selectedBank }
    val approvalPattern = bank?.currentPatterns?.singleOrNull { it.id == approving }
    if (approvalPattern != null) {
        PatternFinancialApprovalScreen(workflow, requireNotNull(snapshot).generation, approvalPattern, source?.body.orEmpty(), source != null,
            onSaved = { snapshot = it; approving = null }, onBack = { approving = null })
        return
    }
    var pending by remember(selectedBank, snapshot?.generation) { mutableStateOf<PendingPatternReport?>(null) }
    var pendingFailed by remember(selectedBank, snapshot?.generation) { mutableStateOf(false) }
    var pendingReload by remember { mutableIntStateOf(0) }
    LaunchedEffect(bank?.bankId, snapshot?.generation, teaching, pendingReload) {
        if (bank != null && !teaching && workflow.canPreviewPending) {
            pending = null; pendingFailed = false
            try { pending = workflow.previewPending(bank.bankId, requireNotNull(snapshot).generation) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { pendingFailed = true }
        }
    }
    MasroofSecondaryScaffold(stringResource(R.string.pattern_title), { back() }, stringResource(R.string.settings_back)) { modifier ->
        Column(modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            Text(stringResource(R.string.pattern_preview_only), style = MaterialTheme.typography.bodyMedium)
            if (busy) CircularProgressIndicator()
            if (failed) {
                Text(stringResource(R.string.pattern_failed), color = MaterialTheme.colorScheme.error)
                MasroofTextButton(onClick = { work { snapshot = workflow.load(); preview = null } }, enabled = !busy) {
                    Text(stringResource(R.string.dashboard_retry))
                }
            }
            val configuration = snapshot
            if (configuration != null && !teaching) {
                MasroofCard {
                    if (bank == null && bankScope == null) configuration.banks.forEach { item ->
                        MasroofOutlinedButton(onClick = { selectedBank = item.bankId }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(item.displayName ?: item.bankId)
                        }
                    }
                    if (bank == null) {
                        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.pattern_bank_name)) }, enabled = !busy)
                        OutlinedTextField(sender, { sender = it }, label = { Text(stringResource(R.string.pattern_sender)) }, enabled = !busy, readOnly = source != null)
                        MasroofButton(onClick = { work {
                            snapshot = workflow.addBank(configuration.generation, name, sender, bankScope)
                            selectedBank = snapshot!!.banks.lastOrNull { it.bankId !in configuration.banks.map { old -> old.bankId } }?.bankId
                            name = ""; sender = ""
                        } }, enabled = !busy && name.isNotBlank() && sender.isNotBlank()) { Text(stringResource(R.string.pattern_add_bank)) }
                    } else {
                        MasroofSectionHeader(bank.displayName ?: bank.bankId)
                        Text(bank.senders.joinToString())
                        if (workflow.canImportBank) MasroofOutlinedButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                                work { imported = workflow.importBank(bank.bankId); pendingReload++ }
                            else { importRequested = bank.bankId; readSmsPermission?.launch(android.Manifest.permission.READ_SMS) }
                        }, enabled = !busy) {
                            Text(stringResource(R.string.pattern_import_bank))
                        }
                        imported?.let { Text(stringResource(R.string.pattern_import_result, it.inserted, it.reviewRequired, it.failed))
                            if (it.failure != null) Text(stringResource(R.string.pattern_import_incomplete), color = MaterialTheme.colorScheme.error) }
                        if (source != null && source.sender !in bank.senders) Text(stringResource(R.string.pattern_sender_mismatch))
                        MasroofButton(onClick = { resetTeaching(); sender = source?.sender ?: bank.senders.first(); teaching = true }, enabled = !busy && (source == null || source.sender in bank.senders)) {
                            Text(stringResource(R.string.pattern_teach))
                        }
                        bank.currentPatterns.forEach { pattern ->
                            val operationIndex = PatternOperation.entries.indexOfFirst { it.name == pattern.label }
                            Text(stringResource(R.string.pattern_revision, operationNames.getOrNull(operationIndex) ?: pattern.label, pattern.revision))
                            Text(pattern.parts.joinToString("") { part ->
                                when (part) {
                                    is com.baraa.masroof.engine.bank.patterns.StaticText -> part.value
                                    is com.baraa.masroof.engine.bank.patterns.FieldSlot -> "…"
                                }
                            }.take(240), style = MaterialTheme.typography.bodySmall)
                            Text(pattern.parts.filterIsInstance<com.baraa.masroof.engine.bank.patterns.FieldSlot>()
                                .filterNot { it.ignored }.joinToString { fieldName(it.field) })
                            Text(stringResource(if (pattern.factContract == null) R.string.pattern_financial_preview_status else R.string.pattern_financial_approved_status))
                            if (workflow.canApprove(bank.bankId)) MasroofOutlinedButton(onClick = { approving = pattern.id }, enabled = !busy && pattern.enabled) {
                                Text(stringResource(R.string.pattern_financial_approve))
                            }
                            MasroofTextButton(onClick = { work {
                                snapshot = workflow.setEnabled(configuration.generation, bank.bankId, pattern.id, !pattern.enabled)
                            } }, enabled = !busy) { Text(stringResource(if (pattern.enabled) R.string.pattern_disable else R.string.pattern_enable)) }
                            bank.patterns.filter { it.id == pattern.id && it.revision < pattern.revision }.forEach { old ->
                                MasroofTextButton(onClick = { work {
                                    snapshot = workflow.restoreRevision(configuration.generation, bank.bankId, pattern.id, old.revision)
                                } }, enabled = !busy) { Text(stringResource(R.string.pattern_restore_revision, old.revision)) }
                            }
                        }
                        if (bankScope == null) MasroofTextButton(onClick = { selectedBank = null }, enabled = !busy) {
                            Text(stringResource(R.string.pattern_add_bank))
                        }
                    }
                }
            }
            if (bank != null && !teaching && workflow.canPreviewPending) {
                MasroofCard {
                    MasroofSectionHeader(stringResource(R.string.pattern_pending_title))
                    Text(stringResource(R.string.pattern_pending_scope), style = MaterialTheme.typography.bodySmall)
                    MasroofTextButton(onClick = { work { snapshot = workflow.load(); pendingReload++ } }, enabled = !busy) { Text(stringResource(R.string.pattern_pending_refresh)) }
                    if (workflow.canApplyPending) MasroofButton(onClick = { work {
                        applied = workflow.applyPending(bank.bankId, requireNotNull(snapshot).generation); pendingReload++
                    } }, enabled = !busy && bank.currentPatterns.any { it.enabled && it.factContract != null }) { Text(stringResource(R.string.pattern_apply_pending)) }
                    applied?.let { Text(stringResource(R.string.pattern_apply_result, it.processed, it.posted,
                        it.failed?.toString() ?: stringResource(R.string.pattern_failure_count_unknown)))
                        if (it.limited) Text(stringResource(R.string.pattern_pending_limited))
                        if (it.incomplete) Text(stringResource(R.string.pattern_financial_incomplete), color = MaterialTheme.colorScheme.error) }
                    when {
                        pendingFailed -> Text(stringResource(R.string.pattern_failed), color = MaterialTheme.colorScheme.error)
                        pending == null -> CircularProgressIndicator()
                        else -> {
                            val report = requireNotNull(pending)
                            val statusNames = stringArrayResource(R.array.pattern_pending_statuses)
                            PendingPatternStatus.entries.forEach { status ->
                                Text("${statusNames[status.ordinal]}: ${report.rows.count { it.status == status }}")
                            }
                            if (report.limited || report.rows.size > 50) Text(stringResource(R.string.pattern_pending_limited))
                            report.rows.take(50).forEachIndexed { index, row ->
                                Text(stringResource(R.string.pattern_pending_row, index + 1, statusNames[row.status.ordinal]), style = MaterialTheme.typography.titleSmall)
                                if (onOpenReview != null) MasroofTextButton(onClick = { onOpenReview(row.reviewId) }, enabled = !busy) {
                                    Text(stringResource(R.string.pattern_pending_open))
                                }
                                // Competing candidates stay visible; none becomes an accepted financial interpretation.
                                row.matches.forEach { match ->
                                    Text(stringResource(R.string.pattern_revision,
                                        operationNames.getOrNull(PatternOperation.entries.indexOfFirst { it.name == match.operation }) ?: match.operation,
                                        match.revision))
                                    match.fields.forEach { (key, value) -> Text("${fieldName(key)}: ${value.text}") }
                                }
                            }
                        }
                    }
                }
            }
            if (configuration != null && bank != null && teaching) {
                MasroofCard {
                    PatternChoice(operationNames.toList(), operationNames[operation.ordinal], !busy) { index ->
                        operation = PatternOperation.entries[index]; role = "amount"; bindings = emptyList(); preview = null
                    }
                    PatternChoice(bank.senders, sender, !busy && source == null) { sender = bank.senders[it]; preview = null }
                    Text(stringResource(R.string.pattern_selection_hint))
                    OutlinedTextField(body, { value ->
                        if (value.text != body.text) { bindings = emptyList(); candidates = emptyList(); preview = null }
                        body = value
                    }, label = { Text(stringResource(R.string.pattern_message)) }, enabled = !busy, readOnly = source != null, modifier = Modifier.fillMaxWidth().testTag("pattern-body"))
                    MasroofOutlinedButton(onClick = { work { candidates = workflow.candidates(body.text) } }, enabled = !busy && body.text.isNotBlank()) {
                        Text(stringResource(R.string.pattern_extract))
                    }
                    candidates.forEach { candidate ->
                        MasroofTextButton(onClick = { body = body.copy(selection = TextRange(candidate.start, candidate.end)) }, enabled = !busy) {
                            Text(candidate.text)
                        }
                    }
                    PatternChoice(operation.fields.map(::fieldName), fieldName(role), !busy) { role = operation.fields[it] }
                    MasroofOutlinedButton(onClick = {
                        bindings = bindings.filterNot { it.field == role } + PatternBinding(role, body.selection.min, body.selection.max)
                        preview = null
                    }, enabled = !busy && !body.selection.collapsed) { Text(stringResource(R.string.pattern_bind)) }
                    MasroofTextButton(onClick = {
                        val index = (1..16).firstOrNull { i -> bindings.none { it.field == "ignored_$i" } } ?: return@MasroofTextButton
                        bindings = bindings + PatternBinding("ignored_$index", body.selection.min, body.selection.max, ignored = true)
                        preview = null
                    }, enabled = !busy && !body.selection.collapsed && bindings.size < 16) { Text(stringResource(R.string.pattern_ignore)) }
                    bindings.forEach { binding ->
                        Text("${if (binding.ignored) stringResource(R.string.pattern_ignore) else fieldName(binding.field)}: ${body.text.substring(binding.start, binding.end)}")
                        MasroofTextButton(onClick = { bindings = bindings - binding; preview = null }, enabled = !busy) {
                            Text(stringResource(R.string.pattern_remove_binding))
                        }
                    }
                    MasroofButton(onClick = { work {
                        preview = workflow.teach(configuration.generation, bank.bankId, sender, operation, body.text, bindings)
                        sampleResult = null; sampleChecked = false
                    } }, enabled = !busy && bindings.any { it.field == "amount" }) { Text(stringResource(R.string.pattern_preview)) }
                }
                preview?.let { reviewed ->
                    MasroofCard {
                        reviewed.fields.forEach { (key, value) -> Text("${fieldName(key)}: ${value.text}") }
                        OutlinedTextField(sample, { sample = it; sampleResult = null; sampleChecked = false },
                            label = { Text(stringResource(R.string.pattern_other_message)) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("pattern-sample"))
                        MasroofOutlinedButton(onClick = { work {
                            sampleResult = workflow.tryPreview(reviewed, sender, sample); sampleChecked = true
                        } }, enabled = !busy && sample.isNotBlank()) { Text(stringResource(R.string.pattern_test)) }
                        if (sampleChecked && sampleResult == null) Text(stringResource(R.string.pattern_no_match))
                        sampleResult?.forEach { (key, value) -> Text("${fieldName(key)}: ${value.text}") }
                        MasroofButton(onClick = { work { snapshot = workflow.saveTeaching(reviewed); if (source != null) onBack() else resetTeaching() } }, enabled = !busy) {
                            Text(stringResource(R.string.pattern_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PatternChoice(options: List<String>, selected: String, enabled: Boolean, onChoose: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MasroofOutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(selected) }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEachIndexed { index, option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onChoose(index) }) }
        }
    }
}
