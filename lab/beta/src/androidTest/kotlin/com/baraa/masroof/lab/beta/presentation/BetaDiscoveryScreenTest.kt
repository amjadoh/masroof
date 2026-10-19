package com.baraa.masroof.lab.beta.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.baraa.masroof.lab.beta.application.BetaAssetProposal
import com.baraa.masroof.lab.beta.application.ManualAssetDraft
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BetaDiscoveryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun rtlProposalRestoresUnconfirmedDraftAndPreservesExplicitStableIdentity() {
        val bank = BankId("BANK_ALJAZIRA")
        val proposal = BetaAssetProposal(AssetId(Environment.BETA, bank, "opaque-proposal"), AssetKind.ACCOUNT, "1234", "evidence", "revision")
        var saved: ManualAssetDraft? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { BetaShell(0, false, editor = {
                    BetaAssetEditor(bank, null, emptyList(), false, false, onSave = { saved = it }, onCancel = {}, proposal = proposal)
                }, onReturn = {}, onReset = {}, onBackup = {}, onRestore = {}) }
            }
        }
        compose.onNodeWithTag("asset-last4").performScrollTo().assertTextContains("1234")
        compose.onNodeWithTag("asset-save").performScrollTo().assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("asset-save").performScrollTo().assertIsNotEnabled()
        assertNull(saved)
        compose.onNodeWithTag("asset-owned-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("asset-save").performScrollTo().performClick()
        assertEquals(proposal.id, saved!!.id)
        assertTrue(saved!!.ownedConfirmed)
        assertNull(saved!!.parentId)
    }
    @Test fun subsequentObservationRequiresExplicitExistingChoiceAndDoesNotDuplicateCard() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.baraa.masroof.lab.beta.BetaContainer.resetOffline(context)
        com.baraa.masroof.lab.beta.BetaContainer(context).use { beta ->
            val bank = BankId("BANK_ALJAZIRA")
            beta.setBankEnabled(bank, true)
            beta.importEvidence(com.baraa.masroof.lab.beta.application.evidence.EvidenceRequest("synthetic", com.baraa.masroof.lab.beta.application.evidence.EvidenceOrigin.EXPLICIT_SAMPLE),
                listOf(com.baraa.masroof.lab.beta.application.evidence.EvidenceMessage("provider", "AlJazira", "POS Purchase\nCard: 1234\nCard type: credit\nAmount: 42 SAR\nAvailable balance: 100 SAR\n2026-10-16 12:00", java.time.Instant.parse("2026-10-16T12:00:00Z"), com.baraa.masroof.lab.beta.application.evidence.ReceiptTimeSource.USER_SUPPLIED)))
            val facility = beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA, bank, "facility"), AssetKind.CREDIT_FACILITY, "", ownedConfirmed = true))
            val card = beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA, bank, "card"), AssetKind.CREDIT_CARD, "1234", facility.id, CreditCardRole.PRIMARY, ownedConfirmed = true))
            // A single confirmed owner no longer needs per-message approval. A collision still needs an explicit binding.
            beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA, bank, "other-card"), AssetKind.CREDIT_CARD, "1234", facility.id, CreditCardRole.SUPPLEMENTARY, ownedConfirmed = true))
            val assets = beta.assets()
            compose.setContent { MaterialTheme { BetaShell(0, false, editor = {
                BetaDiscoveryPanel(beta, assets, onSelect = { fail("Existing choice must not open new asset") }, onClose = {})
            }, onReturn = {}, onReset = {}, onBackup = {}, onRestore = {}) } }
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("proposal-existing-0-0").assertExists(); true }.getOrDefault(false) }
            compose.onNodeWithTag("proposal-existing-0-0").performScrollTo().performClick()
            compose.onNodeWithTag("proposal-link-0").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("proposal-confirm-0").performScrollTo().performClick()
            compose.onNodeWithTag("proposal-link-0").performScrollTo().performClick()
            compose.waitUntil(10_000) { beta.discoveryPage().proposals.single().confirmedAssetId == card.id }
            assertEquals(assets, beta.assets())
            assertEquals(facility.id, beta.assets().single { it.id == card.id }.parentId)
        }
        com.baraa.masroof.lab.beta.BetaContainer.resetOffline(context)
    }
    @Test fun changedArchiveResetsExistingSelectionAndRequiresFreshOwnershipConfirmation() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.baraa.masroof.lab.beta.BetaContainer.resetOffline(context)
        com.baraa.masroof.lab.beta.BetaContainer(context).use { beta ->
            val bank = BankId("BANK_ALJAZIRA")
            beta.setBankEnabled(bank, true)
            beta.importEvidence(com.baraa.masroof.lab.beta.application.evidence.EvidenceRequest("synthetic", com.baraa.masroof.lab.beta.application.evidence.EvidenceOrigin.EXPLICIT_SAMPLE),
                listOf(com.baraa.masroof.lab.beta.application.evidence.EvidenceMessage("provider", "AlJazira", "POS Purchase\nCard: 1234\nCard type: credit\nAmount: 42 SAR\nAvailable balance: 100 SAR\n2026-10-16 12:00", java.time.Instant.parse("2026-10-16T12:00:00Z"), com.baraa.masroof.lab.beta.application.evidence.ReceiptTimeSource.USER_SUPPLIED)))
            val facility = beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA, bank, "facility"), AssetKind.CREDIT_FACILITY, "", ownedConfirmed = true))
            beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA, bank, "card"), AssetKind.CREDIT_CARD, "1234", facility.id, CreditCardRole.PRIMARY, ownedConfirmed = true))
            // A single confirmed owner no longer needs per-message approval. A collision still needs an explicit binding.
            beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA, bank, "other-card"), AssetKind.CREDIT_CARD, "1234", facility.id, CreditCardRole.SUPPLEMENTARY, ownedConfirmed = true))
            val assets = beta.assets()
            compose.setContent { MaterialTheme { BetaShell(0, false, editor = {
                BetaDiscoveryPanel(beta, assets, onSelect = { fail("No new asset") }, onClose = {})
            }, onReturn = {}, onReset = {}, onBackup = {}, onRestore = {}) } }
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("proposal-existing-0-0").assertExists(); true }.getOrDefault(false) }
            compose.onNodeWithTag("proposal-existing-0-0").performScrollTo().performClick()
            compose.onNodeWithTag("proposal-confirm-0").performScrollTo().performClick()
            val id = beta.evidencePage().single().id
            kotlinx.coroutines.runBlocking {
                val db = androidx.room.Room.databaseBuilder(context, com.baraa.masroof.lab.beta.data.BetaDatabase::class.java, com.baraa.masroof.lab.beta.data.BetaDatabase.NAME).build()
                try {
                    val saved = db.evidenceDao().outcome(id)!!
                    val store = com.baraa.masroof.lab.beta.data.RoomBetaEvidenceStore(db)
                    store.begin(id, java.time.Instant.EPOCH)
                    store.complete(id, com.baraa.masroof.lab.beta.application.evidence.EvidenceRoute(com.baraa.masroof.lab.beta.application.evidence.RouteKind.MATCHED, setOf(bank), "full"),
                        com.baraa.masroof.lab.beta.application.evidence.EvidenceComparison(com.baraa.masroof.lab.beta.application.evidence.EvidenceDisposition.REVIEW, emptySet(),
                            saved.baselineJson, org.json.JSONObject(saved.candidateJson).put("reprocessingNote", "recompared").toString()), java.time.Instant.EPOCH)
                } finally { db.close() }
            }
            compose.onNodeWithTag("proposal-link-0").performScrollTo().performClick()
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("proposal-existing-0-0").assertIsNotSelected(); true }.getOrDefault(false) }
            compose.onNodeWithTag("proposal-link-0").assertDoesNotExist()
            assertNull(beta.discoveryPage().proposals.single().confirmedAssetId)
            assertEquals(assets, beta.assets())
            compose.onNodeWithTag("proposal-existing-0-0").performScrollTo().performClick()
            compose.onNodeWithTag("proposal-confirm-0").performScrollTo().assertIsOff()
            compose.onNodeWithTag("proposal-link-0").performScrollTo().assertIsNotEnabled()
        }
        com.baraa.masroof.lab.beta.BetaContainer.resetOffline(context)
    }
    @Test fun suggestedCreditCardRequiresExplicitFacilityRatherThanInventingDebt() {
        val bank = BankId("BANK_ALJAZIRA")
        val proposal = BetaAssetProposal(AssetId(Environment.BETA, bank, "opaque-card"), AssetKind.CREDIT_CARD, "1234", "evidence", "revision")
        compose.setContent { MaterialTheme { BetaShell(0, false, editor = {
            BetaAssetEditor(bank, null, emptyList(), false, false, onSave = { fail("No invented facility") }, onCancel = {}, proposal = proposal)
        }, onReturn = {}, onReset = {}, onBackup = {}, onRestore = {}) } }
        compose.onNodeWithTag("asset-owned-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("asset-save").performScrollTo().assertIsNotEnabled()
    }
}
