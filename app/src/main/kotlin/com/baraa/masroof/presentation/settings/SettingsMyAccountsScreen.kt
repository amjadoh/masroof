package com.baraa.masroof.presentation.settings

import com.baraa.masroof.ui.theme.MasroofSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.common.MasroofTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.AccountType

@Composable
fun SettingsAccountTypeDialog(
    target: ManagedAccountUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onSelect: (AccountType) -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_account_type_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
                AccountType.entries.forEach { type ->
                    TextButton(
                        onClick = { onSelect(type) },
                        enabled = !updating && type != target.accountType,
                    ) {
                        Text(accountTypeLabel(type))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !updating) {
                Text(stringResource(R.string.settings_action_cancel))
            }
        },
    )
}

@Composable
private fun accountTypeLabel(accountType: AccountType): String =
    when (accountType) {
        AccountType.CURRENT -> stringResource(R.string.settings_account_type_current)
        AccountType.SAVINGS -> stringResource(R.string.settings_account_type_savings)
        AccountType.WALLET -> stringResource(R.string.settings_account_type_wallet)
    }
