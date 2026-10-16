package com.baraa.masroof.lab.beta.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.application.ManualAssetDraft
import com.baraa.masroof.lab.beta.application.normalizeManualAssetSuffix
import com.baraa.masroof.lab.beta.presentation.theme.BetaSpacing
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.*
import java.util.UUID

@Composable
internal fun BetaAssetEditor(
    bank: BankId,
    existing: OwnedAsset?,
    assets: List<OwnedAsset>,
    busy: Boolean,
    failed: Boolean,
    onSave: (ManualAssetDraft) -> Unit,
    onCancel: () -> Unit,
    proposal: com.baraa.masroof.lab.beta.application.BetaAssetProposal? = null,
) {
    val localId = rememberSaveable { existing?.id?.localId ?: proposal?.id?.localId ?: UUID.randomUUID().toString() }
    val originalKind by rememberSaveable { mutableStateOf(existing?.kind?.name) }
    val originalSuffix by rememberSaveable { mutableStateOf(existing?.last4) }
    val originalParent by rememberSaveable { mutableStateOf(existing?.parentId?.localId) }
    val originalRole by rememberSaveable { mutableStateOf(existing?.creditCardRole?.name) }
    val stableId = AssetId(Environment.BETA, bank, localId)
    val original = originalKind?.let { OwnedAsset(stableId, AssetKind.valueOf(it), originalSuffix,
        originalParent?.let { parent -> AssetId(Environment.BETA, bank, parent) }, originalRole?.let(CreditCardRole::valueOf)) }
    var kindName by rememberSaveable { mutableStateOf((existing?.kind ?: proposal?.kind ?: AssetKind.ACCOUNT).name) }
    var suffix by rememberSaveable { mutableStateOf((existing?.last4 ?: proposal?.last4).orEmpty()) }
    var parentLocalId by rememberSaveable { mutableStateOf(existing?.parentId?.localId) }
    var roleName by rememberSaveable { mutableStateOf((existing?.creditCardRole ?: CreditCardRole.PRIMARY).name) }
    // Even an edit needs a fresh affirmative confirmation; opening a form never writes ownership.
    var confirmed by rememberSaveable { mutableStateOf(false) }
    val kind = AssetKind.valueOf(kindName)
    val parentKind = when (kind) { AssetKind.DEBIT_CARD -> AssetKind.ACCOUNT; AssetKind.CREDIT_CARD -> AssetKind.CREDIT_FACILITY; else -> null }
    val parents = assets.filter { it.id.bank == bank && it.kind == parentKind && it.id.localId != localId }
    val selectedParent = parents.singleOrNull { it.id.localId == parentLocalId }
    val suffixValid = suffix.matches(Regex("[0-9]{4}")) || (kind == AssetKind.CREDIT_FACILITY && suffix.isEmpty())
    val valid = confirmed && suffixValid && (parentKind == null || selectedParent != null)
    BackHandler { if (!busy) onCancel() }
    Column(verticalArrangement = Arrangement.spacedBy(BetaSpacing.section)) {
        Text(stringResource(if (existing == null) R.string.beta_asset_add else R.string.beta_asset_edit), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(bankName(bank)), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.beta_asset_manual_help), style = MaterialTheme.typography.bodyMedium)
        val kinds = if (existing == null) listOf(AssetKind.ACCOUNT, AssetKind.DEBIT_CARD, AssetKind.CREDIT_FACILITY, AssetKind.CREDIT_CARD)
            else if (existing.kind in setOf(AssetKind.DEBIT_CARD, AssetKind.CREDIT_CARD)) listOf(AssetKind.DEBIT_CARD, AssetKind.CREDIT_CARD) else listOf(existing.kind)
        kinds.forEach { choice ->
            FilterChip(selected = kind == choice, enabled = !busy, modifier = Modifier.testTag("asset-kind-${choice.name}"),
                onClick = { kindName = choice.name; parentLocalId = null }, label = { Text(stringResource(assetKindLabel(choice))) })
        }
        OutlinedTextField(value = suffix, enabled = !busy,
            onValueChange = { value -> normalizeManualAssetSuffix(value)?.let { suffix = it } },
            label = { Text(stringResource(R.string.beta_asset_suffix)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("asset-last4"))
        if (parentKind != null) {
            Text(stringResource(if (kind == AssetKind.DEBIT_CARD) R.string.beta_asset_link_account else R.string.beta_asset_link_facility))
            if (parents.isEmpty()) Text(stringResource(R.string.beta_asset_no_parent), color = MaterialTheme.colorScheme.error)
            parents.forEachIndexed { index, parent ->
                FilterChip(selected = selectedParent?.id == parent.id, enabled = !busy,
                    modifier = Modifier.testTag("asset-parent-${parent.id.localId}"),
                    onClick = { parentLocalId = parent.id.localId },
                    label = { Text(stringResource(R.string.beta_asset_summary, stringResource(assetKindLabel(parent.kind)), parent.last4.orEmpty(), index + 1)) })
            }
        }
        if (kind == AssetKind.CREDIT_CARD) {
            CreditCardRole.entries.forEach { role ->
                FilterChip(selected = role.name == roleName, enabled = !busy, modifier = Modifier.testTag("asset-role-${role.name}"),
                    onClick = { roleName = role.name }, label = { Text(stringResource(if (role == CreditCardRole.PRIMARY) R.string.beta_asset_primary else R.string.beta_asset_supplementary)) })
            }
            Text(stringResource(R.string.beta_asset_shared_facility), style = MaterialTheme.typography.bodySmall)
        }
        Row {
            Checkbox(checked = confirmed, enabled = !busy, onCheckedChange = { confirmed = it }, modifier = Modifier.testTag("asset-owned-confirm"))
            Text(stringResource(R.string.beta_asset_owned_confirm), style = MaterialTheme.typography.bodyMedium)
        }
        if (failed) Text(stringResource(R.string.beta_asset_save_failed), color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy && valid, modifier = Modifier.testTag("asset-save"), onClick = {
            onSave(ManualAssetDraft(AssetId(Environment.BETA, bank, localId), kind, suffix,
                selectedParent?.id, if (kind == AssetKind.CREDIT_CARD) CreditCardRole.valueOf(roleName) else null,
                ownedConfirmed = confirmed, editing = existing != null, expectedOriginal = original))
        }) { Text(stringResource(R.string.beta_asset_save)) }
        OutlinedButton(enabled = !busy, onClick = onCancel, modifier = Modifier.testTag("asset-cancel")) {
            Text(stringResource(R.string.beta_asset_cancel))
        }
    }
}

internal fun assetKindLabel(kind: AssetKind): Int = when (kind) {
    AssetKind.ACCOUNT -> R.string.beta_asset_account
    AssetKind.DEBIT_CARD -> R.string.beta_asset_debit
    AssetKind.CREDIT_CARD -> R.string.beta_asset_credit
    AssetKind.CREDIT_FACILITY -> R.string.beta_asset_facility
    AssetKind.LOAN -> R.string.beta_asset_loan
}
