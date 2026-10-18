package com.baraa.masroof.lab.beta.presentation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.value.BankId
import java.time.Instant
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BetaInterpretationReviewScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var beta: BetaContainer
    @Before fun open() { BetaContainer.resetOffline(context); beta = BetaContainer(context) }
    @After fun close() { beta.close(); BetaContainer.resetOffline(context) }

    @Test fun historyReviewSavesOnlyAnExplicitOriginalSpanLabel() {
        beta.setBankEnabled(BankId("BANK_ALJAZIRA"), true)
        val body = "Credit card purchase\nCard: 1234\nAmount: 42 SAR\nMerchant: Synthetic Shop\nDate: 2026-10-18 12:00"
        val result = beta.importEvidence(EvidenceRequest("m7-ui", EvidenceOrigin.EXPLICIT_SAMPLE), listOf(
            EvidenceMessage("one", "AlJazira", body, Instant.parse("2026-10-18T12:00:00Z"), ReceiptTimeSource.USER_SUPPLIED))).single() as EvidenceResult.Stored
        compose.setContent { MaterialTheme { BetaShell(0, false, editor = {
            BetaHistoryPanel(beta, onClose = {})
        }, onReturn = {}, onReset = {}, onBackup = {}, onRestore = {}) } }
        compose.waitUntil { compose.onAllNodesWithTag("history-review-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("history-review-0").performScrollTo().performClick()
        compose.waitUntil { compose.onAllNodesWithTag("label-original").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("label-field").performScrollTo().performClick()
        compose.onNodeWithTag("label-field-AMOUNT").performClick()
        compose.onNodeWithTag("label-value").performScrollTo().performTextInput("43")
        compose.onNodeWithTag("label-save").performScrollTo().assertIsNotEnabled()
        assertTrue(beta.interpretationReview(result.summary.id)!!.labels.isEmpty())
        compose.onNodeWithTag("label-original").performScrollTo().performTextInputSelection(TextRange(body.indexOf("42 SAR"), body.indexOf("42 SAR") + 2))
        compose.onNodeWithTag("label-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("label-save").performScrollTo().performClick()
        compose.waitUntil { beta.interpretationReview(result.summary.id)!!.labels.size == 1 }
        compose.waitUntil { compose.onAllNodesWithText(context.getString(com.baraa.masroof.lab.beta.R.string.beta_label_count, 1)).fetchSemanticsNodes().isNotEmpty() }
        val label = beta.interpretationReview(result.summary.id)!!.labels.single()
        assertEquals("43", label.value); assertEquals("42", label.originalText)
        assertEquals("42.00", beta.interpretationReview(result.summary.id)!!.observed[BetaLabelField.AMOUNT])
        beta.close(); beta = BetaContainer(context)
        assertEquals(label, beta.interpretationReview(result.summary.id)!!.labels.single())
        assertTrue(beta.assets().isEmpty())
    }
    @Test fun restoredDraftNeedsFreshConfirmation() {
        beta.setBankEnabled(BankId("BANK_ALJAZIRA"), true)
        val body = "Credit card purchase\nCard: 1234\nAmount: 42 SAR\nDate: 2026-10-18 12:00"
        val summary = (beta.importEvidence(EvidenceRequest("m7-ui-restore", EvidenceOrigin.EXPLICIT_SAMPLE), listOf(
            EvidenceMessage("one", "AlJazira", body, Instant.parse("2026-10-18T12:00:00Z"), ReceiptTimeSource.USER_SUPPLIED))).single() as EvidenceResult.Stored).summary
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { BetaShell(0, false, editor = {
            BetaInterpretationReviewPanel(beta, summary.id, onClose = {})
        }, onReturn = {}, onReset = {}, onBackup = {}, onRestore = {}) } }
        compose.waitUntil { compose.onAllNodesWithTag("label-original").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("label-field").performScrollTo().performClick()
        compose.onNodeWithTag("label-field-AMOUNT").performClick()
        compose.onNodeWithTag("label-value").performScrollTo().performTextInput("43")
        compose.onNodeWithTag("label-original").performScrollTo().performTextInputSelection(TextRange(body.indexOf("42 SAR"), body.indexOf("42 SAR") + 2))
        compose.onNodeWithTag("label-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("label-save").performScrollTo().assertIsEnabled()
        beta.reprocessEvidence(summary.id)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil { compose.onAllNodesWithTag("label-original").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("label-confirm").performScrollTo().assertIsOff()
        compose.onNodeWithTag("label-save").performScrollTo().assertIsNotEnabled()
        assertTrue(beta.interpretationReview(summary.id)!!.labels.isEmpty())
    }

}
