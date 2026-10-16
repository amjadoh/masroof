package com.baraa.masroof.lab.beta.processdeath

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.presentation.BetaActivity
import com.baraa.masroof.lab.finance.value.BankId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** External script resets runtime permission before launching this process. Empty CI emulator only. */
class BetaOnboardingPermissionTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun actualSystemDenyThenGrantKeepsManualJourneyAndRestoresBeta() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.READ_SMS))
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { it.setBankEnabled(BankId("BANK_ALJAZIRA"), true) }
        fun systemClick(id: String) {
            val until = android.os.SystemClock.uptimeMillis() + 10_000
            var button: AccessibilityNodeInfo? = null
            while (button == null && android.os.SystemClock.uptimeMillis() < until) {
                button = automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.google.android.permissioncontroller:id/$id")?.firstOrNull()
                    ?: automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$id")?.firstOrNull()
                if (button == null) android.os.SystemClock.sleep(100)
            }
            assertNotNull("Actual Android permission dialog button $id", button)
            assertTrue(button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("beta-history").assertIsEnabled(); true }.getOrDefault(false) }
            compose.onNodeWithTag("beta-badge").assertExists()
            compose.onNodeWithTag("beta-history").performScrollTo().performClick()
            compose.onNodeWithTag("history-days-7").performScrollTo().performClick()
            compose.onNodeWithTag("history-start").performScrollTo().performClick()
            systemClick("permission_deny_button")
            compose.onNodeWithTag("history-denied").performScrollTo().assertExists()
            BetaContainer(context).use { assertNull(it.historySession()); assertTrue(it.evidencePage().isEmpty()) }
            scenario.recreate()
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("history-start").assertIsEnabled(); true }.getOrDefault(false) }
            compose.onNodeWithTag("history-denied").performScrollTo().assertExists()
            compose.onNodeWithTag("history-close").performScrollTo().performClick()
            compose.onNodeWithTag("asset-add-BANK_ALJAZIRA").performScrollTo().performClick()
            compose.onNodeWithTag("asset-last4").performScrollTo().performTextInput("1234")
            compose.onNodeWithTag("asset-save").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("asset-cancel").performScrollTo().performClick()
            compose.onNodeWithTag("beta-history").performScrollTo().performClick()
            compose.onNodeWithTag("history-days-7").performScrollTo().performClick()
            compose.onNodeWithTag("history-start").performScrollTo().performClick()
            systemClick("permission_allow_button")
            compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("history-close").assertIsEnabled(); compose.onNodeWithTag("history-progress").assertExists(); true }.getOrDefault(false) }
            assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.READ_SMS))
            // Grant follows this explicit Start; nothing was read on welcome, denial, or recreation.
            BetaContainer(context).use { assertNotNull(it.historySession()); assertEquals(7L * 86_400_000L, it.historySession()!!.until - it.historySession()!!.from) }
            compose.onNodeWithTag("history-close").performScrollTo().performClick()
            compose.onNodeWithTag("beta-return").assertIsEnabled()
        }
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.withContext(com.baraa.masroof.lab.beta.BetaIo.dispatcher) {} }
        BetaContainer.resetOffline(context)
    }
}
