package com.baraa.masroof.presentation.patterns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.*
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.settings.SettingsNavRow
import com.baraa.masroof.ui.theme.MasroofIconSizes
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Bank-scoped catalog; teaching, approval and application remain separate explicit actions. */
@Composable
fun MessagePatternsScreen(workflow: MessagePatternsWorkflow, initialBankId: String? = null,
    source: PatternTeachingSource? = null, onOpenReview: ((String) -> Unit)? = null, onBack: () -> Unit,
    onImportBank: ((String) -> Unit)? = null, onOpenAccounts: (() -> Unit)? = null) {
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
    var savedApproval by remember { mutableStateOf(false) }
    var editingPattern by remember { mutableStateOf<PatternDefinitionView?>(null) }
    var editingBody by remember { mutableStateOf("") }
    var confirmingDelete by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var quickPreview by remember { mutableStateOf<PatternDefinitionView?>(null) }
    val scope = rememberCoroutineScope()
    fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (caught: Exception) { workflow.reportFailure("MessagePatternsScreen", caught); failed = true }
            finally { busy = false }
        }
    }
    fun back() {
        if (busy) return
        when {
            history -> history = false
            approving -> approving = false
            selectedPattern != null && source != null -> onBack()
            selectedPattern != null -> { selectedPattern = null; savedTeaching = false; savedApproval = false; applied = null }
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
        catch (caught: Exception) { workflow.reportFailure("MessagePatternsScreen", caught); failed = true }
    }
    BackHandler { back() }
    val bank = snapshot?.banks?.singleOrNull { it.bankId == selectedBank }
    val pattern = bank?.currentPatterns?.singleOrNull { it.id == selectedPattern }
    fun applyPending(generation: Long, reviewId: String? = null, patternId: String? = null) = work {
        val bankId = requireNotNull(bank).bankId
        pending = null
        applied = workflow.applyPending(bankId, generation, reviewId, patternId)
        pending = workflow.previewPending(bankId, generation)
    }
    LaunchedEffect(bank?.bankId, snapshot?.generation, showPending, selectedPattern, teaching) {
        if (bank != null && workflow.canPreviewPending && !teaching &&
            (showPending && selectedPattern == null || pattern?.canApply == true)) {
            pending = null
            try { pending = workflow.previewPending(bank.bankId, requireNotNull(snapshot).generation) }
            catch (e: CancellationException) { throw e }
            catch (caught: Exception) { workflow.reportFailure("MessagePatternsScreen", caught); failed = true }
        }
    }
    if (bank != null && teaching && (source == null || source.sender in bank.senders)) {
        PatternTeachingScreen(workflow, requireNotNull(snapshot).generation, bank, source,
            onSaved = { preview, body ->
                teaching = false; editingPattern = null; selectedPattern = preview.patternId; savedTeaching = true; savedBody = body
                work { snapshot = workflow.catalog() }
            }, onReload = { work { snapshot = workflow.catalog() } },
            onBack = { if (source != null && editingPattern == null) onBack() else { teaching = false; editingPattern = null } },
            existingPattern = editingPattern, initialBody = editingBody)
        return
    }
    if (pattern != null && approving) {
        PatternFinancialApprovalScreen(workflow, requireNotNull(snapshot).generation, pattern,
            source?.body ?: savedBody, source != null || savedBody.isNotBlank(),
            onSaved = { snapshot = it; approving = false; savedTeaching = false; savedApproval = true; applied = null }, onBack = { approving = false })
        return
    }
    if (bank != null && pattern != null && history) {
        PatternHistoryScreen(bank, pattern, busy, failed, { if (!busy) history = false }) { revision -> work {
            workflow.restoreRevision(requireNotNull(snapshot).generation, bank.bankId, pattern.id, revision)
            snapshot = workflow.catalog(); savedApproval = false; applied = null; history = false
        } }
        return
    }
    if (bank != null && pattern == null && showPending) {
        MasroofSecondaryScaffold(stringResource(R.string.pattern_pending_title), ::back, stringResource(R.string.settings_back)) { modifier ->
            Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
                PatternPendingCard(pending, busy, applied,
                    onRefresh = { work { pending = workflow.previewPending(bank.bankId, requireNotNull(snapshot).generation) } },
                    onApply = if (workflow.canApplyPending && bank.currentPatterns.any { it.canApply }) ({ applyPending(requireNotNull(snapshot).generation) })
                        else null, onOpenReview = onOpenReview)
            }
        }
        return
    }
    quickPreview?.let { preview ->
        MasroofBottomSheet(title = stringResource(R.string.pattern_template_preview),
            subtitle = patternOperationName(preview.label), onDismiss = { quickPreview = null },
            onApply = { quickPreview = null }, applyLabel = stringResource(R.string.pattern_preview_done)) {
            PatternTemplatePreview(preview)
        }
    }
    if (confirmingDelete && bank != null && pattern != null) PatternDeleteDialog(
        name = patternOperationName(pattern.label), identity = "${bank.bankId} / ${pattern.id}",
        onDismiss = { confirmingDelete = false }, onDelete = {
            confirmingDelete = false
            work {
                snapshot = workflow.deletePattern(requireNotNull(snapshot).generation, bank.bankId, pattern.id)
                selectedPattern = null; savedTeaching = false; savedApproval = false; applied = null; pending = null; savedBody = ""
            }
        })
    MasroofSecondaryScaffold(
        title = pattern?.let { patternOperationName(it.label) } ?: bank?.let { it.displayName ?: it.bankId } ?: stringResource(R.string.teaching_title),
        onBack = ::back,
        backContentDescription = stringResource(when {
            pattern != null && source != null -> R.string.teaching_return_review
            pattern != null -> R.string.teaching_saved_patterns
            else -> R.string.settings_back
        }),
    ) { modifier ->
        Column(modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            if (busy || snapshot == null && !failed) CircularProgressIndicator()
            if (failed) {
                MasroofGuidanceLabel(stringResource(R.string.review_action_failed), stringResource(R.string.pattern_failed), contentColor = MaterialTheme.colorScheme.error)
                MasroofOutlinedButton(onClick = { work {
                    snapshot = workflow.catalog()
                    if (bank != null && workflow.canPreviewPending) pending = workflow.previewPending(bank.bankId, requireNotNull(snapshot).generation)
                } }, enabled = !busy) { Text(stringResource(R.string.dashboard_retry)) }
            }
            val configuration = snapshot
            if (configuration != null) when {
                bank == null -> {
                    MasroofGuidanceLabel(stringResource(R.string.teaching_catalog), stringResource(R.string.teaching_choose_message_help),
                        style = MaterialTheme.typography.titleLarge, contentColor = MaterialTheme.colorScheme.onSurface)
                    if (bankScope == null) configuration.banks.forEach { item ->
                        SettingsNavRow(icon = MasroofIcons.moneyMovement,
                            title = item.displayName ?: item.bankId, subtitle = item.senders.joinToString(),
                            onClick = { selectedBank = item.bankId; applied = null }, enabled = !busy)
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
                    if (savedApproval && applied == null) MasroofGuidanceLabel(stringResource(R.string.pattern_approval_saved), stringResource(R.string.pattern_approval_saved_help),
                        contentColor = MaterialTheme.colorScheme.primary)
                    if (savedTeaching) {
                        MasroofGuidanceLabel(stringResource(R.string.teaching_saved_title), stringResource(R.string.teaching_saved_scope),
                            style = MaterialTheme.typography.titleLarge, contentColor = MaterialTheme.colorScheme.onSurface)
                    }
                    PatternDetailCard(bank, pattern, busy,
                        onApprove = if (workflow.canApprove) ({ approving = true; applied = null; savedApproval = false }) else null,
                        onToggle = { work {
                            workflow.setEnabled(configuration.generation, bank.bankId, pattern.id, !pattern.enabled)
                            snapshot = workflow.catalog(); savedApproval = false; applied = null
                        } },
                        onEdit = if (pattern.canEdit) ({ work {
                            editingBody = source?.body ?: savedBody.ifBlank {
                                workflow.approvalExamples(bank.bankId, configuration.generation, pattern.id).firstOrNull()?.body.orEmpty()
                            }
                            editingPattern = pattern; teaching = true
                        } }) else null,
                        onDelete = { confirmingDelete = true },
                        onHistory = { history = true })
                    if (source?.reviewId != null && pattern.canApply && workflow.canApplyPending) {
                        MasroofButton(onClick = { applyPending(configuration.generation, source.reviewId, pattern.id) },
                            enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("pattern-apply-current")) { Text(stringResource(R.string.teaching_apply_current)) }
                        applied?.let { PatternApplyResult(it, singleMessage = true) }
                    } else if (pattern.canApply && workflow.canApplyPending) {
                        MasroofCard {
                            val report = pending
                            if (report == null) CircularProgressIndicator()
                            else {
                                val count = report.matchingCount(pattern.id)
                                Text(stringResource(if (count == 0) R.string.pattern_approval_no_pending else R.string.pattern_approval_matching_count, count))
                                if (count > 0) MasroofButton(onClick = { applyPending(configuration.generation, patternId = pattern.id) },
                                    enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("pattern-apply-matching")) {
                                    Text(stringResource(R.string.pattern_approval_process_matching, count))
                                }
                                if (report.limited) Text(stringResource(R.string.pattern_pending_limited))
                            }
                            applied?.let { PatternApplyResult(it) }
                        }
                    }
                    if ((applied?.posted ?: 0) > 0 && onOpenAccounts != null) MasroofButton(onOpenAccounts,
                        enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pattern_approval_view_accounts)) }
                }
                else -> {
                    MasroofCard {
                        Text(bank.displayName ?: bank.bankId, style = MaterialTheme.typography.titleMedium)
                        Text(bank.senders.joinToString(), style = MaterialTheme.typography.bodySmall)
                    }
                    if (source != null && source.sender !in bank.senders) Text(stringResource(R.string.pattern_sender_mismatch), color = MaterialTheme.colorScheme.error)
                    MasroofButton(onClick = { teaching = true; editingPattern = null; editingBody = ""; savedTeaching = false; savedBody = "" },
                        enabled = !busy && (source == null || source.sender in bank.senders), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pattern_teach)) }
                    Text(stringResource(R.string.teaching_catalog), style = MaterialTheme.typography.titleLarge)
                    bank.currentPatterns.groupBy { it.label }.forEach { (label, patterns) ->
                        MasroofCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(patternOperationName(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(pluralStringResource(R.plurals.teaching_patterns_count, patterns.size, patterns.size), style = MaterialTheme.typography.labelMedium)
                            }
                            patterns.forEachIndexed { index, item ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    MasroofReferenceNavigationRow(
                                        title = item.example.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: patternOperationName(item.label),
                                        subtitle = patternOperationName(item.label),
                                        status = patternStatusName(item), icon = MasroofIcons.sms,
                                        onClick = { if (!busy) { selectedPattern = item.id; savedBody = ""; applied = null; savedApproval = false } },
                                        divider = index != patterns.lastIndex,
                                        modifier = Modifier.weight(1f).testTag("pattern-open-${bank.bankId}-${item.id}"),
                                    )
                                    IconButton(onClick = { quickPreview = item }, enabled = !busy,
                                        modifier = Modifier.testTag("pattern-preview-${bank.bankId}-${item.id}")) {
                                        Icon(MasroofIcons.preview, stringResource(R.string.pattern_template_preview),
                                            Modifier.size(MasroofIconSizes.md), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    if (workflow.canPreviewPending) MasroofOutlinedButton(onClick = { showPending = true },
                        enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pattern_pending_title)) }
                    onImportBank?.let { action -> MasroofOutlinedButton({ action(bank.bankId) }, enabled = !busy) {
                        Text(stringResource(R.string.pattern_import_bank))
                    } }

                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PatternChoice(options: List<String>, selected: String, enabled: Boolean, onChoose: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap),
        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
        options.forEachIndexed { index, option ->
            MasroofChoice(option, option == selected, { onChoose(index) }, enabled = enabled)
        }
    }
}
