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
        assertEquals(34, context.applicationInfo.targetSdkVersion)
        fun systemClick(id: String) {
            val info = automation.serviceInfo
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            fun roots() = (listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root })
            val until = android.os.SystemClock.uptimeMillis() + 10_000
            var button: AccessibilityNodeInfo? = null
            while (button == null && android.os.SystemClock.uptimeMillis() < until) {
                button = roots().firstNotNullOfOrNull { root ->
                    root.findAccessibilityNodeInfosByViewId("com.google.android.permissioncontroller:id/$id").firstOrNull()
                        ?: root.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$id").firstOrNull()
                }
                if (button == null) android.os.SystemClock.sleep(100)
            }
            // Empty isolated CI emulator only; diagnostics never read node text or content descriptions.
            val directory = java.io.File(context.getExternalFilesDir(null), "beta-permission-diagnostics").apply { mkdirs() }
            val safeIds = linkedSetOf<String>()
            val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
            roots().forEach(queue::add)
            var visited = 0
            while (queue.isNotEmpty() && visited++ < 128) {
                val node = queue.removeFirst()
                node.packageName?.toString()?.let { safeIds.add("package=$it") }
                node.viewIdResourceName?.let { safeIds.add("id=$it") }
                (0 until node.childCount).mapNotNull { node.getChild(it) }.forEach(queue::add)
            }
            java.io.File(directory, "$id.txt").writeText(
                "targetSdk=${context.applicationInfo.targetSdkVersion}\nflags=${automation.serviceInfo.flags}\npermission=${context.checkSelfPermission(Manifest.permission.READ_SMS)}\n" + safeIds.joinToString("\n"))
            automation.takeScreenshot()?.let { bitmap ->
                try { java.io.File(directory, "$id.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                finally { bitmap.recycle() }
            }
            assertNotNull("Actual Android permission dialog button $id; resource-only diagnostics saved", button)
            assertTrue(button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        fun awaitEnabled(tag: String) {
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithTag(tag).assertIsEnabled(); true }.getOrDefault(false)
            }
        }
        ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("beta-history").assertIsEnabled(); true }.getOrDefault(false) }
            compose.onNodeWithTag("beta-badge").assertExists()
            awaitEnabled("beta-history")
            compose.onNodeWithTag("beta-history").performScrollTo().performClick()
            awaitEnabled("history-days-7")
            compose.onNodeWithTag("history-days-7").performScrollTo().performClick()
            awaitEnabled("history-start")
            compose.onNodeWithTag("history-start").performScrollTo().performClick()
            systemClick("permission_deny_button")
            compose.onNodeWithTag("history-denied").performScrollTo().assertExists()
            BetaContainer(context).use { assertNull(it.historySession()); assertTrue(it.evidencePage().isEmpty()) }
            scenario.recreate()
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("history-start").assertIsEnabled(); true }.getOrDefault(false) }
            compose.onNodeWithTag("history-denied").performScrollTo().assertExists()
            awaitEnabled("history-close")
            compose.onNodeWithTag("history-close").performScrollTo().performClick()
            awaitEnabled("asset-add-BANK_ALJAZIRA")
            compose.onNodeWithTag("asset-add-BANK_ALJAZIRA").performScrollTo().performClick()
            awaitEnabled("asset-last4")
            compose.onNodeWithTag("asset-last4").performScrollTo().performTextInput("1234")
            compose.onNodeWithTag("asset-save").performScrollTo().assertIsNotEnabled()
            awaitEnabled("asset-cancel")
            compose.onNodeWithTag("asset-cancel").performScrollTo().performClick()
            awaitEnabled("beta-history")
            compose.onNodeWithTag("beta-history").performScrollTo().performClick()
            awaitEnabled("history-days-7")
            compose.onNodeWithTag("history-days-7").performScrollTo().performClick()
            awaitEnabled("history-start")
            compose.onNodeWithTag("history-start").performScrollTo().performClick()
            systemClick("permission_allow_button")
            compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("history-close").assertIsEnabled(); compose.onNodeWithTag("history-progress").assertExists(); true }.getOrDefault(false) }
            assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.READ_SMS))
            // Grant follows this explicit Start; nothing was read on welcome, denial, or recreation.
            BetaContainer(context).use { assertNotNull(it.historySession()); assertEquals(7L * 86_400_000L, it.historySession()!!.until - it.historySession()!!.from) }
            awaitEnabled("history-close")
            compose.onNodeWithTag("history-close").performScrollTo().performClick()
            compose.onNodeWithTag("beta-return").assertIsEnabled()
        }
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.withContext(com.baraa.masroof.lab.beta.BetaIo.dispatcher) {} }
        BetaContainer.resetOffline(context)
    }
}
