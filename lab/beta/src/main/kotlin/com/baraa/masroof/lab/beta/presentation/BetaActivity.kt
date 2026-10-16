package com.baraa.masroof.lab.beta.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import com.baraa.masroof.lab.finance.asset.OwnedAsset
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.beta.application.BetaBankChoice
import com.baraa.masroof.lab.beta.BetaContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen Beta shell hosted by the lab module.
 *
 * It does not read the production composition root.
 */
class BetaActivity : ComponentActivity() {
    private var betaContainer: BetaContainer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = BetaContainer(applicationContext)
        betaContainer = container
        setContent {
            MaterialTheme {
                val shellContainer = remember { container }
                val scope = rememberCoroutineScope()
                var markerCount by remember { mutableIntStateOf(0) }
                var restoreMissing by remember { mutableStateOf(false) }
                var banks by remember { mutableStateOf<List<BetaBankChoice>>(emptyList()) }
                var assets by remember { mutableStateOf<List<OwnedAsset>>(emptyList()) }
                var loaded by remember { mutableStateOf(false) }
                var editBank by rememberSaveable { mutableStateOf<String?>(null) }
                var editId by rememberSaveable { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(true) }
                var failed by remember { mutableStateOf(false) }
                suspend fun execute(action: () -> Boolean?): Boolean {
                    failed = false
                    try {
                        val snapshot = withContext(Dispatchers.IO) {
                            val restored = action()
                            BetaScreenSnapshot(shellContainer.markerCount(), shellContainer.bankChoices(), shellContainer.assets(), restored)
                        }
                        markerCount = snapshot.markers
                        banks = snapshot.banks
                        assets = snapshot.assets
                        loaded = true
                        restoreMissing = snapshot.restored == false
                        return true
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        banks = emptyList()
                        failed = true
                        return false
                    } finally { busy = false }
                }
                fun perform(action: () -> Boolean?) {
                    if (busy) return
                    busy = true
                    scope.launch { execute(action) }
                }
                LaunchedEffect(Unit) { execute { null } }
                BetaShell(
                    markerCount = markerCount,
                    assets = assets,
                    returnEnabled = editBank == null || !busy,
                    onAddAsset = { bank -> if (!busy) { editBank = bank.value; editId = null; failed = false } },
                    onEditAsset = { asset -> if (!busy) { editBank = asset.id.bank.value; editId = asset.id.localId; failed = false } },
                    editor = if (editBank != null && loaded) ({
                        key(editBank, editId) {
                            val existing = editId?.let { id -> assets.singleOrNull { it.id.bank.value == editBank && it.id.localId == id } }
                            if (editId != null && existing == null) {
                                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.baraa.masroof.lab.beta.R.string.beta_asset_missing))
                                androidx.compose.material3.OutlinedButton(onClick = { editBank = null; editId = null }) {
                                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.baraa.masroof.lab.beta.R.string.beta_asset_cancel))
                                }
                            } else BetaAssetEditor(BankId(checkNotNull(editBank)), existing, assets, busy, failed,
                                onCancel = { editBank = null; editId = null; failed = false },
                                onSave = { draft ->
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            if (execute { shellContainer.saveManualAsset(draft); null }) {
                                                editBank = null
                                                editId = null
                                            }
                                        }
                                    }
                                })
                        }
                    }) else null,
                    banks = banks,
                    busy = busy,
                    failed = failed,
                    onRetry = { perform { null } },
                    onSetBankEnabled = { bank, enabled ->
                        perform { shellContainer.setBankEnabled(bank, enabled); null }
                    },
                    restoreMissing = restoreMissing,
                    onReturn = { if (editBank == null || !busy) finish() },
                    onReset = { perform { shellContainer.reset(); null } },
                    onBackup = { perform { shellContainer.backup(); null } },
                    onRestore = { perform { shellContainer.restore() } },
                )
            }
        }
    }

    override fun onDestroy() {
        betaContainer?.close()
        betaContainer = null
        super.onDestroy()
    }
}

private data class BetaScreenSnapshot(
    val markers: Int,
    val banks: List<BetaBankChoice>,
    val assets: List<OwnedAsset>,
    val restored: Boolean?,
)
