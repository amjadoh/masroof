package com.baraa.masroof.lab.beta.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.baraa.masroof.lab.beta.BetaIo
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.application.history.HistorySession
import com.baraa.masroof.lab.beta.presentation.theme.BetaSpacing
import kotlinx.coroutines.*

/** Explicit optional import. No reads, jobs or automatic resume on entering this screen. */
@Composable
internal fun BetaHistoryPanel(container: BetaContainer, onClose: () -> Unit, onBusyChanged: (Boolean) -> Unit = {}) {
    var selectedEvidence by rememberSaveable { mutableStateOf<String?>(null) }
    selectedEvidence?.let { id ->
        BetaInterpretationReviewPanel(container, id, onClose = { selectedEvidence = null }, onBusyChanged = onBusyChanged)
        return
    }
    val context = LocalContext.current
    val scope = rememberBetaUiScope()
    var session by remember { mutableStateOf<HistorySession?>(null) }
    var page by remember { mutableStateOf<List<EvidenceSummary>>(emptyList()) }
    var after by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var days by rememberSaveable { mutableIntStateOf(30) }
    var pendingDays by rememberSaveable { mutableStateOf<Int?>(null) }
    suspend fun refresh() {
        val snapshot = withContext(BetaIo.dispatcher) { container.historySession() to container.evidencePage(after) }
        session = snapshot.first
        page = snapshot.second
    }
    SideEffect { onBusyChanged(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try { block(); refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                failed = true
                // The durable checkpoint can advance even when a later batch operation fails.
                try { refresh() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Keep the failure visible; reopening may recover storage. */ }
            }
            finally { busy = false; running = false }
        }
    }
    fun scan(newDays: Int?) = action {
        if (newDays != null) session = withContext(BetaIo.dispatcher) { container.startHistory(newDays) }
        running = true
        do {
            val current = withContext(BetaIo.dispatcher) { container.stepHistory() }
            session = current
            yield()
        } while (running && !current.finished && !current.limited)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = pendingDays
        pendingDays = null
        denied = !granted
        // A grant does not authorize an automatic restarted import; user explicitly resumes/starts.
        if (granted && requested != null) scan(requested.takeIf { it != 0 })
    }
    BetaLaunchedEffect(Unit) {
        try { refresh() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(BetaSpacing.section)) {
        Text(stringResource(R.string.beta_history_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.beta_history_help))
        Text(stringResource(R.string.beta_history_scope))
        listOf(7, 30, 90).forEach { value ->
            OutlinedButton(enabled = !busy, onClick = { days = value }, modifier = Modifier.testTag("history-days-$value")) {
                Text(stringResource(if (days == value) R.string.beta_history_days_selected else R.string.beta_history_days, value))
            }
        }
        if (denied) Text(stringResource(R.string.beta_history_denied), modifier = Modifier.testTag("history-denied"))
        if (failed) Text(stringResource(R.string.beta_action_failed), color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy, onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) scan(days)
            else { pendingDays = days; permission.launch(Manifest.permission.READ_SMS) }
        }, modifier = Modifier.testTag("history-start")) { Text(stringResource(R.string.beta_history_start)) }
        session?.let { current ->
            val formatter = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .withLocale(java.util.Locale.getDefault()).withZone(java.time.ZoneId.systemDefault())
            Text(stringResource(R.string.beta_history_range, formatter.format(java.time.Instant.ofEpochMilli(current.from)),
                formatter.format(java.time.Instant.ofEpochMilli(current.until))))
            current.banks.forEach { Text(stringResource(bankName(it))) }
            Text(stringResource(R.string.beta_history_progress, current.scanned, current.stored, current.excluded, current.conflicts), modifier = Modifier.testTag("history-progress"))
            Text(stringResource(when { current.finished -> R.string.beta_history_done; current.limited -> R.string.beta_history_limited; else -> R.string.beta_history_paused }))
            if (!current.finished && !current.limited) OutlinedButton(enabled = !busy, onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) scan(null)
                else { denied = true; pendingDays = 0; permission.launch(Manifest.permission.READ_SMS) }
            }, modifier = Modifier.testTag("history-resume")) { Text(stringResource(R.string.beta_history_resume)) }
        }
        if (busy) {
            CircularProgressIndicator()
            if (running) OutlinedButton(onClick = { running = false }, modifier = Modifier.testTag("history-pause")) {
                Text(stringResource(R.string.beta_history_pause))
            }
        }
        OutlinedButton(enabled = !busy, onClick = { action { withContext(BetaIo.dispatcher) { container.retryEvidence(100) } } }, modifier = Modifier.testTag("history-retry")) {
            Text(stringResource(R.string.beta_history_retry))
        }
        Text(stringResource(R.string.beta_history_saved), style = MaterialTheme.typography.titleMedium)
        page.forEachIndexed { index, summary ->
            Text(stringResource(R.string.beta_history_row, index + 1, stringResource(when (summary.processingState) {
                EvidenceProcessingState.RETRY -> R.string.beta_history_pending
                EvidenceProcessingState.REVIEW -> R.string.beta_history_review
                EvidenceProcessingState.COMPARISON_ONLY -> R.string.beta_history_compared
                EvidenceProcessingState.INTERPRETED -> R.string.beta_history_interpreted
            })))
            OutlinedButton(enabled = !busy, onClick = { selectedEvidence = summary.id }, modifier = Modifier.testTag("history-review-$index")) {
                Text(stringResource(R.string.beta_label_title))
            }
            OutlinedButton(enabled = !busy, onClick = { action { withContext(BetaIo.dispatcher) { container.reprocessEvidence(summary.id) } } }, modifier = Modifier.testTag("history-reparse-$index")) {
                Text(stringResource(R.string.beta_history_reparse))
            }
        }
        if (page.isNotEmpty()) OutlinedButton(enabled = !busy, onClick = { after = page.last().id; action {} }) { Text(stringResource(R.string.beta_history_next)) }
        if (after.isNotEmpty()) OutlinedButton(enabled = !busy, onClick = { after = ""; action {} }) { Text(stringResource(R.string.beta_history_first)) }
        OutlinedButton(enabled = !busy, onClick = onClose, modifier = Modifier.testTag("history-close")) { Text(stringResource(R.string.beta_asset_cancel)) }
    }
}
