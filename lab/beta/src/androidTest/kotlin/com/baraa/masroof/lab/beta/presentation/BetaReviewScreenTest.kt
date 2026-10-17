package com.baraa.masroof.lab.beta.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.LayoutDirection
import com.baraa.masroof.lab.beta.application.corrections.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class BetaReviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun detail(): BetaReviewDetail {
        val asset = OwnedAsset(AssetId(Environment.BETA, BankId("BANK_ALJAZIRA"), "review-account"), AssetKind.ACCOUNT, "1234")
        val input = FinancialClassificationInput(PostingKey(Environment.BETA, "review-transaction"), PostingRevision(0, 1),
            FinancialType.EXPENSE, Money.of("23", Currency.SAR), TransactionTime(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Asia/Riyadh")),
            FinancialEndpoint.Owned(asset.id), FinancialEndpoint.External, mapOf(asset.id to asset), setOf("review-evidence"), true)
        return BetaReviewDetail(BetaReviewToken(input.key, 1, 0, 0, input.evidenceIds, null), CorrectionObservation(input, ObservationSafety.ORDINARY), null, null)
    }

    @Test fun rtlDraftSurvivesRecreationAndCancelNeverWrites() {
        val restoration = StateRestorationTester(compose)
        var saves = 0; var cancels = 0
        restoration.setContent { MaterialTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.verticalScroll(rememberScrollState())) { BetaReviewEditor(detail(), false, false, { cancels++ }, { _, _, _ -> saves++ }) }
        } } }
        compose.onNodeWithTag("review-note").performScrollTo().performTextInput("ملاحظة للمراجعة")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("review-note").assertTextContains("ملاحظة للمراجعة")
        compose.onNodeWithTag("review-cancel").performScrollTo().performClick()
        assertEquals(1, cancels); assertEquals(0, saves)
    }

    @Test fun amountRequiresExplicitConfirmationAndSaveKeepsCurrency() {
        var decision: FinancialDecision? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BetaReviewEditor(detail(), false, false, {}, { _, choice, _ -> decision = choice }) } } }
        compose.onNodeWithTag("review-mode-amount").performScrollTo().performClick()
        compose.onNodeWithTag("review-save").assertIsNotEnabled()
        compose.onNodeWithTag("review-amount").performScrollTo().performTextInput("31.25")
        compose.onNodeWithTag("review-save").assertIsNotEnabled()
        compose.onNodeWithTag("review-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("review-save").performScrollTo().performClick()
        assertEquals(FinancialDecision.ConfirmFamilyOrAmount(amount = Money.of("31.25", Currency.SAR)), decision)
    }

    @Test fun busyBlocksSaveCancelAndChangingTheDraft() {
        var busy by mutableStateOf(false); var writes = 0; var cancels = 0
        compose.setContent { MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) { Column(Modifier.verticalScroll(rememberScrollState())) { BetaReviewEditor(detail(), busy, false, { cancels++ }, { _, _, _ -> writes++; busy = true }) } } }
        compose.onNodeWithTag("review-note").performScrollTo().performTextInput("saved note")
        compose.onNodeWithTag("review-save").performScrollTo().performClick()
        compose.onNodeWithTag("review-save").assertIsNotEnabled()
        compose.onNodeWithTag("review-cancel").assertIsNotEnabled()
        compose.onNodeWithTag("review-note").assertIsNotEnabled()
        compose.onNodeWithTag("review-mode-amount").assertIsNotEnabled()
        assertEquals(1, writes); assertEquals(0, cancels)
    }

    @Test fun actualReviewPanelSavesDurableWholeRecordSuppression() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.baraa.masroof.lab.beta.BetaContainer.resetOffline(context)
        val database = androidx.room.Room.databaseBuilder(context, com.baraa.masroof.lab.beta.data.BetaDatabase::class.java,
            com.baraa.masroof.lab.beta.data.BetaDatabase.NAME).build()
        val selected = detail()
        kotlinx.coroutines.runBlocking {
            selected.observation.input.assets.values.forEach { com.baraa.masroof.lab.beta.data.BetaAssetRegistry(database).register(it) }
            assertTrue(com.baraa.masroof.lab.beta.data.RoomBetaFinancialLedger(database).post(selected.observation.input) is FinancialPostingResult.Applied)
        }
        database.close()
        val container = com.baraa.masroof.lab.beta.BetaContainer(context)
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BetaReviewPanel(container, {}) } } }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("review-item-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("review-item-0").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("review-mode-suppress").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("review-mode-suppress").performScrollTo().performClick()
            compose.onNodeWithTag("review-confirm").performScrollTo().performClick()
            compose.onNodeWithTag("review-save").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("review-item-0").fetchSemanticsNodes().isNotEmpty() }
            assertNull(container.financialPosting(selected.token.key))
            assertEquals(BetaLedgerActivity.SUPPRESSED, container.correction(selected.token.key)!!.activity)
            assertEquals(1, container.correctionHistory(selected.token.key).size)
            assertNotNull(container.financialAuditPosting(selected.token.key))
        } finally { container.close(); com.baraa.masroof.lab.beta.BetaContainer.resetOffline(context) }
    }
}
