package com.baraa.masroof.presentation.patterns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.*
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Bank-scoped catalog; teaching, approval and application remain separate explicit actions. */
@Composable
fun MessagePatternsScreen(workflow: MessagePatternsWorkflow, initialBankId: String? = null,
    source: PatternTeachingSource? = null, onOpenReview: ((String) -> Unit)? = null, onBack: () -> Unit,
    onImportBank: ((String) -> Unit)? = null) {
    val bankScope = source?.bankId ?: initialBankId
    var snapshot by remember { mutableStateOf<PatternCatalog?>(null) }
    var selectedBank by remember { mutableStateOf(bankScope) }
    var selectedPattern by remember { mutableStateOf<String?>(null) }
    var teaching by remember { mutableStateOf(source != null) }
    var addingBank by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf(source?.sender.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var approving by remember { mutableStateOf(false) }
    var savedTeaching by remember { mutableStateOf(false) }
    var savedBody by remember { mutableStateOf("") }
    var showPending by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingPatternReport?>(null) }
    var applied by remember { mutableStateOf<PendingPatternApplyReport?>(null) }
    val scope = rememberCoroutineScope()
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
    fun back() {
        if (busy) return
        when {
            approving -> approving = false
            savedTeaching && source != null -> onBack()
            selectedPattern != null -> { selectedPattern = null; savedTeaching = false; applied = null }
            teaching && source != null -> onBack()
            teaching -> teaching = false
            showPending -> showPending = false
            addingBank -> addingBank = false
            selectedBank != null && bankScope == null -> selectedBank = null
            else -> onBack()
        }
    }
    LaunchedEffect(workflow) {
        try { snapshot = workflow.catalog() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    BackHandler { back() }
    val bank = snapshot?.banks?.singleOrNull { it.bankId == selectedBank }
    val pattern = bank?.currentPatterns?.singleOrNull { it.id == selectedPattern }
    LaunchedEffect(bank?.bankId, snapshot?.generation, showPending, selectedPattern, teaching) {
        if (bank != null && showPending && selectedPattern == null && !teaching) {
            pending = null
            try { pending = workflow.previewPending(bank.bankId, requireNotNull(snapshot).generation) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
        }
    }
    if (bank != null && teaching && (source == null || source.sender in bank.senders)) {
        PatternTeachingScreen(workflow, requireNotNull(snapshot).generation, bank, source,
            onSaved = { preview, body ->
                teaching = false; selectedPattern = preview.patternId; savedTeaching = true; savedBody = body
                work { snapshot = workflow.catalog() }
            }, onReload = { work { snapshot = workflow.catalog() } },
            onBack = { if (source != null) onBack() else teaching = false })
        return
    }
    if (pattern != null && approving) {
        PatternFinancialApprovalScreen(workflow, requireNotNull(snapshot).generation, pattern,
            source?.body ?: savedBody, source != null || savedBody.isNotBlank(),
            onSaved = { snapshot = it; approving = false; savedTeaching = false }, onBack = { approving = false })
        return
    }
    MasroofSecondaryScaffold(stringResource(R.string.teaching_title), ::back, stringResource(R.string.settings_back)) { modifier ->
        Column(modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            if (busy || snapshot == null && !failed) CircularProgressIndicator()
            if (failed) {
                MasroofGuidanceLabel(stringResource(R.string.review_action_failed), stringResource(R.string.pattern_failed), contentColor = MaterialTheme.colorScheme.error)
                MasroofTextButton(onClick = { work { snapshot = workflow.catalog() } }, enabled = !busy) { Text(stringResource(R.string.dashboard_retry)) }
            }
            val configuration = snapshot
            if (configuration != null) when {
                bank == null -> {
                    MasroofGuidanceLabel(stringResource(R.string.teaching_catalog), stringResource(R.string.teaching_choose_message_help),
                        style = MaterialTheme.typography.titleLarge, contentColor = MaterialTheme.colorScheme.onSurface)
                    if (bankScope == null) configuration.banks.forEach { item ->
                        MasroofNavCard(onClick = { selectedBank = item.bankId; applied = null }, enabled = !busy) {
                            Text(item.displayName ?: item.bankId, style = MaterialTheme.typography.titleMedium)
                            Text(item.senders.joinToString(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (addingBank || bankScope != null || configuration.banks.isEmpty()) {
                        MasroofCard {
                            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.pattern_bank_name)) }, enabled = !busy)
                            OutlinedTextField(sender, { sender = it }, label = { Text(stringResource(R.string.pattern_sender)) }, enabled = !busy, readOnly = source != null)
                            MasroofButton(onClick = { work {
                                workflow.addBank(configuration.generation, name, sender, bankScope)
                                snapshot = workflow.catalog()
                                selectedBank = snapshot!!.banks.lastOrNull { it.bankId !in configuration.banks.map { old -> old.bankId } }?.bankId
                                name = ""; addingBank = false
                            } }, enabled = !busy && name.isNotBlank() && sender.isNotBlank()) { Text(stringResource(R.string.pattern_add_bank)) }
                        }
                    } else MasroofOutlinedButton(onClick = { addingBank = true }, enabled = !busy) { Text(stringResource(R.string.pattern_add_bank)) }
                }
                pattern != null -> {
                    if (savedTeaching) {
                        MasroofGuidanceLabel(stringResource(R.string.teaching_saved_title), stringResource(R.string.teaching_saved_scope),
                            style = MaterialTheme.typography.titleLarge, contentColor = MaterialTheme.colorScheme.onSurface)
                    }
                    PatternDetailCard(bank, pattern, busy,
                        onApprove = if (workflow.canApprove) ({ approving = true }) else null,
                        onToggle = { work { workflow.setEnabled(configuration.generation, bank.bankId, pattern.id, !pattern.enabled); snapshot = workflow.catalog() } },
                        onRestore = { revision -> work { workflow.restoreRevision(configuration.generation, bank.bankId, pattern.id, revision); snapshot = workflow.catalog() } })
                    if (source?.reviewId != null && pattern.canApply && workflow.canApplyPending) {
                        MasroofButton(onClick = { work { applied = workflow.applyPending(bank.bankId, configuration.generation, source.reviewId) } },
                            enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("pattern-apply-current")) { Text(stringResource(R.string.teaching_apply_current)) }
                        applied?.let { PatternApplyResult(it, singleMessage = true) }
                    }
                    if (source != null) MasroofOutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.teaching_return_review))
                    }
                    MasroofOutlinedButton(onClick = { selectedPattern = null; savedTeaching = false }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.teaching_saved_patterns))
                    }
                }
                else -> {
                    MasroofCard {
                        Text(bank.displayName ?: bank.bankId, style = MaterialTheme.typography.titleMedium)
                        Text(bank.senders.joinToString(), style = MaterialTheme.typography.bodySmall)
                    }
                    if (source != null && source.sender !in bank.senders) Text(stringResource(R.string.pattern_sender_mismatch), color = MaterialTheme.colorScheme.error)
                    MasroofButton(onClick = { teaching = true; savedTeaching = false; savedBody = "" },
                        enabled = !busy && (source == null || source.sender in bank.senders), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pattern_teach)) }
                    Text(stringResource(R.string.teaching_catalog), style = MaterialTheme.typography.titleLarge)
                    bank.currentPatterns.groupBy { it.label }.forEach { (label, patterns) ->
                        MasroofCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(patternOperationName(label), style = MaterialTheme.typography.titleMedium)
                                Text(pluralStringResource(R.plurals.teaching_patterns_count, patterns.size, patterns.size), style = MaterialTheme.typography.labelMedium)
                            }
                            patterns.forEachIndexed { index, item ->
                                MasroofReferenceNavigationRow(
                                    title = item.example.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: patternOperationName(item.label),
                                    subtitle = stringResource(R.string.pattern_revision, patternOperationName(item.label), item.revision),
                                    status = patternStatusName(item), icon = MasroofIcons.sms,
                                    onClick = { if (!busy) { selectedPattern = item.id; applied = null } },
                                    divider = index != patterns.lastIndex,
                                    modifier = Modifier.testTag("pattern-open-${bank.bankId}-${item.id}"),
                                )
                            }
                        }
                    }
                    if (workflow.canPreviewPending) MasroofOutlinedButton(onClick = { showPending = !showPending },
                        enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pattern_pending_title)) }
                    if (showPending) PatternPendingCard(pending, busy, applied,
                        onRefresh = { work { pending = workflow.previewPending(bank.bankId, configuration.generation) } },
                        onApply = if (workflow.canApplyPending && bank.currentPatterns.any { it.canApply }) ({ work {
                            applied = workflow.applyPending(bank.bankId, configuration.generation)
                            pending = workflow.previewPending(bank.bankId, configuration.generation)
                        } }) else null, onOpenReview = onOpenReview)
                    onImportBank?.let { action -> MasroofOutlinedButton({ action(bank.bankId) }, enabled = !busy) {
                        Text(stringResource(R.string.pattern_import_bank))
                    } }

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
