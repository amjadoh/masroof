package com.baraa.masroof.lab.beta.presentation

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.finance.value.BankId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BetaHistoryPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun busyReturnAndSystemBackStayBlockedUntilPaused() {
        var allowReturn by mutableStateOf(false)
        var returned = 0
        compose.setContent {
            MaterialTheme { BetaShell(0, false, returnEnabled = allowReturn, onReturn = { returned++ }, onBackup = {}, onRestore = {}, onReset = {}) }
        }
        compose.onNodeWithTag("beta-return").assertIsNotEnabled()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed(); assertEquals(0, returned) }
        compose.runOnIdle { allowReturn = true }
        compose.onNodeWithTag("beta-return").assertIsEnabled()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed(); assertEquals(1, returned) }
    }
    @Test fun deniedPermissionCallbackLeavesManualRouteAndDoesNotCapture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(BankId("BANK_ALJAZIRA"), true)
            val deniedContext = object : ContextWrapper(context) {
                override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                    if (permission == Manifest.permission.READ_SMS) PackageManager.PERMISSION_DENIED else super.checkPermission(permission, pid, uid)
                override fun checkSelfPermission(permission: String): Int =
                    if (permission == Manifest.permission.READ_SMS) PackageManager.PERMISSION_DENIED else super.checkSelfPermission(permission)
            }
            var launches = 0
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry = object : ActivityResultRegistry() {
                    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                        launches++
                        dispatchResult(requestCode, false)
                    }
                }
            }
            var closed = false
            compose.setContent {
                CompositionLocalProvider(LocalContext provides deniedContext, LocalActivityResultRegistryOwner provides owner) {
                    MaterialTheme { BetaShell(0, false, editor = { BetaHistoryPanel(beta, { closed = true }) }, onReturn = {}, onBackup = {}, onRestore = {}, onReset = {}) }
                }
            }
            compose.onNodeWithTag("history-start").performScrollTo().performClick()
            compose.onNodeWithTag("history-denied").performScrollTo().assertExists()
            compose.onNodeWithTag("history-close").performScrollTo().performClick()
            compose.runOnIdle { assertTrue(closed); assertEquals(1, launches) }
            assertNull(beta.historySession())
            assertTrue(beta.evidencePage().isEmpty())
        }
        BetaContainer.resetOffline(context)
    }
    @Test fun firstBatchAccessFailureKeepsSavedScopeAndResumeVisible() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun appOp(mode: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("appops set ${context.packageName} READ_SMS $mode")).use { it.readBytes() }
        }
        BetaContainer.resetOffline(context)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
        appOp("ignore")
        try {
            BetaContainer(context).use { beta ->
                beta.setBankEnabled(BankId("BANK_ALJAZIRA"), true)
                compose.setContent {
                    MaterialTheme { BetaShell(0, false, editor = { BetaHistoryPanel(beta, {}) }, onReturn = {}, onBackup = {}, onRestore = {}, onReset = {}) }
                }
                compose.onNodeWithTag("history-start").performScrollTo().performClick()
                compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("history-resume").assertIsEnabled(); true }.getOrDefault(false) }
                compose.onNodeWithTag("history-resume").performScrollTo().assertIsEnabled()
                assertNotNull(beta.historySession())
                assertEquals(0, beta.historySession()!!.scanned)
                assertTrue(beta.evidencePage().isEmpty())
            }
        } finally {
            appOp("default")
            BetaContainer.resetOffline(context)
        }
    }
    @Test fun openingAndRestoringScreenNeverStartsReadingAndPreservesSavedScope() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(BankId("BANK_ALJAZIRA"), true)
            val saved = beta.startHistory(7, 1000)
            val restoration = StateRestorationTester(compose)
            restoration.setContent {
                MaterialTheme { BetaShell(0, false, editor = { BetaHistoryPanel(beta, {}) }, onReturn = {}, onBackup = {}, onRestore = {}, onReset = {}) }
            }
            compose.onNodeWithTag("beta-badge").assertExists()
            compose.onNodeWithTag("beta-return").assertExists()
            compose.onNodeWithTag("history-resume").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("history-days-90").performScrollTo().performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("history-resume").performScrollTo().assertExists()
            assertEquals(saved, beta.historySession())
            assertTrue(beta.evidencePage().isEmpty())
        }
        BetaContainer.resetOffline(context)
    }
}
