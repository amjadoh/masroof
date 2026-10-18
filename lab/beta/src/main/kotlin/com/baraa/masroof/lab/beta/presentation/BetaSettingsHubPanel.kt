package com.baraa.masroof.lab.beta.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.ui.components.*
import com.baraa.masroof.ui.theme.*
import java.time.YearMonth

/** Financial management only. Maintenance and appearance are separate developer tooling. */
@Composable
internal fun BetaSettingsHubPanel(category: String, onCategory: (String) -> Unit,
    banks: List<BetaBankChoice>, assets: List<OwnedAsset>, controls: BetaDashboardControls, busy: Boolean,
    onBankEnabled: (BankId, Boolean) -> Unit, onAdd: (BankId, AssetKind) -> Unit, onEdit: (OwnedAsset) -> Unit,
    onBusyChanged: (Boolean) -> Unit, onClose: () -> Unit) {
    BackHandler { if (!busy) { if (category == "HUB") onClose() else onCategory("HUB") } }
    Column(Modifier.testTag("beta-settings-panel"), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        Text(stringResource(R.string.beta_nav_settings), style = MaterialTheme.typography.headlineSmall)
        if (category == "HUB") MasroofCard(Modifier.testTag("beta-management-settings")) {
            Text(stringResource(R.string.beta_manage_title), style = MaterialTheme.typography.titleMedium)
            val groups = listOf("ACCOUNTS" to R.string.beta_home_accounts, "CARDS" to R.string.beta_home_cards,
                "LOANS" to R.string.beta_home_loans, "COMMITMENTS" to R.string.beta_home_commitments_shortcut)
            groups.forEachIndexed { index, (key, label) ->
                BetaManagementRow(stringResource(label), stringResource(R.string.beta_manage_open), when (key) {
                    "ACCOUNTS" -> MasroofOutlineIcons.Landmark; "CARDS" -> MasroofOutlineIcons.CreditCard
                    "LOANS" -> MasroofOutlineIcons.HandCoins; else -> MasroofOutlineIcons.CalendarCheck
                }, enabled = !busy, onClick = { onCategory(key) }, modifier = Modifier.testTag("manage-$key"), divider = index != groups.lastIndex)
            }
        } else if (category == "COMMITMENTS") {
            BetaDashboardConfigurationPanel(controls, emptyList(), YearMonth.now(java.time.ZoneId.of("Asia/Riyadh")), !busy,
                onBusyChanged, onSaved = {}, commitmentsOnly = true)
        } else {
            val kinds = when (category) {
                "ACCOUNTS" -> setOf(AssetKind.ACCOUNT)
                "LOANS" -> setOf(AssetKind.LOAN)
                else -> setOf(AssetKind.CREDIT_CARD, AssetKind.DEBIT_CARD, AssetKind.CREDIT_FACILITY)
            }
            banks.forEach { bank ->
                MasroofCard {
                    Text(stringResource(bankName(bank.bankId)), style = MaterialTheme.typography.titleMedium)
                    if (category == "ACCOUNTS") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(if (bank.state == BankSelectionState.ENABLED) R.string.beta_bank_enabled else R.string.beta_bank_disabled), Modifier.weight(1f))
                            Switch(bank.state == BankSelectionState.ENABLED, onCheckedChange = { onBankEnabled(bank.bankId, it) }, enabled = !busy,
                                modifier = Modifier.testTag("manage-bank-${bank.bankId.value}"))
                        }
                    }
                    assets.filter { it.id.bank == bank.bankId && it.kind in kinds }.forEach { asset ->
                        BetaManagementRow(stringResource(assetKindLabel(asset.kind)), asset.last4?.let { "•• $it" }.orEmpty(), MasroofOutlineIcons.Wallet,
                            enabled = !busy, onClick = { onEdit(asset) }, modifier = Modifier.testTag("manage-asset-${asset.id.bank.value}-${asset.id.localId}"))
                    }
                    OutlinedButton(enabled = !busy && bank.state == BankSelectionState.ENABLED, onClick = {
                        onAdd(bank.bankId, when (category) { "ACCOUNTS" -> AssetKind.ACCOUNT; "LOANS" -> AssetKind.LOAN; else -> AssetKind.CREDIT_FACILITY })
                    }, modifier = Modifier.fillMaxWidth().testTag("manage-add-${bank.bankId.value}")) { Text(stringResource(R.string.beta_asset_add)) }
                }
            }
        }
        OutlinedButton(enabled = !busy, onClick = { if (category == "HUB") onClose() else onCategory("HUB") },
            modifier = Modifier.testTag("beta-settings-close")) { Text(stringResource(if (category == "HUB") R.string.beta_asset_cancel else R.string.beta_dash_back)) }
    }
}

/** Production navigation geometry expressed with existing shared tokens and renderers. */
@Composable
private fun BetaManagementRow(title: String, subtitle: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, divider: Boolean = true) {
    Column(modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(top = MasroofReference.rowVertical,
            bottom = if (divider) MasroofReference.rowVertical else MasroofReference.lastRowBottom),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MasroofReference.rowGap)) {
            Surface(shape = MasroofShapes.small, color = MasroofThemeExtras.extendedColors.accountSoft) {
                Box(Modifier.size(MasroofReference.iconBox), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(MasroofReference.icon), tint = MasroofThemeExtras.extendedColors.account)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MasroofReference.metadataGap)) {
                Text(title, style = MasroofReference.pair.copy(fontWeight = MasroofFontWeights.semiBold))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (LocalLayoutDirection.current == LayoutDirection.Rtl) MasroofOutlineIcons.ChevronLeft else MasroofOutlineIcons.ChevronRight,
                null, Modifier.size(MasroofReference.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (divider) HorizontalDivider(color = MasroofThemeExtras.extendedColors.cardBorder)
    }
}
