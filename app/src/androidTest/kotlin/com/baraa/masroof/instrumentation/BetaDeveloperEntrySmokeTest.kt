package com.baraa.masroof.instrumentation

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.BuildConfig
import com.baraa.masroof.R
import com.baraa.masroof.presentation.settings.*
import com.baraa.masroof.ui.theme.MasroofTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Navigation callback contract only; no inbox, financial repository or database is accessed. */
class BetaDeveloperEntrySmokeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun betaEntryRemainsAvailableOnlyThroughAboutAndDeveloperSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.baraa.masroof", context.packageName)
        assertTrue(BuildConfig.ENABLE_MASROOF_BETA)
        compose.setContent {
            var screen by remember { mutableStateOf("settings") }
            MasroofTheme {
                when (screen) {
                    "settings" -> SettingsHubScreen(state = SettingsUiState(loading = false), reviewRequiredCount = 0,
                        onBack = {}, onOpenAccounts = {}, onOpenCards = {}, onOpenLoans = {}, onOpenCommitments = {},
                        onOpenReview = {}, onOpenApp = {}, onOpenDataBackup = {}, onOpenStatementReconciliation = {},
                        onOpenAbout = { screen = "about" })
                    "about" -> SettingsAboutScreen(appVersion = "test", isNightlyBuild = false,
                        updateChannel = com.baraa.masroof.application.update.UpdateChannel.STABLE,
                        githubTokenConfigured = false, updateState = SettingsUiState().updateState, updateMessage = null,
                        onBack = { screen = "settings" }, onOpenLogs = {}, onOpenDeveloperSettings = { screen = "developer" },
                        onSaveGithubToken = {}, onClearGithubToken = {}, onSelectUpdateChannel = {}, onCheckForUpdates = {},
                        onDownloadUpdate = {}, onInstallUpdate = {}, onClearUpdateMessage = {})
                    "developer" -> SettingsDeveloperScreen(onBack = { screen = "about" }, onOpenBeta = { screen = "beta" })
                    else -> Text("Beta callback reached")
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.settings_developer_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_about_section)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_developer_title)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.beta_entry_title)).performClick()
        compose.onNodeWithText("Beta callback reached").assertIsDisplayed()
    }
}
