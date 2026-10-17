package com.baraa.masroof.lab.beta.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.baraa.masroof.lab.beta.application.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger

class BetaDashboardControlsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 10)
    private class Controls : BetaDashboardControls {
        var state = BetaDashboardConfiguration(1, BetaDashboardConfigurationDraft())
        var hold: CompletableDeferred<Unit>? = null
        var conflict = false
        val saves = AtomicInteger()
        override suspend fun read() = state
        override suspend fun save(expectedRevision: Long, draft: BetaDashboardConfigurationDraft): BetaDashboardConfigurationWrite {
            saves.incrementAndGet(); hold?.await()
            if (conflict || expectedRevision != state.revision) return BetaDashboardConfigurationWrite.Conflict(state.copy(revision = state.revision + 1))
            state = BetaDashboardConfiguration(state.revision + 1, draft)
            return BetaDashboardConfigurationWrite.Saved(state)
        }
    }
    private fun show(controls: BetaDashboardControls) {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            BetaDashboardConfigurationPanel(controls, emptyList(), month, true, {}, {})
        } } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("controls-salary").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun node(tag: String) = compose.onNodeWithTag(tag)
    @Test fun cancelNeverWritesAndConfirmedBusySaveBlocksDismissalAndDuplicateSubmission() {
        val controls = Controls(); show(controls)
        node("controls-salary").performScrollTo().performClick()
        node("controls-day").performTextInput("27")
        node("dash-sheet-cancel").performClick()
        assertEquals(0, controls.saves.get()); assertNull(controls.state.draft.salary)
        node("controls-salary").performScrollTo().performClick()
        node("controls-day").performTextInput("27")
        node("dash-sheet-apply").performClick()
        assertEquals(0, controls.saves.get())
        controls.hold = CompletableDeferred()
        node("controls-confirm").performClick()
        compose.waitUntil { controls.saves.get() == 1 }
        node("controls-confirm").assertIsNotEnabled()
        compose.onAllNodesWithTag("dash-sheet-cancel").onFirst().assertIsNotEnabled()
        compose.runOnIdle { controls.hold!!.complete(Unit) }
        compose.waitUntil { compose.onAllNodesWithTag("dash-sheet").fetchSemanticsNodes().isEmpty() }
        assertEquals(27, controls.state.draft.salary!!.day); assertEquals(1, controls.saves.get())
    }
    @Test fun staleRevisionRequiresDiscardAndReloadRatherThanOverwritingNewerConfiguration() {
        val controls = Controls(); show(controls)
        node("controls-salary").performScrollTo().performClick(); node("controls-day").performTextInput("27")
        node("dash-sheet-apply").performClick(); controls.conflict = true; node("controls-confirm").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("controls-conflict").fetchSemanticsNodes().isNotEmpty() }
        node("dash-sheet-apply").assertIsNotEnabled(); assertNull(controls.state.draft.salary)
        node("controls-reload").performScrollTo().performClick()
        compose.waitUntil { compose.onAllNodesWithTag("dash-sheet").fetchSemanticsNodes().isEmpty() }
        assertEquals(1, controls.saves.get())
    }
    @Test fun restoredDraftKeepsOriginalRevisionAndDetectsNewerSettingsAfterRecreation() {
        val controls = Controls()
        val restorer = StateRestorationTester(compose)
        restorer.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            BetaDashboardConfigurationPanel(controls, emptyList(), month, true, {}, {})
        } } }
        compose.waitUntil { compose.onAllNodesWithTag("controls-salary").fetchSemanticsNodes().isNotEmpty() }
        node("controls-salary").performScrollTo().performClick(); node("controls-day").performTextInput("27")
        controls.state = controls.state.copy(revision = 2)
        restorer.emulateSavedInstanceStateRestore()
        compose.waitUntil { compose.onAllNodesWithTag("controls-day").fetchSemanticsNodes().isNotEmpty() }
        node("controls-day").assertTextContains("27")
        node("controls-conflict").assertExists(); node("dash-sheet-apply").assertIsNotEnabled()
        assertEquals(0, controls.saves.get())
        node("dash-sheet-cancel").performClick()
    }
    @Test fun recurringDraftValidationAndManualPaidDecisionNeverCreateAnImplicitPayment() {
        val controls = Controls(); show(controls)
        node("controls-add").performScrollTo().performClick()
        node("controls-label").performTextInput("Synthetic rent")
        node("controls-amount").performTextInput("-1")
        node("controls-day").performScrollTo().performTextInput("31")
        node("dash-sheet-apply").performClick(); node("controls-invalid").assertExists(); assertEquals(0, controls.saves.get())
        node("controls-amount").performScrollTo().performTextReplacement("1200.00")
        node("dash-sheet-apply").performClick(); node("controls-confirm").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("dash-sheet").fetchSemanticsNodes().isEmpty() }
        val row = controls.state.draft.commitments.single()
        node("controls-paid-${row.id}").performScrollTo().performClick(); node("dash-sheet-cancel").performClick()
        assertTrue(controls.state.draft.commitments.single().paidMonths.isEmpty())
        node("controls-paid-${row.id}").performScrollTo().performClick(); node("dash-sheet-apply").performClick(); node("controls-confirm").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("dash-sheet").fetchSemanticsNodes().isEmpty() }
        assertEquals(setOf(month), controls.state.draft.commitments.single().paidMonths)
        node("controls-delete-${row.id}").performScrollTo().performClick(); node("dash-sheet-cancel").performClick()
        assertEquals(1, controls.state.draft.commitments.size)
    }
}
