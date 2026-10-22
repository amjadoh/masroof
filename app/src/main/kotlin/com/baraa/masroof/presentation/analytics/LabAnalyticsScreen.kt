package com.baraa.masroof.presentation.analytics

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.analytics.*
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.settings.ManagedCommitmentUi
import com.baraa.masroof.presentation.settings.SettingsCommitmentEditorScreen
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

val LocalLabAnalyticsWorkflow = staticCompositionLocalOf<LabAnalyticsWorkflow?> { null }
/** Rendering seam for comparing the exact same Home with/without the additive card. */
val LocalLabAnalyticsCardsEnabled = staticCompositionLocalOf { true }
@Composable private fun workflow(): LabAnalyticsWorkflow {
    LocalLabAnalyticsWorkflow.current?.let { return it }
    val context = LocalContext.current
    return remember(context) { LabAnalyticsWorkflow.from(context) }
}

@Composable fun LabForecastCard(onOpen: () -> Unit) {
    if (!LocalLabAnalyticsCardsEnabled.current) return
    val workflow = workflow()
    val state by workflow.state.collectAsState()
    val value = state.snapshot
    MasroofCard(Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("lab-forecast-entry")) {
        MasroofSectionHeader(title = stringResource(R.string.lab_analysis_period), icon = MasroofIcons.analysis,
            onViewAll = onOpen, viewAllLabel = stringResource(R.string.lab_analysis_open))
        Status(state)
        value?.total?.let { Amount(stringResource(R.string.lab_analysis_expected_total), it) }
        if (value != null && value.total == null) Amount(stringResource(R.string.lab_analysis_actual), value.actual)
        Text(stringResource(R.string.lab_analysis_sections), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Status(state: AnalysisUiState) {
    val label = when {
        state.unavailable -> R.string.lab_analysis_failed
        state.snapshot == null -> R.string.lab_analysis_preparing
        state.updating -> R.string.lab_analysis_updating
        state.snapshot?.status != "READY" -> R.string.lab_analysis_insufficient
        state.snapshot.quality.blocked -> R.string.lab_analysis_quality_warning
        state.snapshot.method == "WEEKDAY_MEDIAN" -> R.string.lab_analysis_initial
        else -> R.string.lab_analysis_ready
    }
    val guidance = when (label) {
        R.string.lab_analysis_insufficient -> R.string.lab_analysis_need_data
        R.string.lab_analysis_initial, R.string.lab_analysis_ready -> R.string.lab_analysis_forecast_hint
        R.string.lab_analysis_quality_warning -> R.string.lab_analysis_quality_hint
        else -> null
    }
    MasroofGuidanceLabel(stringResource(label), guidance?.let { stringResource(it) },
        style = MaterialTheme.typography.labelMedium, contentColor = MaterialTheme.colorScheme.primary)
    state.snapshot?.let { Text(stringResource(R.string.lab_analysis_updated, timestamp(it.issuedAt)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable fun LabAnalyticsRoute(onBack: () -> Unit, onOpenTransaction: (String) -> Unit,
    onOpenReview: () -> Unit = {}, onRequestSmsPermission: () -> Unit = {}, onRescan: () -> Unit = {}) {
    val workflow = workflow()
    val state by workflow.state.collectAsState()
    val scope = rememberCoroutineScope()
    val target by AnalyticsNavigation.target.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val displayedTab = tab.takeIf { it in 0..1 } ?: 0 // An older saved screen may still reference the removed alerts tab.
    var settings by rememberSaveable { mutableStateOf(target == "settings") }
    var settingsEntry by rememberSaveable { mutableStateOf(target == "settings") }
    var endedAlert by rememberSaveable { mutableStateOf(false) }
    var purchaseRange by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    var pendingTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedAlert by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(target) {
        target?.let { pendingTarget = it; AnalyticsNavigation.clear() }
    }
    LaunchedEffect(pendingTarget, state.updating, state.snapshot?.sourceHash) {
        val id = pendingTarget ?: return@LaunchedEffect
        if (id == "resume") { pendingTarget = null; return@LaunchedEffect }
        if (id == "settings") { settings = true; settingsEntry = true; pendingTarget = null; return@LaunchedEffect }
        if (state.updating) return@LaunchedEffect
        val alert = state.alerts.firstOrNull { it.id == id } ?: try { workflow.findAlert(id) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
        settings = false; settingsEntry = false
        tab = if (alert?.kind in listOf("DUE", "PAYMENT")) 1 else 0
        purchaseRange = 0
        endedAlert = alert == null || alert.resolved
        if (alert != null && !alert.resolved) {
            focusedAlert = id; workflow.markAlert(id)
            if (alert.kind == "PAYMENT") alert.evidenceId?.let(onOpenTransaction)
        }
        pendingTarget = null
    }
    BackHandler(enabled = !saving) {
        when { editing != null -> editing = null; settings -> if (settingsEntry) onBack() else { settings = false }; else -> onBack() }
    }
    BackHandler(enabled = saving) { }
    val candidate = state.snapshot?.candidates?.firstOrNull { it.key == editing }
    if (candidate != null) {
        val editor = remember(candidate.key) { workflow.editorDraft(candidate) }
        SettingsCommitmentEditorScreen(
            commitment = ManagedCommitmentUi(candidate.key, editor.name, editor.amount, editor.transactionDate,
                editor.recurrence, editor.dueDate, true, candidate.sourceIds.last()),
            saving = saving, onBack = { if (!saving) editing = null },
            onSave = { draft ->
                saving = true; failure = false
                scope.launch {
                    try { if (workflow.acceptCandidate(candidate.key, draft)) editing = null else failure = true }
                    catch (_: Exception) { failure = true }
                    finally { saving = false }
                }
            }, onToggleActive = {}, onDelete = {}, newSuggestion = true,
        )
        if (failure) AlertDialog(onDismissRequest = { failure = false },
            text = { Text(stringResource(R.string.lab_analysis_save_failed)) },
            confirmButton = { MasroofTextButton(onClick = { failure = false }) { Text(stringResource(R.string.lab_analysis_close)) } })
        return
    }
    if (settings) { Preferences(workflow, state, onBack = { if (settingsEntry) onBack() else settings = false }); return }
    MasroofSecondaryScaffold(stringResource(R.string.lab_analysis_title), onBack,
        stringResource(R.string.lab_analysis_back), actions = {
            IconButton(onClick = { settingsEntry = false; settings = true }, modifier = Modifier.testTag("lab-analysis-settings")) {
                Icon(MasroofIcons.settings, stringResource(R.string.lab_analysis_settings), Modifier.size(com.baraa.masroof.ui.theme.MasroofIconSizes.appBarAction))
            }
        }) { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            TabRow(selectedTabIndex = displayedTab) {
                listOf(R.string.lab_analysis_spending_tab, R.string.lab_analysis_commitments).forEachIndexed { index, label ->
                    Tab(selected = displayedTab == index, onClick = { pendingTarget = null; focusedAlert = null; tab = index }, text = { Text(stringResource(label)) })
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
                if (displayedTab != 0 || purchaseRange == 0) state.snapshot?.let { Text("${day(it.periodStart)} — ${day(it.periodEnd - 1)}", style = MaterialTheme.typography.bodySmall) }
                if (displayedTab != 0 || purchaseRange == 0 || state.updating || state.unavailable) Status(state)
                if (endedAlert) Text(stringResource(R.string.lab_analysis_alert_ended))
                if (state.unavailable) MasroofTextButton(onClick = workflow::request) { Text(stringResource(R.string.dashboard_retry)) }
                when (displayedTab) {
                    0 -> Purchases(state, workflow, onOpenTransaction, purchaseRange, onOpenReview, onRequestSmsPermission, onRescan) { purchaseRange = it }
                    1 -> {
                        MasroofGuidanceLabel(stringResource(R.string.lab_analysis_commitments), stringResource(R.string.lab_analysis_commitments_hint))
                        state.snapshot?.dues?.forEach { due ->
                            FocusAnalysisItem(focusedAlert == "due:${due.id}:${due.day}", { focusedAlert = null }) {
                            MasroofCard {
                                Text(due.name, style = MaterialTheme.typography.titleSmall)
                                Amount(stringResource(if (due.confirmed) R.string.lab_analysis_confirmed else R.string.lab_analysis_estimated), due.amount)
                                due.day?.let { Text(day(it), style = MaterialTheme.typography.bodySmall) }
                            }
                            }
                        }
                        MasroofSectionHeader(stringResource(R.string.lab_analysis_candidates), icon = MasroofIcons.analysis)
                        if (state.snapshot?.candidates.isNullOrEmpty()) Text(stringResource(R.string.lab_analysis_no_candidates))
                        state.snapshot?.candidates?.forEach { c ->
                            MasroofCard(Modifier.testTag("lab-candidate-${c.key}")) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall)
                                Amount(stringResource(R.string.lab_analysis_estimated), c.amount)
                                if (c.overdue) Text(stringResource(R.string.lab_analysis_overdue_candidate), style = MaterialTheme.typography.bodySmall)
                                Text("${stringResource(if (c.cadence == "WEEKLY") R.string.lab_analysis_weekly else R.string.lab_analysis_monthly)} · ${day(c.nextDay)}")
                                EvidenceGroup(c.sourceIds, stringResource(R.string.lab_analysis_previous_payments),
                                    workflow, state.snapshot?.sourceHash.orEmpty(), onOpenTransaction)
                                MasroofButton(onClick = { editing = c.key }, enabled = !state.updating, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.lab_analysis_review_candidate))
                                }
                                MasroofTextButton(onClick = { scope.launch { workflow.rejectCandidate(c.key) } }) { Text(stringResource(R.string.lab_analysis_reject)) }
                            }
                        }
                    }

                }
                Spacer(Modifier.height(MasroofSpacing.screenVertical))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun FocusAnalysisItem(active: Boolean, onConsumed: () -> Unit, content: @Composable () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active) { if (active) { delay(100); requester.bringIntoView(); onConsumed() } }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) { content() }
}

@Composable fun labUnreadAlerts(workflow: LabAnalyticsWorkflow?): Int {
    if (workflow == null) return 0
    val state by workflow.state.collectAsState()
    return visibleAnalysisAlerts(state, currentAnalysisTime()).count { !it.read }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Purchases(state: AnalysisUiState, workflow: LabAnalyticsWorkflow, onOpenTransaction: (String) -> Unit, range: Int, onOpenReview: () -> Unit, onRequestSmsPermission: () -> Unit, onRescan: () -> Unit, onRangeChanged: (Int) -> Unit) {
    val s = state.snapshot
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
        listOf(0 to R.string.lab_analysis_current_period, 3 to R.string.lab_analysis_last_three, 6 to R.string.lab_analysis_last_six).forEach { (value, label) ->
            MasroofChoice(label = stringResource(label), selected = range == value, onClick = { onRangeChanged(value) }, modifier = Modifier.testTag("lab-history-$value"))
        }
    }
    if (range > 0) HistoricalPurchases(state, range)
    if (range == 0 && s != null) {
        MasroofCard {
            s.total?.let { Amount(stringResource(R.string.lab_analysis_expected_total), it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
                Column(Modifier.weight(1f)) { Amount(stringResource(R.string.lab_analysis_actual), s.actual) }
                s.remaining?.let { amount -> Column(Modifier.weight(1f)) { Amount(stringResource(R.string.lab_analysis_remaining), amount) } }
            }
            Text(stringResource(R.string.lab_analysis_scope_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.wholeDaysFrom != null && s.wholeDaysLower != null && s.wholeDaysUpper != null) {
                MasroofGuidanceLabel(stringResource(R.string.lab_analysis_whole_days_range, day(s.wholeDaysFrom),
                    formatLocalizedMoney(analysisAmount(s.wholeDaysLower)), formatLocalizedMoney(analysisAmount(s.wholeDaysUpper))),
                    stringResource(R.string.lab_analysis_intraday_hint))
            } else if (s.total != null) MasroofGuidanceLabel(stringResource(R.string.lab_analysis_range_unavailable), stringResource(R.string.lab_analysis_no_range))
            if (state.preferences.budget != null) Amount(stringResource(R.string.lab_analysis_budget), state.preferences.budget)
            if (workflow.budgetStatus() == "NEAR") Text(stringResource(R.string.lab_analysis_near), color = MaterialTheme.colorScheme.primary)
        }
        AnalysisSpendingExplanation(s, workflow, onOpenTransaction)
    }
    s?.let { AnalysisDataQuality(it, workflow, onOpenTransaction, onOpenReview, onRequestSmsPermission, onRescan) }
    if (range == 0 && s != null) {
        val points = remember(s) { s.chart() }
        if (points.isNotEmpty()) MasroofCard {
            MasroofGuidanceLabel(stringResource(R.string.lab_analysis_chart), stringResource(R.string.lab_analysis_chart_hint),
                style = MaterialTheme.typography.titleSmall, contentColor = MaterialTheme.colorScheme.onSurface)
            if (s.trainingFrom != null && s.trainingThrough != null) MasroofGuidanceLabel(
                stringResource(R.string.lab_analysis_training_range, day(s.trainingFrom), day(s.trainingThrough)),
                stringResource(R.string.lab_analysis_training_hint), style = MaterialTheme.typography.bodySmall)
            var selected by rememberSaveable(s.periodStart) { mutableIntStateOf(0) }
            val index = selected.coerceIn(points.indices)
            val labels = points.map { "${day(it.day)} · ${stringResource(if (it.expected) R.string.lab_analysis_estimated else R.string.lab_analysis_actual)} · ${formatLocalizedMoney(analysisAmount(it.amount))}" }
            Text(labels[index], style = MaterialTheme.typography.bodySmall)
            MasroofInteractiveLineChart(points.map { it.amount }, java.math.BigDecimal.ZERO,
                selectedPointIndex = index, onPointSelected = { selected = it }, pointLabel = { labels[it] },
                axisLabel = { LocalDate.ofEpochDay(points[it].day).dayOfMonth.toString() },
                currencyLabel = stringResource(R.string.lab_analysis_sar), expectedFromIndex = points.indexOfFirst { it.expected }.takeIf { it >= 0 })
            Text(stringResource(R.string.lab_analysis_chart_legend), style = MaterialTheme.typography.bodySmall)
        }
    }
    PublishedForecastHistory(state)
    LearningAndForecastPerformance(state)
}

@Composable private fun Preferences(workflow: LabAnalyticsWorkflow, state: AnalysisUiState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val original = state.preferences
    var draft by remember { mutableStateOf(original) }
    var budget by rememberSaveable { mutableStateOf(original.budget?.let { workflow.money(it).amount.toPlainString() }.orEmpty()) }
    var reminder by rememberSaveable { mutableStateOf(original.reminderDays.toString()) }
    var budgetInvalid by remember { mutableStateOf(false) }
    var daysInvalid by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> draft = draft.copy(phone = granted); denied = !granted }
    BackHandler(enabled = saving) { }
    MasroofSecondaryScaffold(stringResource(R.string.lab_analysis_settings), { if (!saving) onBack() }, stringResource(R.string.lab_analysis_back)) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofCard {
                MasroofGuidanceLabel(stringResource(R.string.lab_analysis_budget_section), stringResource(R.string.lab_analysis_budget_hint), style = MaterialTheme.typography.titleSmall)
                MasroofFormField(value = budget, onValueChange = { budget = it; budgetInvalid = false }, enabled = !saving,
                    label = { Text(stringResource(R.string.lab_analysis_budget)) },
                    placeholder = { Text(stringResource(R.string.lab_analysis_budget_example)) },
                    supportingText = { Text(stringResource(R.string.lab_analysis_budget_optional)) }, isError = budgetInvalid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("lab-analysis-budget"))
                if (budgetInvalid) Text(stringResource(R.string.lab_analysis_budget_invalid), color = MaterialTheme.colorScheme.error)
                PreferenceToggle(stringResource(R.string.lab_analysis_budget_toggle), draft.budgetAlerts, !saving, stringResource(R.string.lab_analysis_budget_hint)) { draft = draft.copy(budgetAlerts = it) }
            }
            MasroofCard {
                Text(stringResource(R.string.lab_analysis_commitments), style = MaterialTheme.typography.titleSmall)
                PreferenceToggle(stringResource(R.string.lab_analysis_due_toggle), draft.dueAlerts, !saving, stringResource(R.string.lab_analysis_due_hint)) { draft = draft.copy(dueAlerts = it) }
                if (draft.dueAlerts) {
                    MasroofFormField(value = reminder, onValueChange = { reminder = it; daysInvalid = false }, enabled = !saving,
                        label = { Text(stringResource(R.string.lab_analysis_reminder_days)) }, isError = daysInvalid,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("lab-analysis-reminder"))
                    if (daysInvalid) Text(stringResource(R.string.lab_analysis_days_invalid), color = MaterialTheme.colorScheme.error)
                }
                PreferenceToggle(stringResource(R.string.lab_analysis_payment_toggle), draft.paymentAlerts, !saving, stringResource(R.string.lab_analysis_payment_hint)) { draft = draft.copy(paymentAlerts = it) }
            }
            MasroofCard {
                PreferenceToggle(stringResource(R.string.lab_analysis_phone), draft.phone, !saving,
                    stringResource(R.string.lab_analysis_shadow) + "\n\n" + stringResource(R.string.lab_analysis_phone_hint)) { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else draft = draft.copy(phone = enabled)
                }
                if (denied) Text(stringResource(R.string.lab_analysis_permission_denied))
            }
            if (failed) Text(stringResource(R.string.lab_analysis_preferences_failed), color = MaterialTheme.colorScheme.error)
            MasroofButton(onClick = {
                val parsed = runCatching { workflow.parseBudget(budget) }
                val days = if (draft.dueAlerts) normalizeAnalysisNumber(reminder).toIntOrNull() else original.reminderDays
                budgetInvalid = parsed.isFailure
                daysInvalid = days == null || days !in 1..30
                if (!budgetInvalid && !daysInvalid) {
                    saving = true; failed = false
                    scope.launch {
                        try { workflow.updatePreferences(draft.copy(budget = parsed.getOrNull(), reminderDays = requireNotNull(days))); onBack() }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { failed = true } finally { saving = false }
                    }
                }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lab_analysis_save)) }
            MasroofOutlinedButton(onClick = onBack, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lab_analysis_cancel)) }
        }
    }
}

@Composable private fun PreferenceToggle(title: String, checked: Boolean, enabled: Boolean, guidance: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
        MasroofGuidanceLabel(title, guidance, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            contentColor = MaterialTheme.colorScheme.onSurface)
        Switch(checked, onChange, enabled = enabled)
    }
}
@Composable internal fun Amount(label: String, cents: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = MasroofSpacing.inlineGap)) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MasroofAmountText(formatLocalizedMoney(analysisAmount(cents)), MasroofAmountRole.Card, maxLines = 2)
    }
}
@Composable internal fun day(value: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy", LocalConfiguration.current.locales[0]).format(LocalDate.ofEpochDay(value))
@Composable internal fun timestamp(value: Long): String = DateTimeFormatter.ofPattern("d MMM · HH:mm", LocalConfiguration.current.locales[0]).format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))
