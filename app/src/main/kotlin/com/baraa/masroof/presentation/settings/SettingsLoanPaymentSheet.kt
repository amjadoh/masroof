package com.baraa.masroof.presentation.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.locale.formatLocalizedMoney

@Composable
internal fun SettingsLoanPaymentSheet(state: SettingsUiState, onClose: () -> Unit,
    onPreview: (String) -> Unit, onConfirm: () -> Unit, onReset: () -> Unit) {
    val loan = state.loanPaymentTarget ?: return
    var biller by rememberSaveable(loan.id) { mutableStateOf("") }
    val preview = state.loanPaymentPreview
    MasroofBottomSheet(
        title = stringResource(R.string.settings_loan_payment_link),
        subtitle = "${settingsBankLabel(loan.bank)} · ${loanDisplayLabel(loan)}",
        onDismiss = onClose, busy = state.loanPaymentBusy,
        onApply = { if (preview == null) onPreview(biller) else onConfirm() },
        canApply = biller.isNotBlank(),
        applyLabel = stringResource(if (preview == null) R.string.settings_loan_payment_preview else R.string.settings_loan_payment_confirm),
        resetLabel = if (preview != null) stringResource(R.string.settings_loan_payment_edit) else null,
        onReset = if (preview != null) onReset else null,
    ) {
        MasroofHintBox(stringResource(R.string.settings_loan_payment_hint))
        MasroofFormField(value = biller, onValueChange = { biller = it },
            label = { MasroofText(stringResource(R.string.settings_loan_payment_biller)) },
            singleLine = true, enabled = !state.loanPaymentBusy && preview == null)
        preview?.let {
            MasroofText(stringResource(R.string.settings_loan_payment_count, it.transactionCount))
            it.totals.forEach { total -> MasroofMoneyRow(stringResource(R.string.settings_loan_payment_total), formatLocalizedMoney(total)) }
            if (it.unconfirmedCount > 0) MasroofHintBox(stringResource(R.string.settings_loan_payment_unconfirmed, it.unconfirmedCount))
        }
        if (state.loanPaymentFailed) MasroofText(stringResource(R.string.settings_loan_payment_failed), color = MaterialTheme.colorScheme.error)
    }
}
