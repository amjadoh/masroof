package com.baraa.masroof.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.MessagePatternsWorkflow
import com.baraa.masroof.application.patterns.PatternCatalog
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsBankEditorScreen(catalog: PatternCatalog, bankId: String?, workflow: MessagePatternsWorkflow,
    onBack: () -> Unit, onSaved: (String) -> Unit) {
    val bank = catalog.banks.find { it.bankId == bankId }
    var name by rememberSaveable(bankId) { mutableStateOf(bank?.displayName.orEmpty()) }
    var sender by rememberSaveable(bankId) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val close = { if (!busy) onBack() }
    BackHandler(onBack = close)
    MasroofSecondaryScaffold(stringResource(if (bankId == null) R.string.settings_add_bank else R.string.settings_bank_details),
        close, stringResource(R.string.settings_back)) { modifier ->
        Column(modifier.imePadding().fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofCard {
                MasroofFormField(name, { name = it }, label = { MasroofText(stringResource(R.string.pattern_bank_name)) },
                    enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("bank-name"))
                if (bank != null) {
                    MasroofText(stringResource(R.string.settings_bank_senders))
                    bank.senders.forEach { MasroofText(it) }
                }
                MasroofFormField(sender, { sender = it },
                    label = { MasroofText(stringResource(if (bank == null) R.string.pattern_sender else R.string.settings_bank_extra_sender)) },
                    enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("bank-sender"))
                MasroofText(stringResource(R.string.settings_bank_sender_hint))
            }
            if (failed) MasroofText(stringResource(R.string.settings_bank_save_failed), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            MasroofButton(onClick = {
                if (!busy) {
                    busy = true; failed = false
                    scope.launch {
                        try {
                            val updated = if (bank == null) workflow.addBank(catalog.generation, name, sender, bankId)
                            else workflow.updateBank(catalog.generation, bank.bankId, name, bank.senders + listOfNotNull(sender.trim().takeIf { it.isNotEmpty() }))
                            val id = bank?.bankId ?: updated.banks.single { added -> catalog.banks.none { it.bankId == added.bankId } }.bankId
                            onSaved(id)
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                }
            }, enabled = !busy && name.isNotBlank() && (bank != null || sender.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                MasroofText(stringResource(R.string.settings_save))
            }
            MasroofText(stringResource(R.string.settings_bank_save_hint))
        }
    }
}
