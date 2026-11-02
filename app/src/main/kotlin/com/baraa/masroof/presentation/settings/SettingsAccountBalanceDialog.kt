package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.baraa.masroof.R
import com.baraa.masroof.application.settings.AccountBalanceInput
import com.baraa.masroof.presentation.common.MasroofGuidanceLabel
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate
import com.baraa.masroof.ui.theme.MasroofSpacing
import java.time.ZoneId
import com.baraa.masroof.presentation.common.MasroofFormField as OutlinedTextField
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.common.MasroofTextButton as TextButton

/** Settings actions for an owned account's opening balance (start point and SMS matching). */
class AccountBalanceActions(
    val onOpen: (ManagedAccountUi) -> Unit,
    val onDismiss: () -> Unit,
    val onSave: (java.math.BigDecimal) -> Unit,
    val onAdoptStated: () -> Unit,
    val onClear: () -> Unit,
)

@Composable
fun SettingsAccountBalanceDialog(editor: AccountBalanceEditorUi?, actions: AccountBalanceActions) {
    editor ?: return
    val key = "${editor.account.bank.id}:${editor.account.maskedNumber}"
    var text by rememberSaveable(key) { mutableStateOf("") }
    val parsed = AccountBalanceInput.parse(text)
    val enabled = !editor.loading && !editor.busy
    val zone = ZoneId.systemDefault()
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = {
            MasroofGuidanceLabel(stringResource(R.string.settings_account_balance_title, registryLabel(editor.account)),
                stringResource(R.string.settings_account_balance_hint), style = MaterialTheme.typography.titleLarge,
                contentColor = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
                editor.opening?.let { opening ->
                    Text(stringResource(R.string.settings_account_balance_current,
                        formatLocalizedMoney(opening.opening), formatLocalizedTransactionDate(opening.date, "d MMMM yyyy")),
                        style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { MasroofGuidanceLabel(stringResource(R.string.settings_account_balance_amount),
                        stringResource(R.string.settings_account_balance_amount_hint)) },
                    isError = text.isNotBlank() && parsed == null,
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().testTag("account-balance-amount"),
                )
                when {
                    editor.loading -> Text(stringResource(R.string.settings_account_balance_stated_loading),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    editor.stated != null -> {
                        val stated = editor.stated
                        Text(stringResource(R.string.settings_account_balance_stated, formatLocalizedMoney(stated.amount),
                            formatLocalizedTransactionDate(stated.at.atZone(zone).toLocalDate(), "d MMMM yyyy")),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = actions.onAdoptStated, enabled = enabled, modifier = Modifier.testTag("account-balance-adopt")) {
                            Text(stringResource(R.string.settings_account_balance_adopt))
                        }
                    }
                    else -> Text(stringResource(R.string.settings_account_balance_stated_none),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (editor.opening != null) {
                    TextButton(onClick = actions.onClear, enabled = enabled, modifier = Modifier.testTag("account-balance-clear")) {
                        Text(stringResource(R.string.settings_account_balance_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (editor.failed) Text(stringResource(R.string.settings_update_failed), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(actions.onSave) }, enabled = enabled && parsed != null,
                modifier = Modifier.testTag("account-balance-save")) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismiss, enabled = !editor.busy) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
