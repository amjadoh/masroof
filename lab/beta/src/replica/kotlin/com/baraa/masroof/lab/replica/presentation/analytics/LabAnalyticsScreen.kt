package com.baraa.masroof.lab.replica.presentation.analytics

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.replica.application.analytics.*
import com.baraa.masroof.lab.replica.presentation.common.*
import com.baraa.masroof.lab.replica.presentation.common.MasroofText as Text
import com.baraa.masroof.lab.replica.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.lab.replica.presentation.settings.ManagedCommitmentUi
import com.baraa.masroof.lab.replica.presentation.settings.SettingsCommitmentEditorScreen
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
        Text(stringResource(R.string.lab_analysis_card_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Status(state: AnalysisUiState) {
    val label = when {
        state.unavailable -> R.string.lab_analysis_failed
        state.updating -> R.string.lab_analysis_updating
        state.snapshot?.status != "READY" -> R.string.lab_analysis_insufficient
        state.snapshot.quality.blocked -> R.string.lab_analysis_quality_warning
        state.snapshot.method == "WEEKDAY_MEDIAN" -> R.string.lab_analysis_initial
        else -> R.string.lab_analysis_ready
    }
    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    state.snapshot?.let { Text(stringResource(R.string.lab_analysis_updated, timestamp(it.issuedAt)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable fun LabAnalyticsRoute(onBack: () -> Unit, onOpenTransaction: (String) -> Unit) {
    val workflow = workflow()
    val state by workflow.state.collectAsState()
    val scope = rememberCoroutineScope()
    val target by AnalyticsNavigation.target.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(if (target.isNullOrEmpty()) 0 else 2) }
    var explanation by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
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
        if (state.updating) return@LaunchedEffect
        val alert = state.alerts.firstOrNull { it.id == id }
        tab = when (alert?.kind) { "DUE" -> 1; "ACTUAL", "BUDGET" -> 0; else -> 2 }
        if (alert != null) { focusedAlert = id; workflow.markAlert(id) }
        pendingTarget = null
    }
    BackHandler(enabled = !saving) {
        when { editing != null -> editing = null; settings -> settings = false; explanation -> explanation = false; else -> onBack() }
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
    if (settings) { Preferences(workflow, state, onBack = { settings = false }); return }
    if (explanation) {
        Explanation(state, onBack = { explanation = false }, onOpenTransaction = onOpenTransaction)
        return
    }
    MasroofSecondaryScaffold(stringResource(R.string.lab_analysis_title), onBack,
        stringResource(R.string.lab_analysis_back), actions = {
            MasroofTextButton(onClick = { settings = true }) { Text(stringResource(R.string.lab_analysis_settings)) }
        }) { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            TabRow(selectedTabIndex = tab) {
                listOf(R.string.lab_analysis_purchases, R.string.lab_analysis_commitments, R.string.lab_analysis_alerts).forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { pendingTarget = null; focusedAlert = null; tab = index }, text = { Text(stringResource(label)) })
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
                state.snapshot?.let { Text("${day(it.periodStart)} — ${day(it.periodEnd - 1)}", style = MaterialTheme.typography.bodySmall) }
                Status(state)
                if (state.unavailable) MasroofTextButton(onClick = workflow::request) { Text(stringResource(R.string.lab_replica_dashboard_retry)) }
                when (tab) {
                    0 -> Purchases(state, workflow, onWhy = { explanation = true })
                    1 -> {
                        Text(stringResource(R.string.lab_analysis_commitments_hint), style = MaterialTheme.typography.bodySmall)
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
                                Text("${stringResource(if (c.cadence == "WEEKLY") R.string.lab_analysis_weekly else R.string.lab_analysis_monthly)} · ${day(c.nextDay)}")
                                Text(stringResource(R.string.lab_analysis_evidence_count, c.sourceIds.size), style = MaterialTheme.typography.bodySmall)
                                var expanded by rememberSaveable(c.key) { mutableStateOf(false) }
                                val evidence = if (expanded) c.sourceIds else c.sourceIds.takeLast(3)
                                evidence.forEachIndexed { i, id ->
                                    MasroofTextButton(onClick = { onOpenTransaction(id) }) { Text(stringResource(R.string.lab_analysis_payment_number, i + 1)) }
                                }
                                if (!expanded && c.sourceIds.size > 3) MasroofTextButton(onClick = { expanded = true }) { Text(stringResource(R.string.lab_analysis_more)) }
                                MasroofButton(onClick = { editing = c.key }, enabled = !state.updating, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.lab_analysis_review_candidate))
                                }
                                MasroofTextButton(onClick = { scope.launch { workflow.rejectCandidate(c.key) } }) { Text(stringResource(R.string.lab_analysis_reject)) }
                            }
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.lab_analysis_alerts_hint), style = MaterialTheme.typography.bodySmall)
                        if (state.alerts.isEmpty()) Text(stringResource(R.string.lab_analysis_no_alerts))
                        state.alerts.forEach { alert ->
                            FocusAnalysisItem(focusedAlert == alert.id, { focusedAlert = null }) {
                            MasroofCard(Modifier.testTag("lab-alert-${alert.id}")) {
                                val title = when (alert.kind) {
                                    "ACTUAL" -> R.string.lab_analysis_actual_alert
                                    "DUE" -> R.string.lab_analysis_due_alert
                                    "PAYMENT" -> R.string.lab_analysis_payment_alert
                                    else -> R.string.lab_analysis_budget_alert
                                }
                                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                                if (alert.title.isNotEmpty()) Text(alert.title)
                                Amount("", alert.amount)
                                if (state.updating) Text(stringResource(R.string.lab_analysis_updating))
                                if (alert.snoozedUntil > System.currentTimeMillis()) Text(stringResource(R.string.lab_analysis_snoozed))
                                MasroofTextButton(onClick = {
                                    scope.launch { workflow.markAlert(alert.id) }
                                    when (alert.kind) { "DUE" -> { focusedAlert = alert.id; tab = 1 }; "PAYMENT" -> alert.evidenceId?.let(onOpenTransaction); else -> tab = 0 }
                                }) { Text(stringResource(R.string.lab_analysis_open)) }
                                if (!alert.read) MasroofTextButton(onClick = { scope.launch { workflow.markAlert(alert.id) } }) { Text(stringResource(R.string.lab_analysis_read)) }
                                MasroofTextButton(onClick = { scope.launch { workflow.markAlert(alert.id, snooze = true) } }) { Text(stringResource(R.string.lab_analysis_snooze)) }
                            }
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

@Composable fun LabAlertsCard() {
    val state by workflow().state.collectAsState()
    MasroofCard(Modifier.fillMaxWidth().clickable { AnalyticsNavigation.open("alerts") }) {
        Text(stringResource(R.string.lab_analysis_alerts), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.lab_analysis_alert_count, state.alerts.count { !it.read && it.snoozedUntil <= System.currentTimeMillis() }))
    }
}

@Composable fun labUnreadAlerts(workflow: LabAnalyticsWorkflow?): Int {
    if (workflow == null) return 0
    val state by workflow.state.collectAsState()
    return state.alerts.count { !it.read && it.snoozedUntil <= System.currentTimeMillis() }
}

@Composable private fun Purchases(state: AnalysisUiState, workflow: LabAnalyticsWorkflow, onWhy: () -> Unit) {
    val s = state.snapshot
    MasroofCard {
        s?.let {
            Amount(stringResource(R.string.lab_analysis_actual), it.actual)
            it.remaining?.let { amount -> Amount(stringResource(R.string.lab_analysis_remaining), amount) }
            it.total?.let { amount -> Amount(stringResource(R.string.lab_analysis_expected_total), amount) }
            if (it.lower != null && it.upper != null) Text(stringResource(R.string.lab_analysis_range,
                formatLocalizedMoney(analysisAmount(it.lower)), formatLocalizedMoney(analysisAmount(it.upper))), style = MaterialTheme.typography.bodySmall)
            else Text(stringResource(R.string.lab_analysis_no_range), style = MaterialTheme.typography.bodySmall)
            if (state.preferences.budget != null) Amount(stringResource(R.string.lab_analysis_budget), state.preferences.budget)
            if (workflow.budgetStatus() == "NEAR") Text(stringResource(R.string.lab_analysis_near), color = MaterialTheme.colorScheme.primary)
        }
        if (s?.total == null) Text(stringResource(R.string.lab_analysis_need_data))
        MasroofTextButton(onClick = onWhy) { Text(stringResource(R.string.lab_analysis_why)) }
    }
    if (s != null) {
        val points = remember(s) { s.chart() }
        if (points.isNotEmpty()) MasroofCard {
            Text(stringResource(R.string.lab_analysis_chart), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.lab_analysis_chart_hint), style = MaterialTheme.typography.bodySmall)
            var selected by rememberSaveable(s.periodStart) { mutableIntStateOf(0) }
            val index = selected.coerceIn(points.indices)
            val labels = points.map { "${day(it.day)} · ${stringResource(if (it.expected) R.string.lab_analysis_estimated else R.string.lab_analysis_actual)} · ${it.amount}" }
            Text(labels[index], style = MaterialTheme.typography.bodySmall)
            MasroofInteractiveLineChart(points.map { it.amount }, java.math.BigDecimal.ZERO,
                selectedPointIndex = index, onPointSelected = { selected = it }, pointLabel = { labels[it] },
                axisLabel = { LocalDate.ofEpochDay(points[it].day).dayOfMonth.toString() },
                currencyLabel = stringResource(R.string.lab_analysis_sar))
        }
    }
    MasroofCard {
        Text(stringResource(R.string.lab_analysis_scope), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.lab_analysis_local), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.history.isNotEmpty()) MasroofCard {
        Text(stringResource(R.string.lab_analysis_history), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.lab_analysis_history_hint), style = MaterialTheme.typography.bodySmall)
        state.history.take(8).forEach { past ->
            Text(timestamp(past.issuedAt), style = MaterialTheme.typography.bodySmall)
            past.total?.let { Amount(stringResource(R.string.lab_analysis_expected_total), it) }
            state.historyActuals[past.periodStart]?.let { Amount(stringResource(R.string.lab_analysis_final_actual), it) }
        }
    }
}

@Composable private fun Explanation(state: AnalysisUiState, onBack: () -> Unit, onOpenTransaction: (String) -> Unit) {
    MasroofSecondaryScaffold(stringResource(R.string.lab_analysis_why), onBack, stringResource(R.string.lab_analysis_back)) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofCard {
                Text(stringResource(R.string.lab_analysis_models))
                Text(stringResource(R.string.lab_analysis_inputs), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.lab_analysis_need_data), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.lab_analysis_scope), style = MaterialTheme.typography.bodySmall)
            }
            state.snapshot?.let { snapshot ->
                MasroofCard {
                    Text(stringResource(R.string.lab_analysis_quality), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.lab_analysis_quality_counts, snapshot.quality.used, snapshot.quality.coveredDays, snapshot.completedPeriods, snapshot.quality.pendingReviews))
                    if (snapshot.quality.historyFrom != null && snapshot.quality.historyThrough != null) Text(stringResource(R.string.lab_analysis_history_range,
                        day(snapshot.quality.historyFrom), day(snapshot.quality.historyThrough)), style = MaterialTheme.typography.bodySmall)
                    Text("${snapshot.engineVersion} · ${snapshot.cleaningVersion} · ${snapshot.featureVersion}", style = MaterialTheme.typography.bodySmall)
                    if (!snapshot.quality.coverageKnown) Text(stringResource(R.string.lab_analysis_coverage_unknown))
                    if (snapshot.quality.blocked) Text(stringResource(R.string.lab_analysis_quality_warning))
                }
                snapshot.quality.excluded.forEach { (reason, ids) ->
                    MasroofCard {
                        Text(stringResource(when (reason) {
                            "OUTSIDE_PURCHASE_SCOPE" -> R.string.lab_analysis_excluded_scope
                            "DUPLICATE_ID" -> R.string.lab_analysis_excluded_duplicate
                            "FUTURE_DATE" -> R.string.lab_analysis_excluded_future
                            else -> R.string.lab_analysis_excluded_amount
                        }))
                        var visible by rememberSaveable(reason) { mutableIntStateOf(20) }
                        ids.take(visible).forEachIndexed { index, id -> MasroofTextButton(onClick = { onOpenTransaction(id) }) {
                            Text(stringResource(R.string.lab_analysis_payment_number, index + 1))
                        } }
                        if (ids.size > visible) MasroofTextButton(onClick = { visible += 20 }) { Text(stringResource(R.string.lab_analysis_more)) }
                    }
                }
                if (snapshot.quality.unusual.isNotEmpty()) MasroofCard {
                    Text(stringResource(R.string.lab_analysis_unusual))
                    snapshot.quality.unusual.forEachIndexed { i, id -> MasroofTextButton(onClick = { onOpenTransaction(id) }) {
                        Text(stringResource(R.string.lab_analysis_payment_number, i + 1))
                    } }
                }
            }
        }
    }
}

@Composable private fun Preferences(workflow: LabAnalyticsWorkflow, state: AnalysisUiState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val original = state.preferences
    var draft by remember { mutableStateOf(original) }
    var budget by rememberSaveable { mutableStateOf(original.budget?.let { workflow.money(it).amount.toPlainString() }.orEmpty()) }
    var reminder by rememberSaveable { mutableStateOf(original.reminderDays.toString()) }
    var invalid by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> draft = draft.copy(phone = granted); denied = !granted }
    BackHandler(enabled = saving) { }
    MasroofSecondaryScaffold(stringResource(R.string.lab_analysis_settings), { if (!saving) onBack() }, stringResource(R.string.lab_analysis_back)) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofCard {
                MasroofFormField(value = budget, onValueChange = { budget = it }, label = { Text(stringResource(R.string.lab_analysis_budget)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("lab-analysis-budget"))
                MasroofFormField(value = reminder, onValueChange = { reminder = it }, label = { Text(stringResource(R.string.lab_analysis_reminder_days)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PreferenceToggle(stringResource(R.string.lab_analysis_budget_alert), draft.budgetAlerts, !saving) { draft = draft.copy(budgetAlerts = it) }
                PreferenceToggle(stringResource(R.string.lab_analysis_due_alert), draft.dueAlerts, !saving) { draft = draft.copy(dueAlerts = it) }
                PreferenceToggle(stringResource(R.string.lab_analysis_payment_alert), draft.paymentAlerts, !saving) { draft = draft.copy(paymentAlerts = it) }
                PreferenceToggle(stringResource(R.string.lab_analysis_phone), draft.phone, !saving) { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else draft = draft.copy(phone = enabled)
                }
                Text(stringResource(R.string.lab_analysis_shadow), style = MaterialTheme.typography.bodySmall)
                if (denied) Text(stringResource(R.string.lab_analysis_permission_denied))
                if (invalid) Text(stringResource(R.string.lab_analysis_invalid), color = MaterialTheme.colorScheme.error)
            }
            MasroofButton(onClick = {
                val parsed = runCatching { workflow.parseBudget(budget) }
                val days = reminder.toIntOrNull()
                if (parsed.isFailure || days == null || days !in 1..30) invalid = true else {
                    saving = true
                    scope.launch {
                        try { workflow.updatePreferences(draft.copy(budget = parsed.getOrNull(), reminderDays = days)); onBack() }
                        catch (_: Exception) { invalid = true } finally { saving = false }
                    }
                }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lab_analysis_save)) }
            MasroofOutlinedButton(onClick = onBack, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lab_analysis_cancel)) }
        }
    }
}

@Composable private fun PreferenceToggle(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
        Text(title, Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled)
    }
}
@Composable private fun Amount(label: String, cents: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = MasroofSpacing.inlineGap)) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MasroofAmountText(formatLocalizedMoney(analysisAmount(cents)), MasroofAmountRole.Card, maxLines = 2)
    }
}
@Composable private fun day(value: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy", LocalConfiguration.current.locales[0]).format(LocalDate.ofEpochDay(value))
@Composable private fun timestamp(value: Long): String = DateTimeFormatter.ofPattern("d MMM · HH:mm", LocalConfiguration.current.locales[0]).format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))
