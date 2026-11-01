package com.baraa.masroof.instrumentation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.baraa.masroof.application.maintenance.StartupMaintenanceOutcome
import com.baraa.masroof.presentation.startup.rememberStartupMaintenanceOutcome
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class StartupGateRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savedReady_doesNotExposeFinancialContentWhileNewMaintenanceIsPending() {
        var maintenance = CompletableDeferred(StartupMaintenanceOutcome.READY)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val outcome by rememberStartupMaintenanceOutcome({ maintenance.await() }, {})
            if (outcome == StartupMaintenanceOutcome.READY) Text("financial content")
        }
        compose.onNodeWithText("financial content").assertExists()
        compose.runOnIdle { maintenance = CompletableDeferred() }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("financial content").assertDoesNotExist()
        compose.runOnIdle { maintenance.complete(StartupMaintenanceOutcome.BLOCKED) }
        compose.onNodeWithText("financial content").assertDoesNotExist()
    }

    @Test
    fun blockedStartupKeepsRetryAndBackupRestoreReachable() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        var retries = 0
        var restores = 0
        compose.setContent {
            com.baraa.masroof.ui.theme.MasroofTheme {
                com.baraa.masroof.presentation.startup.StartupBlockedScreen(onRetry = { retries++ }, onRestoreBackup = { restores++ })
            }
        }
        compose.onNodeWithText(context.getString(com.baraa.masroof.R.string.startup_maintenance_blocked)).assertExists()
        compose.onNodeWithText(context.getString(com.baraa.masroof.R.string.dashboard_retry)).performClick()
        compose.onNodeWithText(context.getString(com.baraa.masroof.R.string.reference_restore_title)).performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(1, retries)
            org.junit.Assert.assertEquals(1, restores)
        }
    }
}
