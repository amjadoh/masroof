package com.baraa.masroof.lab.beta.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.finance.asset.OwnedAsset
import com.baraa.masroof.lab.beta.presentation.theme.BetaSpacing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
internal fun BetaDiscoveryPanel(container: BetaContainer, assets: List<OwnedAsset>, onSelect: (BetaAssetProposal) -> Unit, onClose: () -> Unit, onBusyChanged: (Boolean) -> Unit = {}) {
    val scope = rememberBetaUiScope()
    var busy by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    SideEffect { onBusyChanged(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    var after by rememberSaveable { mutableStateOf("") }
    var page by remember { mutableStateOf<BetaDiscoveryPage?>(null) }
    var failed by remember { mutableStateOf(false) }
    BetaLaunchedEffect(after, generation) {
        try { page = withContext(BetaIo.dispatcher) { container.discoveryPage(after) } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { page = null; failed = true }
    }
    BackHandler { if (!busy) onClose() }
    Column(verticalArrangement = Arrangement.spacedBy(BetaSpacing.section)) {
        Text(stringResource(R.string.beta_discovery_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.beta_discovery_help))
        if (failed) Text(stringResource(R.string.beta_action_failed), color = MaterialTheme.colorScheme.error)
        if (page == null && !failed) CircularProgressIndicator()
        page?.let { current ->
            if (current.proposals.isEmpty()) Text(stringResource(R.string.beta_discovery_empty))
            // One visual item per observed bank/kind/suffix. This does not create ownership or merge entities.
            current.proposals.distinctBy { Triple(it.id.bank, it.kind, it.last4) }.forEachIndexed { index, proposal ->
                val confirmed = assets.singleOrNull { it.id == (proposal.confirmedAssetId ?: proposal.id) }
                    ?: assets.filter { it.id.bank == proposal.id.bank && it.kind == proposal.kind && it.last4 == proposal.last4 }.singleOrNull()
                val conflict = confirmed != null && (confirmed.kind != proposal.kind || confirmed.last4 != proposal.last4)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(BetaSpacing.screen), verticalArrangement = Arrangement.spacedBy(BetaSpacing.section)) {
                        Text(stringResource(bankName(proposal.id.bank)))
                        Text(stringResource(R.string.beta_asset_summary, stringResource(assetKindLabel(proposal.kind)), proposal.last4, index + 1))
                        if (confirmed != null) Text(stringResource(if (conflict) R.string.beta_discovery_conflict else R.string.beta_discovery_confirmed))
                        if (assets.any { it.id != proposal.id && it.id.bank == proposal.id.bank && it.last4 == proposal.last4 }) Text(stringResource(R.string.beta_discovery_collision))
                        if (confirmed == null) {
                            key(proposal.id.localId, proposal.revision) {
                                var selected by rememberSaveable { mutableStateOf<String?>(null) }
                                var ownershipConfirmed by rememberSaveable { mutableStateOf(false) }
                                val compatible = assets.filter { it.id.bank == proposal.id.bank && it.kind == proposal.kind && it.last4 == proposal.last4 }
                                compatible.forEachIndexed { position, asset ->
                                    FilterChip(selected = selected == asset.id.localId, enabled = !busy,
                                        onClick = { selected = asset.id.localId; ownershipConfirmed = false },
                                        modifier = Modifier.testTag("proposal-existing-$index-$position"),
                                        label = {
                                            Column {
                                                Text(stringResource(R.string.beta_discovery_existing, assets.indexOf(asset) + 1, stringResource(assetKindLabel(asset.kind)), asset.last4.orEmpty()))
                                                asset.parentId?.let { parentId -> assets.singleOrNull { it.id == parentId }?.let { parent ->
                                                    Text(stringResource(R.string.beta_discovery_parent, stringResource(assetKindLabel(parent.kind)), parent.last4.orEmpty(), assets.indexOf(parent) + 1))
                                                } }
                                                asset.creditCardRole?.let { role -> Text(stringResource(if (role == com.baraa.masroof.lab.finance.asset.CreditCardRole.PRIMARY) R.string.beta_asset_primary else R.string.beta_asset_supplementary)) }
                                            }
                                        })
                                }
                                if (selected != null) {
                                    Text(stringResource(R.string.beta_discovery_link_help))
                                    Row {
                                        Checkbox(checked = ownershipConfirmed, enabled = !busy, onCheckedChange = { ownershipConfirmed = it }, modifier = Modifier.testTag("proposal-confirm-$index"))
                                        Text(stringResource(R.string.beta_asset_owned_confirm))
                                    }
                                    OutlinedButton(enabled = !busy && ownershipConfirmed, modifier = Modifier.testTag("proposal-link-$index"), onClick = {
                                        val target = compatible.singleOrNull { it.id.localId == selected }
                                        if (target != null) {
                                            busy = true; failed = false
                                            scope.launch {
                                                try { withContext(BetaIo.dispatcher) { container.linkExistingProposal(proposal, target, ownershipConfirmed) }; generation++ }
                                                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                                catch (_: Exception) { failed = true; generation++ }
                                                finally { busy = false }
                                            }
                                        }
                                    }) { Text(stringResource(R.string.beta_discovery_link)) }
                                }
                            }
                        }
                        OutlinedButton(enabled = !busy && confirmed == null, onClick = { onSelect(proposal) }, modifier = Modifier.testTag("proposal-open-$index")) { Text(stringResource(R.string.beta_discovery_open)) }
                    }
                }
            }
            if (current.needsReview > 0) Text(stringResource(R.string.beta_discovery_review, current.needsReview))
            if (current.afterId != null) OutlinedButton(enabled = !busy, onClick = { page = null; after = current.afterId }, modifier = Modifier.testTag("proposal-next")) { Text(stringResource(R.string.beta_history_next)) }
        }
        if (after.isNotEmpty()) OutlinedButton(enabled = !busy, onClick = { page = null; after = "" }) { Text(stringResource(R.string.beta_history_first)) }
        OutlinedButton(enabled = !busy, onClick = onClose, modifier = Modifier.testTag("proposal-close")) { Text(stringResource(R.string.beta_asset_cancel)) }
    }
}
