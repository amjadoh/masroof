package com.baraa.masroof.lab.beta.presentation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BetaSettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun themePersistsAndDestructiveConfirmationSurvivesRecreationWithoutWriting() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta ->
            beta.putMarker("synthetic", "keep")
            val state = StateRestorationTester(compose)
            state.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BetaSettingsPanel(beta, {}) } } }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("beta-theme-DARK").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("beta-theme-DARK").performScrollTo().performClick()
            compose.waitUntil(10000) { beta.settings().theme == "DARK" }
            compose.onNodeWithTag("beta-settings-reset").performScrollTo().performClick()
            state.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("beta-settings-confirm").assertIsDisplayed()
            compose.onNodeWithTag("beta-settings-cancel").performClick()
            assertEquals(1, beta.markerCount()); assertEquals("DARK", beta.settings().theme)
        }
    }

    @Test fun actualSettingsBackupAndRestoreRequireExplicitConfirmationAndKeepOriginalOnCorruption() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta ->
            beta.putMarker("original", "synthetic")
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BetaSettingsPanel(beta, {}) } } }
            compose.onNodeWithTag("beta-settings-backup").performScrollTo().performClick()
            compose.waitUntil(10000) { beta.settings().backupAvailable }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("beta-settings-busy").fetchSemanticsNodes().isEmpty() }
            beta.putMarker("new", "synthetic")
            compose.onNodeWithTag("beta-settings-restore").performScrollTo().performClick()
            assertEquals(2, beta.markerCount())
            compose.onNodeWithTag("beta-settings-confirm").performClick()
            compose.waitUntil(10000) { beta.markerCount() == 1 }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("beta-settings-busy").fetchSemanticsNodes().isEmpty() }
            beta.putMarker("new", "keep")
            File(beta.backupDatabaseFile().parentFile, "preferences.json").appendText("broken")
            compose.onNodeWithTag("beta-settings-restore").performScrollTo().performClick()
            compose.onNodeWithTag("beta-settings-confirm").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("beta-settings-busy").fetchSemanticsNodes().isEmpty() }
            assertEquals(2, beta.markerCount())
            compose.onNodeWithTag("beta-settings-result").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("beta-settings-close").assertIsEnabled()
        }
    }
}
