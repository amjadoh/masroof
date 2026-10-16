package com.baraa.masroof.lab.beta.presentation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.ManualAssetDraft
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BetaManualAssetScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val bank = BankId("BANK_ALJAZIRA")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var beta: BetaContainer
    private fun id(value: String) = AssetId(Environment.BETA, bank, value)
    @Before fun open() {
        BetaContainer.resetOffline(context)
        beta = BetaContainer(context)
        beta.setBankEnabled(bank, true)
    }
    @After fun close() { beta.close(); BetaContainer.resetOffline(context) }
    private fun showEditor(existing: OwnedAsset? = null, onCancel: () -> Unit = {}, onSaved: () -> Unit = {}) {
        val assets = beta.assets()
        compose.setContent {
            MaterialTheme {
                BetaShell(0, false, editor = {
                    BetaAssetEditor(bank, existing, assets, false, false,
                        onSave = { beta.saveManualAsset(it); onSaved() }, onCancel = onCancel)
                }, onReturn = {}, onReset = {}, onBackup = {}, onRestore = {})
            }
        }
    }
    @Test fun cancelAndBackDiscardUnconfirmedDraftWithoutWrites() {
        var canceled = 0
        showEditor(onCancel = { canceled++ })
        compose.onNodeWithTag("asset-last4").performScrollTo().performTextInput("1234")
        compose.onNodeWithTag("asset-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("asset-cancel").performScrollTo().performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(2, canceled)
        assertTrue(beta.assets().isEmpty())
    }
    @Test fun manualAccountSavesWithoutSmsAndSurvivesRestart() {
        var saved = false
        showEditor(onSaved = { saved = true })
        compose.onNodeWithTag("asset-last4").performScrollTo().performTextInput("١٢٣٤")
        compose.onNodeWithTag("asset-owned-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("asset-save").performScrollTo().performClick()
        assertTrue(saved)
        val original = beta.assets().single()
        beta.close()
        beta = BetaContainer(context)
        assertEquals(original, beta.assets().single())
        assertEquals(AssetKind.ACCOUNT, original.kind)
        assertEquals("1234", original.last4)
    }
    @Test fun restoredDraftRetryKeepsItsStableIdentityWithoutDuplicateAsset() {
        val restoration = StateRestorationTester(compose)
        val savedIds = mutableListOf<AssetId>()
        restoration.setContent {
            MaterialTheme {
                BetaShell(0, false, editor = {
                    BetaAssetEditor(bank, null, emptyList(), false, false,
                        onSave = { savedIds += beta.saveManualAsset(it).id }, onCancel = {})
                }, onReturn = {}, onReset = {}, onBackup = {}, onRestore = {})
            }
        }
        compose.onNodeWithTag("asset-last4").performScrollTo().performTextInput("1234")
        compose.onNodeWithTag("asset-owned-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("asset-save").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("asset-save").performScrollTo().performClick()
        assertEquals(2, savedIds.size)
        assertEquals(savedIds.first(), savedIds.last())
        assertEquals(1, beta.assets().size)
    }
    @Test fun existingDebitCanBeConfiguredAsSupplementaryWithStableIdAndFacilityLink() {
        beta.saveManualAsset(ManualAssetDraft(id("account"), AssetKind.ACCOUNT, "1111", ownedConfirmed = true))
        beta.saveManualAsset(ManualAssetDraft(id("facility"), AssetKind.CREDIT_FACILITY, "", ownedConfirmed = true))
        beta.saveManualAsset(ManualAssetDraft(id("primary"), AssetKind.CREDIT_CARD, "2222", id("facility"), CreditCardRole.PRIMARY, true))
        val debit = beta.saveManualAsset(ManualAssetDraft(id("debit"), AssetKind.DEBIT_CARD, "3333", id("account"), ownedConfirmed = true))
        showEditor(debit)
        compose.onNodeWithTag("asset-kind-CREDIT_CARD").performScrollTo().performClick()
        compose.onNodeWithTag("asset-parent-facility").performScrollTo().performClick()
        compose.onNodeWithTag("asset-role-SUPPLEMENTARY").performScrollTo().performClick()
        compose.onNodeWithTag("asset-owned-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("asset-save").performScrollTo().performClick()
        val changed = beta.assets().single { it.id == debit.id }
        assertEquals(AssetKind.CREDIT_CARD, changed.kind)
        assertEquals(CreditCardRole.SUPPLEMENTARY, changed.creditCardRole)
        assertEquals(id("facility"), changed.parentId)
        assertEquals(4, beta.assets().size)
        beta.backup()
        beta.saveManualAsset(ManualAssetDraft(changed.id, changed.kind, "9999", changed.parentId, changed.creditCardRole,
            ownedConfirmed = true, editing = true, expectedOriginal = changed))
        assertTrue(beta.restore())
        assertEquals(changed, beta.assets().single { it.id == changed.id })
        beta.close()
        beta = BetaContainer(context)
        assertEquals(changed, beta.assets().single { it.id == changed.id })
    }
}
