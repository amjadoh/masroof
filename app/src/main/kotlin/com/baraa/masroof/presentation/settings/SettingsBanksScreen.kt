package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.PatternApprovalStatus
import com.baraa.masroof.application.patterns.PatternBankView
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.ui.theme.MasroofSpacing

@Composable
fun SettingsBanksScreen(state: SettingsUiState, onBack: () -> Unit, onOpenBank: (String) -> Unit,
    onAddBank: (() -> Unit)? = null, onImport: (() -> Unit)? = null,
    title: String = stringResource(R.string.settings_banks_title)) {
    MasroofSecondaryScaffold(title, onBack, stringResource(R.string.settings_back)) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            if (state.bankSummaries.isEmpty()) MasroofText(stringResource(R.string.settings_banks_empty))
            else MasroofCard {
                state.bankSummaries.forEachIndexed { index, summary ->
                    val definition = state.bankCatalog?.banks?.find { it.bankId == summary.bank.id }
                    MasroofReferenceNavigationRow(settingsBankLabel(summary.bank),
                        definition?.let { bankSupportLabel(it) } ?: stringResource(R.string.settings_bank_registry_only),
                        MasroofIcons.moneyMovement, { onOpenBank(summary.bank.id) },
                        divider = index != state.bankSummaries.lastIndex)
                }
            }
            onAddBank?.let { action -> MasroofButton(action, Modifier.fillMaxWidth()) { MasroofText(stringResource(R.string.settings_add_bank)) } }
            onImport?.let { action ->
                MasroofOutlinedButton(action, Modifier.fillMaxWidth(), enabled = state.bankCatalog?.banks?.isNotEmpty() == true) {
                    MasroofText(stringResource(R.string.settings_import_selected_banks))
                }
            }
        }
    }
}

@Composable
internal fun bankSupportLabel(bank: PatternBankView): String {
    val active = bank.currentPatterns.filter { it.enabled }
    return stringResource(when {
        active.isEmpty() && bank.currentPatterns.isNotEmpty() -> R.string.settings_bank_patterns_disabled
        active.isEmpty() -> R.string.settings_bank_needs_teaching
        active.all { it.status == PatternApprovalStatus.BUNDLED } -> R.string.settings_bank_bundled
        active.none { it.canApply } -> R.string.settings_bank_needs_approval
        active.any { !it.canApply } -> R.string.settings_bank_partly_approved
        else -> R.string.settings_bank_approved
    })
}
