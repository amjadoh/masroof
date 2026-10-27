package com.baraa.masroof.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.onboarding.HistoricalImportUserOutcome
import com.baraa.masroof.application.onboarding.userOutcome
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.ui.theme.MasroofSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSmsImportScreen(state: SettingsUiState, bankId: String?, onBack: () -> Unit,
    onImport: (Set<String>, LocalDate, LocalDate) -> Unit, onClearResult: () -> Unit,
    onRequestPermission: () -> Unit, onOpenAppSettings: () -> Unit, onOpenBank: (String) -> Unit,
    onRetry: () -> Unit = {}) {
    val today = LocalDate.now()
    var fromText by rememberSaveable { mutableStateOf(today.minusDays(30).toString()) }
    var throughText by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedIds by rememberSaveable(bankId) { mutableStateOf(listOfNotNull(bankId)) }
    var choosingDate by rememberSaveable { mutableStateOf<String?>(null) }
    var initialized by rememberSaveable(bankId) { mutableStateOf(false) }
    val from = LocalDate.parse(fromText)
    val through = LocalDate.parse(throughText)
    val banks = state.bankCatalog?.banks.orEmpty()
    val selected = selectedIds.filter { id -> banks.any { it.bankId == id } }.toSet()
    val busy = state.importingSms
    val close = { if (!busy) onBack() }
    BackHandler(onBack = close)
    LaunchedEffect(bankId) { if (!initialized) { onClearResult(); initialized = true } }
    choosingDate?.let { field ->
        val date = if (field == "from") from else through
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val draft = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        MasroofBottomSheet(stringResource(if (field == "from") R.string.settings_import_from else R.string.settings_import_through),
            onDismiss = { choosingDate = null }, onApply = {
                draft?.let { if (field == "from") fromText = it.toString() else throughText = it.toString() }
                choosingDate = null; onClearResult()
            }, applyLabel = stringResource(R.string.transaction_list_filter_apply), canApply = draft != null && !draft.isAfter(today)) {
            DatePicker(picker, modifier = Modifier.testTag("bank-import-date-picker"), showModeToggle = false)
        }
    }
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
            MasroofCard {
                MasroofText(stringResource(R.string.settings_import_banks), style = MaterialTheme.typography.titleMedium)
                if (bankId != null) MasroofText(settingsBankLabel(Bank(bankId)))
                else MasroofSheetChoices(banks.map { it.bankId }, selected, { settingsBankLabel(Bank(it)) }, { id ->
                    if (!busy) {
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        onClearResult()
                    }
                }, { "import-bank-$it" })
            }
            MasroofCard {
                MasroofText(stringResource(R.string.settings_import_period), style = MaterialTheme.typography.titleMedium)
                MasroofReferenceNavigationRow(stringResource(R.string.settings_import_from), from.toString(), MasroofIcons.calendar,
                    { if (!busy) choosingDate = "from" }, modifier = Modifier.testTag("import-from"))
                MasroofReferenceNavigationRow(stringResource(R.string.settings_import_through), through.toString(), MasroofIcons.calendar,
                    { if (!busy) choosingDate = "through" }, divider = false, modifier = Modifier.testTag("import-through"))
                MasroofText(stringResource(R.string.settings_import_period_hint), style = MaterialTheme.typography.bodySmall)
                if (from.isAfter(through) || through.isAfter(today)) MasroofText(stringResource(R.string.settings_import_invalid_period), color = MaterialTheme.colorScheme.error)
            }
            MasroofText(stringResource(R.string.settings_import_scope_hint), style = MaterialTheme.typography.bodySmall)
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
                    if (selected.size == 1) MasroofOutlinedButton({ onOpenBank(selected.single()) }, enabled = !busy) {
                        MasroofText(stringResource(R.string.settings_import_open_bank))
                    }
                }
            }
        }
    }
}
