@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.baraa.masroof.presentation.settings

import com.baraa.masroof.ui.theme.MasroofSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.CardOwnershipInlinePrompt
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBankCardsScreen(
    bank: Bank,
    state: SettingsUiState,
    onBack: () -> Unit,
    onConfirmOwned: (ManagedCardUi) -> Unit,
    onMarkExternal: (ManagedCardUi) -> Unit,
    onRequestStopTracking: (ManagedCardUi) -> Unit,
    onResumeTracking: (ManagedCardUi) -> Unit,
    onDismissStopConfirm: () -> Unit,
    onConfirmStopTracking: () -> Unit,
    onRenameCard: (ManagedCardUi) -> Unit,
    onDismissRenameCard: () -> Unit,
    onSaveCardName: (String) -> Unit,
    onPickCardNetwork: (ManagedCardUi) -> Unit,
    onDismissCardNetwork: () -> Unit,
    onSaveCardSetup: (com.baraa.masroof.domain.model.CardConfiguration) -> Unit = {},
    onOpenBank: (() -> Unit)? = null,
    onAddManual: (() -> Unit)? = null,
) {
    var referenceTab by rememberSaveable { mutableStateOf("followed") }

    val unregisteredCards = state.unregisteredCards.filter { it.bank == bank }
    val followedCards = state.followedCards.filter { it.bank == bank }
    val stoppedCards = state.stoppedCards.filter { it.bank == bank }
    val bankTree = state.bankTree(bank.id)

    SettingsStopConfirmDialog(
        target = state.stopConfirmCardTarget,
        updating = state.updating,
        onDismiss = onDismissStopConfirm,
        onConfirm = onConfirmStopTracking,
    )
    SettingsRenameCardDialog(
        target = state.renameCardTarget,
        updating = state.updating,
        onDismiss = onDismissRenameCard,
        onSave = onSaveCardName,
    )
    SettingsCardSetupSheet(
        target = state.cardNetworkTarget,
        suggestion = state.cardTypeSuggestion,
        cards = state.followedCards + state.unregisteredCards + state.stoppedCards,
        accounts = state.followedAccounts + state.stoppedAccounts,
        busy = state.updating,
        failed = state.error != null,
        onDismiss = onDismissCardNetwork,
        onSave = onSaveCardSetup,
    )
    MasroofSecondaryScaffold(
        title = stringResource(R.string.reference_manage_cards),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SettingsSpacing.sectionGap),
        ) {
            onAddManual?.let { action -> com.baraa.masroof.presentation.common.MasroofOutlinedButton(action,
                enabled = !state.updating, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_add_card_manual))
            } }
            SettingsScreenHeader(
                bank = bank,
                hint = stringResource(R.string.settings_cards_hint),
                onOpenBank = onOpenBank,
            )
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(com.baraa.masroof.ui.theme.MasroofReference.shortcutGap), verticalArrangement = Arrangement.spacedBy(com.baraa.masroof.ui.theme.MasroofReference.shortcutGap)) {
                listOf("followed" to R.string.reference_followed, "unconfirmed" to R.string.reference_unconfirmed,
                    "stopped" to R.string.settings_external_cards).forEach { (key, label) ->
                    com.baraa.masroof.presentation.common.MasroofChoice(stringResource(label), referenceTab == key, { referenceTab = key })
                }
            }


            if (followedCards.isEmpty() && unregisteredCards.isEmpty() && stoppedCards.isEmpty()) {
                Text(
                    stringResource(R.string.settings_cards_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (referenceTab == "unconfirmed" && unregisteredCards.isNotEmpty()) {
                SettingsGroupTitle(stringResource(R.string.settings_cards_unregistered))
                unregisteredCards.forEach { card ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.cardPayment,
                        bank = card.bank,
                        title = registryLabel(card),
                        showBankLabel = false,
                        footer = {
                            CardOwnershipInlinePrompt(
                                enabled = !state.updating,
                                onConfirmOwned = { onConfirmOwned(card) },
                                onMarkExternal = { onMarkExternal(card) },
                            )
                        },
                    )
                }
            }

            if (referenceTab == "followed" && followedCards.isNotEmpty()) {

                if (bankTree != null) {
                    bankTree.creditCards.forEach { card ->
                        SettingsRegistryCardRow(
                            card = card,
                            updating = state.updating,
                            onRequestStopTracking = onRequestStopTracking,
                            onRenameCard = onRenameCard,
                            onPickCardNetwork = onPickCardNetwork,
                        )
                    }
                    bankTree.allDebitCards.forEach { card ->
                        SettingsRegistryCardRow(
                            card = card,
                            updating = state.updating,
                            onRequestStopTracking = onRequestStopTracking,
                            onRenameCard = onRenameCard,
                            onPickCardNetwork = onPickCardNetwork,
                        )
                    }
                } else {
                    followedCards.forEach { card ->
                        SettingsRegistryCardRow(
                            card = card,
                            updating = state.updating,
                            onRequestStopTracking = onRequestStopTracking,
                            onRenameCard = onRenameCard,
                            onPickCardNetwork = onPickCardNetwork,
                        )
                    }
                }
            }

            if (referenceTab == "stopped" && stoppedCards.isNotEmpty()) {
                SettingsGroupTitle(stringResource(R.string.settings_external_cards))
                stoppedCards.forEach { card ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.cardPayment,
                        bank = card.bank,
                        title = registryLabel(card),
                        showBankLabel = false,
                        footer = {
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap),
                                verticalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap),
                            ) {
                                com.baraa.masroof.presentation.common.MasroofManagementAction(
                                    stringResource(R.string.settings_action_rename), { onRenameCard(card) }, enabled = !state.updating,
                                )
                                com.baraa.masroof.presentation.common.MasroofManagementAction(
                                    stringResource(R.string.settings_card_setup), { onPickCardNetwork(card) }, enabled = !state.updating,
                                )
                            }
                        },
                        endAction = {
                            com.baraa.masroof.presentation.common.MasroofManagementAction(
                                label = stringResource(R.string.settings_external_card_is_mine),
                                onClick = { onResumeTracking(card) },
                                enabled = !state.updating,
                            )
                        },
                    )
                }
            }

            state.error?.let {
                Text(
                    stringResource(R.string.settings_update_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun SettingsRegistryCardRow(
    card: ManagedCardUi,
    updating: Boolean,
    onRequestStopTracking: (ManagedCardUi) -> Unit,
    onRenameCard: (ManagedCardUi) -> Unit,
    onPickCardNetwork: (ManagedCardUi) -> Unit,
) {
    SettingsRegistryItemCard(
        icon = MasroofIcons.cardPayment,
        bank = card.bank,
        title = registryLabel(card),
        showBankLabel = false,
        onTitleClick = { onRenameCard(card) },
        footer = {
            cardSubtitle(card)?.let { subtitle ->
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MasroofSpacing.inlineGap),
                )
            }
            SettingsCardMetadataActions(
                enabled = !updating,
                onRename = { onRenameCard(card) },
                onPickNetwork = { onPickCardNetwork(card) },
                includeRename = false,
                onStop = { onRequestStopTracking(card) },
            )
        },
    )
}

@Composable
private fun cardSubtitle(card: ManagedCardUi): String? {
    val parts = buildList {
        card.cardNetwork?.let { add(cardNetworkLabel(it)) }
        card.cardRole?.let { add(cardRoleLabel(it)) }
        card.linkedAccountMaskedNumber?.let {
            add(stringResource(R.string.settings_linked_account_suffix, it.takeLast(4)))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
