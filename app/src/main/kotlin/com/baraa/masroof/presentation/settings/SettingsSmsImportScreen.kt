package com.baraa.masroof.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.onboarding.HistoricalImportUserOutcome
import com.baraa.masroof.application.onboarding.userOutcome
import com.baraa.masroof.application.patterns.PatternApprovalStatus
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.ui.theme.MasroofSpacing
import java.time.LocalDate

@Composable
fun SettingsSmsImportScreen(state: SettingsUiState, bankId: String?, onBack: () -> Unit,
    onImport: (Set<String>, LocalDate, LocalDate) -> Unit, onClearResult: () -> Unit,
    onRequestPermission: () -> Unit, onOpenAppSettings: () -> Unit, onOpenBank: (String) -> Unit,
    onRetry: () -> Unit = {}, onOpenPatterns: ((String) -> Unit)? = null) {
    val today = LocalDate.now()
    var fromText by rememberSaveable { mutableStateOf(today.minusDays(30).toString()) }
    var throughText by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedIds by rememberSaveable(bankId) { mutableStateOf(listOfNotNull(bankId)) }
    var initialized by rememberSaveable(bankId) { mutableStateOf(false) }
    val from = LocalDate.parse(fromText)
    val through = LocalDate.parse(throughText)
    val banks = state.bankCatalog?.banks.orEmpty()
    val selected = selectedIds.filter { id -> banks.any { it.bankId == id } }.toSet()
    val busy = state.importingSms
    val close = { if (!busy) onBack() }
    BackHandler(onBack = close)
    LaunchedEffect(bankId) { if (!initialized) { onClearResult(); initialized = true } }
    MasroofSecondaryScaffold(stringResource(R.string.settings_import_sms_title), close, stringResource(R.string.settings_back)) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            if (!state.smsPermissionGranted) SmsPermissionNotice(onRequestPermission, onOpenAppSettings)
            if (state.bankCatalog == null) {
                if (state.loading) CircularProgressIndicator()
                else {
                    MasroofText(stringResource(R.string.settings_update_failed), color = MaterialTheme.colorScheme.error)
                    MasroofOutlinedButton(onRetry) { MasroofText(stringResource(R.string.dashboard_retry)) }
                }
            }
            if (bankId != null) MasroofCard {
                MasroofGuidanceLabel(stringResource(R.string.settings_import_banks),
                    stringResource(R.string.settings_import_scope_hint), style = MaterialTheme.typography.titleMedium)
                MasroofText(settingsBankLabel(Bank(bankId)))
            } else SettingsImportBankChoices(banks, selected, !busy) { id ->
                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                onClearResult()
            }
            SettingsImportPeriod(from, through, !busy,
                { fromText = it.toString(); onClearResult() }, { throughText = it.toString(); onClearResult() })
            if (banks.any { bank -> bank.bankId in selected && bank.currentPatterns.any { it.status == PatternApprovalStatus.PREVIEW_ONLY } }) {
                MasroofCard { MasroofGuidanceLabel(stringResource(R.string.pattern_approval_title),
                    stringResource(R.string.pattern_approval_import_hint)) }
            }
            if (busy) {
                CircularProgressIndicator()
                MasroofText(stringResource(R.string.onboarding_import_scanning))
            }
            MasroofButton({ onImport(selected, from, through) }, Modifier.fillMaxWidth().testTag("import-selected-sms"),
                enabled = !busy && selected.isNotEmpty() && !from.isAfter(through) && !through.isAfter(today) && state.smsPermissionGranted) {
                MasroofText(stringResource(R.string.onboarding_start_import))
            }
            if (state.scopedSmsImportFailed) MasroofText(stringResource(R.string.dashboard_rescan_failed), color = MaterialTheme.colorScheme.error)
            state.scopedSmsImportResult?.let { result ->
                val outcome = result.userOutcome()
                MasroofCard {
                    MasroofText(stringResource(when (outcome) {
                        HistoricalImportUserOutcome.FAILED -> R.string.dashboard_rescan_failed
                        HistoricalImportUserOutcome.PERMISSION_DENIED -> R.string.settings_import_sms_permission_denied
                        HistoricalImportUserOutcome.ALREADY_UP_TO_DATE -> R.string.dashboard_rescan_already_up_to_date
                        HistoricalImportUserOutcome.NO_MESSAGES -> R.string.dashboard_rescan_no_messages
                        HistoricalImportUserOutcome.NO_BANK_SMS -> R.string.settings_import_no_selected_bank_sms
                        HistoricalImportUserOutcome.NEEDS_REVIEW -> R.string.dashboard_rescan_needs_review
                        HistoricalImportUserOutcome.NO_NEW_TRANSACTIONS -> R.string.dashboard_rescan_no_transactions
                        HistoricalImportUserOutcome.OK -> R.string.dashboard_rescan_ok
                    }), style = MaterialTheme.typography.titleMedium)
                    MasroofText(stringResource(R.string.settings_import_result_counts, result.inserted, result.duplicates, result.reviewRequired, result.failed))
                    if (selected.size == 1 && onOpenPatterns != null) MasroofButton({ onOpenPatterns(selected.single()) }, enabled = !busy) {
                        MasroofText(stringResource(R.string.pattern_approval_open_patterns))
                    }
                    if (selected.size == 1) MasroofOutlinedButton({ onOpenBank(selected.single()) }, enabled = !busy) {
                        MasroofText(stringResource(R.string.settings_import_open_bank))
                    }
                }
            }
        }
    }
}
