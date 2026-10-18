package com.baraa.masroof.lab.beta.presentation

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.beta.BetaIo
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BetaSettingsActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun actualActivityRestoresSettingsRouteThemeAndUncommittedConfirmationAfterRecreation() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { it.putMarker("synthetic", "keep") }
        ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
            compose.waitUntil(15000) { compose.onAllNodesWithTag("beta-start-continue").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onNodeWithTag("beta-start-continue").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("beta-tools").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onNodeWithTag("beta-reset").assertDoesNotExist()
            compose.onNodeWithTag("beta-tools").performClick()
            compose.onNodeWithTag("beta-developer-tools").performScrollTo().performClick()
            compose.onNodeWithTag("beta-theme-DARK").performScrollTo().performClick()
            compose.waitUntil(10000) { context.getSharedPreferences(BetaEnvironment.PREFS_NAME, 0).getString("beta_ui_theme", null) == "DARK" }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("beta-settings-busy").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("beta-settings-reset").performScrollTo().performClick()
            scenario.recreate()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("beta-settings-confirm").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onNodeWithTag("beta-settings-cancel").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("beta-settings-panel").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("beta-settings-panel").assertExists()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("beta-theme-DARK").fetchSemanticsNodes().singleOrNull()?.config
                ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == true }
            compose.onNodeWithTag("beta-theme-DARK").performScrollTo().assertIsNotEnabled()
        }
        waitForClose()
        BetaContainer(context).use { assertEquals(1, it.markerCount()); assertEquals("DARK", it.settings().theme) }
    }

    @Test fun malformedRecoveryShowsSafeRetryAndDoesNotCreateReplacementData() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { it.putMarker("synthetic", "keep") }
        val journal = File(context.filesDir, "beta_recovery_v1/restore_journal")
        journal.writeText("corrupted journal")
        ActivityScenario.launch(BetaActivity::class.java).use {
            compose.waitUntil(15000) { compose.onAllNodesWithTag("beta-startup-failure").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onNodeWithTag("beta-settings").assertDoesNotExist()
            assertTrue(context.getDatabasePath(BetaEnvironment.DATABASE_NAME).length() > 0)
            journal.delete()
            compose.onNodeWithTag("beta-startup-retry").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("beta-start-continue").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onNodeWithTag("beta-start-continue").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("beta-tools").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        }
        waitForClose()
        BetaContainer(context).use { assertEquals(1, it.markerCount()) }
    }
    private fun waitForClose() { val done = CountDownLatch(1); BetaIo.execute { done.countDown() }; assertTrue(done.await(15, TimeUnit.SECONDS)) }
}
