package com.baraa.masroof.lab.beta.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaIo
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.presentation.theme.BetaSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A settings route for Beta-owned preferences and local snapshots only. */
@Composable
fun BetaSettingsPanel(container: BetaContainer, onClose: () -> Unit, onBusyChanged: (Boolean) -> Unit = {}, onChanged: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var result by rememberSaveable { mutableStateOf("IDLE") }
    var theme by remember { mutableStateOf("SYSTEM") }
    var backupAvailable by remember { mutableStateOf(false) }
    suspend fun refresh() {
        val settings = withContext(BetaIo.dispatcher) { container.settings() }
        theme = settings.theme; backupAvailable = settings.backupAvailable
    }
    LaunchedEffect(Unit) {
        try { refresh() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { result = "FAILED" }
    }
    fun perform(action: () -> Boolean) {
        if (busy) return
        busy = true; onBusyChanged(true); result = "IDLE"
        scope.launch {
            try {
                val succeeded = withContext(BetaIo.dispatcher) { action() }
                refresh(); result = if (succeeded) "SUCCESS" else "MISSING"; onChanged()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { result = "FAILED" }
            finally { busy = false; onBusyChanged(false) }
        }
    }
    BackHandler { if (!busy) onClose() }
    Column(verticalArrangement = Arrangement.spacedBy(BetaSpacing.section), modifier = Modifier.testTag("beta-settings-panel")) {
        Text(stringResource(R.string.beta_settings_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.beta_settings_isolation))
        Text(stringResource(R.string.beta_settings_theme), style = MaterialTheme.typography.titleMedium)
        listOf("SYSTEM" to R.string.beta_theme_system, "LIGHT" to R.string.beta_theme_light, "DARK" to R.string.beta_theme_dark).forEach { (value, label) ->
            OutlinedButton(enabled = !busy && theme != value, onClick = { perform { container.setTheme(value); true } }, modifier = Modifier.fillMaxWidth().testTag("beta-theme-$value")) { Text(stringResource(label)) }
        }
        Text(stringResource(if (backupAvailable) R.string.beta_settings_backup_available else R.string.beta_settings_backup_missing))
        Text(stringResource(R.string.beta_settings_backup_help))
        OutlinedButton(enabled = !busy, onClick = { perform { container.backup(); true } }, modifier = Modifier.fillMaxWidth().testTag("beta-settings-backup")) { Text(stringResource(R.string.beta_backup)) }
        OutlinedButton(enabled = !busy && backupAvailable, onClick = { confirmation = "RESTORE" }, modifier = Modifier.fillMaxWidth().testTag("beta-settings-restore")) { Text(stringResource(R.string.beta_restore)) }
        OutlinedButton(enabled = !busy, onClick = { confirmation = "RESET" }, modifier = Modifier.fillMaxWidth().testTag("beta-settings-reset")) { Text(stringResource(R.string.beta_reset)) }
        if (busy) Text(stringResource(R.string.beta_settings_busy), modifier = Modifier.testTag("beta-settings-busy"))
        if (result != "IDLE") Text(stringResource(when (result) {
            "SUCCESS" -> R.string.beta_settings_success
            "MISSING" -> R.string.beta_restore_missing
            else -> R.string.beta_settings_failed
        }), modifier = Modifier.testTag("beta-settings-result"), color = if (result == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        OutlinedButton(enabled = !busy, onClick = onClose, modifier = Modifier.testTag("beta-settings-close")) { Text(stringResource(R.string.beta_asset_cancel)) }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { if (!busy) confirmation = null },
            title = { Text(stringResource(if (action == "RESET") R.string.beta_reset else R.string.beta_restore)) },
            text = { Text(stringResource(if (action == "RESET") R.string.beta_settings_reset_confirm else R.string.beta_settings_restore_confirm)) },
            confirmButton = { Button(enabled = !busy, onClick = { confirmation = null; perform { if (action == "RESET") { container.reset(); true } else container.restore() } }, modifier = Modifier.testTag("beta-settings-confirm")) { Text(stringResource(R.string.beta_settings_confirm)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmation = null }, modifier = Modifier.testTag("beta-settings-cancel")) { Text(stringResource(R.string.beta_asset_cancel)) } })
    }
}
