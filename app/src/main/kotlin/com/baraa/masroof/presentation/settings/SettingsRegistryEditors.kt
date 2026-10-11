package com.baraa.masroof.presentation.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.baraa.masroof.presentation.theme.MasroofSpacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofFormField as OutlinedTextField
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.common.MasroofTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.presentation.common.formatCardLast4

@Composable
fun SettingsRenameCardDialog(
    target: ManagedCardUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    target ?: return
    var name by rememberSaveable(target.last4) { mutableStateOf(target.displayName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rename_card_title)) },
        text = {
            OutlinedTextField(
                visualTransformation = com.baraa.masroof.presentation.locale.AppDigitsVisualTransformation,
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_display_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = !updating) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
fun SettingsRenameAccountDialog(
    target: ManagedAccountUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    target ?: return
    var name by rememberSaveable(target.maskedNumber) { mutableStateOf(target.displayName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rename_account_title)) },
        text = {
            OutlinedTextField(
                visualTransformation = com.baraa.masroof.presentation.locale.AppDigitsVisualTransformation,
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_display_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = !updating) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
fun SettingsCardNetworkDialog(
    target: ManagedCardUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onSelect: (CardNetwork?) -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_card_network_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
                CardNetwork.entries.filter { it != CardNetwork.UNKNOWN }.forEach { network ->
                    Text(
                        text = cardNetworkLabel(network),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !updating) { onSelect(network) }
                            .padding(vertical = MasroofSpacing.cardInnerGap),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_card_network_clear),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !updating) { onSelect(null) }
                        .padding(vertical = MasroofSpacing.cardInnerGap),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
fun SettingsCardRoleDialog(
    target: ManagedCardUi?,
    primaryCards: List<ManagedCardUi>,
    updating: Boolean,
    onDismiss: () -> Unit,
    onSetPrimary: () -> Unit,
    onSetSupplementary: (String) -> Unit,
    onClearRole: () -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_card_role_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
                Text(
                    text = stringResource(R.string.settings_card_role_primary),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !updating) { onSetPrimary() }
                        .padding(vertical = MasroofSpacing.cardInnerGap),
                )
                primaryCards
                    .filter { it.last4 != target.last4 }
                    .forEach { primary ->
                        Text(
                            text = stringResource(
                                R.string.settings_card_role_supplementary_to,
                                formatCardLast4(primary.last4),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !updating) { onSetSupplementary(primary.last4) }
                                .padding(vertical = MasroofSpacing.cardInnerGap),
                        )
                    }
                Text(
                    text = stringResource(R.string.settings_card_role_clear),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !updating) { onClearRole() }
                        .padding(vertical = MasroofSpacing.cardInnerGap),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
fun SettingsLinkDebitDialog(
    target: ManagedCardUi?,
    accounts: List<ManagedAccountUi>,
    updating: Boolean,
    onDismiss: () -> Unit,
    onLink: (ManagedAccountUi) -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_link_debit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
                accounts.forEach { account ->
                    Text(
                        text = registryLabel(account),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !updating) { onLink(account) }
                            .padding(vertical = MasroofSpacing.cardInnerGap),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsCardMetadataActions(
    card: ManagedCardUi,
    enabled: Boolean,
    onRename: () -> Unit,
    onPickNetwork: () -> Unit,
    onPickRole: () -> Unit,
    onLinkDebit: () -> Unit,
    onMarkDebit: () -> Unit,
    includeRename: Boolean = true,
    onStop: (() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = MasroofSpacing.sectionGap),
        horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap),
        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
        if (includeRename) com.baraa.masroof.presentation.common.MasroofManagementAction(stringResource(R.string.settings_action_rename), onRename, enabled = enabled)
        com.baraa.masroof.presentation.common.MasroofManagementAction(stringResource(R.string.settings_action_network), onPickNetwork, enabled = enabled)
        if (card.cardType == CardType.CREDIT) com.baraa.masroof.presentation.common.MasroofManagementAction(stringResource(R.string.settings_action_role), onPickRole, enabled = enabled)
        if (card.cardType == CardType.DEBIT || card.cardNetwork == CardNetwork.MADA || card.cardType == null)
            com.baraa.masroof.presentation.common.MasroofManagementAction(stringResource(R.string.settings_action_link_account), onLinkDebit, enabled = enabled)
        if (card.cardType == null) com.baraa.masroof.presentation.common.MasroofManagementAction(stringResource(R.string.settings_action_mark_debit), onMarkDebit, enabled = enabled)
        onStop?.let { com.baraa.masroof.presentation.common.MasroofManagementAction(stringResource(R.string.reference_stop_tracking), it, enabled = enabled, warning = true) }
    }
}

@Composable
fun cardNetworkLabel(network: CardNetwork): String =
    when (network) {
        CardNetwork.MADA -> stringResource(R.string.card_network_mada)
        CardNetwork.VISA -> stringResource(R.string.card_network_visa)
        CardNetwork.MASTERCARD -> stringResource(R.string.card_network_mastercard)
        CardNetwork.AMEX -> stringResource(R.string.card_network_amex)
        CardNetwork.UNKNOWN -> stringResource(R.string.card_network_unknown)
    }

@Composable
fun cardRoleLabel(role: CardRole?): String =
    when (role) {
        CardRole.PRIMARY -> stringResource(R.string.settings_card_role_primary)
        CardRole.SUPPLEMENTARY -> stringResource(R.string.settings_card_role_additional)
        CardRole.STANDALONE, null -> stringResource(R.string.settings_card_role_standalone)
    }
