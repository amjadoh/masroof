package com.baraa.masroof.lab.beta.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import com.baraa.masroof.lab.beta.presentation.theme.BetaSpacing
import com.baraa.masroof.lab.beta.application.BetaBankChoice
import com.baraa.masroof.lab.beta.application.BankSelectionState
import com.baraa.masroof.lab.finance.asset.OwnedAsset
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.beta.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetaShell(
    markerCount: Int,
    restoreMissing: Boolean,
    banks: List<BetaBankChoice> = emptyList(),
    assets: List<OwnedAsset> = emptyList(),
    editor: (@Composable () -> Unit)? = null,
    dashboard: (@Composable () -> Unit)? = null,
    onOpenDiscovery: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    onOpenApproval: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenHome: (() -> Unit)? = null,
    onOpenTransactions: () -> Unit = {},
    selectedHomeTab: String = "HOME",
    showMaintenanceActions: Boolean = true,
    onAddAsset: (BankId) -> Unit = {},
    onEditAsset: (OwnedAsset) -> Unit = {},
    returnEnabled: Boolean = true,
    busy: Boolean = false,
    failed: Boolean = false,
    onSetBankEnabled: (BankId, Boolean) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    onReturn: () -> Unit,
    onReset: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    var workspaceOpen by rememberSaveable { mutableStateOf(false) }
    val homeState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    BackHandler { if (returnEnabled) { if (workspaceOpen && editor == null) workspaceOpen = false else onReturn() } }
    Scaffold(
        topBar = {
            com.baraa.masroof.ui.components.MasroofAppBar(
                title = stringResource(R.string.beta_home_brand),
                actions = {
                    if (dashboard != null && editor == null) androidx.compose.material3.IconButton(
                        enabled = !busy, onClick = { workspaceOpen = !workspaceOpen }, modifier = Modifier.testTag("beta-tools")) {
                        androidx.compose.material3.Icon(com.baraa.masroof.ui.components.MasroofOutlineIcons.SlidersHorizontal,
                            stringResource(R.string.beta_home_tools))
                    }

                    Surface(Modifier.padding(end = BetaSpacing.section).testTag("beta-badge"),
                        color = MaterialTheme.colorScheme.errorContainer) {
                        Text(stringResource(R.string.beta_badge),
                            Modifier.padding(horizontal = BetaSpacing.badgeHorizontal, vertical = BetaSpacing.badgeVertical),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                },
            )
        },
        bottomBar = {
            Column {
            if (editor == null && dashboard != null && onOpenHome != null) {
                val icons = com.baraa.masroof.ui.components.MasroofOutlineIcons
                val items = listOf(
                    com.baraa.masroof.ui.components.MasroofNavigationItem(stringResource(R.string.beta_nav_home), icons.LayoutDashboard, selectedHomeTab == "HOME"),
                    com.baraa.masroof.ui.components.MasroofNavigationItem(stringResource(R.string.beta_nav_transactions), icons.List, selectedHomeTab == "TRANSACTIONS"),
                    com.baraa.masroof.ui.components.MasroofNavigationItem(stringResource(R.string.beta_nav_review), icons.ShieldCheck, false),
                    com.baraa.masroof.ui.components.MasroofNavigationItem(stringResource(R.string.beta_nav_settings), icons.Settings, false))
                Column(Modifier.testTag("beta-bottom-navigation")) {
                com.baraa.masroof.ui.components.MasroofBottomNavigation(items, { index ->
                    when (index) { 0 -> { workspaceOpen = false; onOpenHome() }; 1 -> { workspaceOpen = false; onOpenTransactions() }; 2 -> onOpenReview(); 3 -> onOpenSettings() }
                }, enabled = returnEnabled && !busy)
                }
            }
            Button(onClick = onReturn, enabled = returnEnabled, modifier = Modifier.fillMaxWidth().padding(BetaSpacing.screen).testTag("beta-return")) {
                Text(stringResource(R.string.beta_return))
            }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(BetaSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(BetaSpacing.section),
        ) {
            if (editor != null) {
                editor()
                return@Column
            }
            if (dashboard != null && !workspaceOpen) {
                homeState.SaveableStateProvider("dashboard") { dashboard() }
                return@Column
            }
            Text(stringResource(if (dashboard == null) R.string.beta_welcome else R.string.beta_home_tools), style = MaterialTheme.typography.titleLarge)
            if (dashboard == null) Text(stringResource(R.string.beta_welcome_steps))
            OutlinedButton(enabled = !busy, onClick = onOpenSettings, modifier = Modifier.testTag("beta-settings")) { Text(stringResource(R.string.beta_settings_title)) }
            OutlinedButton(enabled = !busy, onClick = onOpenDiscovery, modifier = Modifier.testTag("beta-discovery")) { Text(stringResource(R.string.beta_discovery_title)) }
            if (dashboard == null) Text(stringResource(R.string.beta_empty_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(enabled = !busy, onClick = onOpenHistory, modifier = Modifier.testTag("beta-history")) {
                Text(stringResource(R.string.beta_history_title))
            }
            OutlinedButton(enabled = !busy, onClick = onOpenReview, modifier = Modifier.testTag("beta-review")) { Text(stringResource(R.string.beta_review_title)) }
            OutlinedButton(enabled = !busy, onClick = onOpenApproval, modifier = Modifier.testTag("beta-approval")) { Text(stringResource(R.string.beta_approval_title)) }
            Text(stringResource(R.string.beta_bank_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.beta_bank_help), style = MaterialTheme.typography.bodyMedium)
            if (failed) {
                Text(stringResource(R.string.beta_action_failed), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry, enabled = !busy, modifier = Modifier.testTag("beta-retry")) {
                    Text(stringResource(R.string.beta_retry))
                }
            }
            banks.forEach { choice ->
                Card(modifier = Modifier.fillMaxWidth().testTag("beta-bank-${choice.bankId.value}")) {
                    Column(Modifier.padding(BetaSpacing.screen), verticalArrangement = Arrangement.spacedBy(BetaSpacing.section)) {
                        Text(stringResource(bankName(choice.bankId)), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(when (choice.state) {
                            BankSelectionState.ENABLED -> R.string.beta_bank_enabled
                            BankSelectionState.DISABLED -> R.string.beta_bank_disabled
                            null -> R.string.beta_bank_not_selected
                        }))
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { onSetBankEnabled(choice.bankId, choice.state != BankSelectionState.ENABLED) },
                            modifier = Modifier.testTag("beta-bank-action-${choice.bankId.value}"),
                        ) {
                            Text(stringResource(when (choice.state) {
                                BankSelectionState.ENABLED -> R.string.beta_bank_disable
                                BankSelectionState.DISABLED -> R.string.beta_bank_resume
                                null -> R.string.beta_bank_add
                            }))
                        }
                        if (choice.state == BankSelectionState.ENABLED) {
                            OutlinedButton(enabled = !busy, onClick = { onAddAsset(choice.bankId) }, modifier = Modifier.testTag("asset-add-${choice.bankId.value}")) {
                                Text(stringResource(R.string.beta_asset_add))
                            }
                        }
                    }
                }
            }
            if (assets.isNotEmpty()) Text(stringResource(R.string.beta_asset_list), style = MaterialTheme.typography.titleLarge)
            assets.forEachIndexed { index, asset ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(BetaSpacing.screen)) {
                        Text(stringResource(R.string.beta_asset_summary, stringResource(assetKindLabel(asset.kind)), asset.last4.orEmpty(), index + 1))
                        OutlinedButton(enabled = !busy, onClick = { onEditAsset(asset) }, modifier = Modifier.testTag("asset-edit-${asset.id.localId}")) {
                            Text(stringResource(R.string.beta_asset_edit))
                        }
                    }
                }
            }
            if (showMaintenanceActions) {
            OutlinedButton(
                enabled = !busy,
                onClick = onBackup,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beta-backup"),
            ) {
                Text(stringResource(R.string.beta_backup))
            }
            OutlinedButton(
                enabled = !busy,
                onClick = onRestore,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beta-restore"),
            ) {
                Text(stringResource(R.string.beta_restore))
            }
            OutlinedButton(
                enabled = !busy,
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beta-reset"),
            ) {
                Text(stringResource(R.string.beta_reset))
            }
            }
            if (showMaintenanceActions) Text(stringResource(R.string.beta_marker_count, markerCount), style = MaterialTheme.typography.bodySmall)
            if (restoreMissing) Text(stringResource(R.string.beta_restore_missing), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Every real supported bank must have a localized name before joining the picker. */
internal fun bankName(bankId: BankId): Int = when (bankId.value) {
    "BANK_ALJAZIRA" -> R.string.beta_bank_aljazira
    else -> error("Supported bank has no localized picker label")
}
