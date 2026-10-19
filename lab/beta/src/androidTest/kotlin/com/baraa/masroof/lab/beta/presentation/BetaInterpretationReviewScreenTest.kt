package com.baraa.masroof.lab.beta.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
        compose.onNodeWithTag("history-filter-all").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("history-review-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("history-review-0").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("label-original").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("label-field").performScrollTo().performClick()
        compose.onNodeWithTag("label-field-AMOUNT").performClick()
        compose.onNodeWithTag("label-value").performScrollTo().performTextInput("43")
        compose.onNodeWithTag("label-save").performScrollTo().assertIsNotEnabled()
        assertTrue(beta.interpretationReview(result.summary.id)!!.labels.isEmpty())
        compose.onNodeWithTag("label-original").performScrollTo().performTextInputSelection(TextRange(body.indexOf("42 SAR"), body.indexOf("42 SAR") + 2))
        compose.waitUntil(15000) { compose.onAllNodesWithTag("label-selected").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("label-selected").performScrollTo().assertTextContains("42", substring = true)
        compose.onNodeWithTag("label-confirm").performScrollTo().performClick()
        awaitSaveReady()
        compose.onNodeWithTag("label-save").performScrollTo().performClick()
        compose.waitUntil(15000) { beta.interpretationReview(result.summary.id)!!.labels.size == 1 }
        compose.waitUntil(15000) { compose.onAllNodesWithText(context.getString(com.baraa.masroof.lab.beta.R.string.beta_label_count, 1)).fetchSemanticsNodes().isNotEmpty() }
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
        compose.waitUntil(15000) { compose.onAllNodesWithTag("label-original").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("label-field").performScrollTo().performClick()
        compose.onNodeWithTag("label-field-AMOUNT").performClick()
        compose.onNodeWithTag("label-value").performScrollTo().performTextInput("43")
        compose.onNodeWithTag("label-original").performScrollTo().performTextInputSelection(TextRange(body.indexOf("42 SAR"), body.indexOf("42 SAR") + 2))
        compose.waitUntil(15000) { compose.onAllNodesWithTag("label-selected").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("label-selected").performScrollTo().assertTextContains("42", substring = true)
        compose.onNodeWithTag("label-confirm").performScrollTo().performClick()
        awaitSaveReady()
        compose.onNodeWithTag("label-save").performScrollTo().assertIsEnabled()
        beta.reprocessEvidence(summary.id)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("label-original").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("label-confirm").performScrollTo().assertIsOff()
        compose.onNodeWithTag("label-save").performScrollTo().assertIsNotEnabled()
        assertTrue(beta.interpretationReview(summary.id)!!.labels.isEmpty())
    }

    private fun awaitSaveReady() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("label-save").fetchSemanticsNodes().singleOrNull()?.config
                ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
        }
    }

    @Test fun uiEffectsAndActionsResumeOnMainAfterIo() {
        val effectGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val actionGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val effectEntered = java.util.concurrent.atomic.AtomicBoolean(false)
        val actionEntered = java.util.concurrent.atomic.AtomicBoolean(false)
        compose.setContent {
            val scope = rememberBetaUiScope()
            var effectDone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var actionDone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            BetaLaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(com.baraa.masroof.lab.beta.BetaIo.dispatcher) {
                    effectEntered.set(true); effectGate.await()
                    assertNotEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
                }
                assertEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
                effectDone = true
            }
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(if (effectDone) "effect-main" else "effect-pending")
                androidx.compose.material3.Button(onClick = {
                    scope.launch {
                        kotlinx.coroutines.withContext(com.baraa.masroof.lab.beta.BetaIo.dispatcher) {
                            actionEntered.set(true); actionGate.await()
                        }
                        assertEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
                        actionDone = true
                    }
                }) { androidx.compose.material3.Text("start-main-action") }
                androidx.compose.material3.Text(if (actionDone) "action-main" else "action-pending")
            }
        }
        compose.waitUntil(15000) { effectEntered.get() }
        compose.runOnIdle { } // The UI has yielded while IO is still suspended.
        effectGate.complete(Unit)
        compose.waitUntil(15000) { compose.onAllNodesWithText("effect-main").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("start-main-action").performClick()
        compose.waitUntil(15000) { actionEntered.get() }
        compose.runOnIdle { }
        actionGate.complete(Unit)
        compose.waitUntil(15000) { compose.onAllNodesWithText("action-main").fetchSemanticsNodes().isNotEmpty() }
    }

}
