package com.baraa.masroof.lab.beta.presentation

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.BetaStartState
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.*
import org.junit.Assert.*

/** Synthetic instrumentation package on CI emulator only; never resets a user installation. */
class BetaStartActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun isolatedEmulator() = context.packageName == "com.baraa.masroof.lab.beta.test" &&
        (Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
    @Before fun setup() {
        Assume.assumeTrue("Synthetic emulator only", isolatedEmulator())
        BetaContainer.resetOffline(context)
    }
    @After fun cleanup() { if (isolatedEmulator()) { drain(); BetaContainer.resetOffline(context) } }

    @Test fun freshWelcomeSurvivesBackAndRecreationUntilExplicitStart() {
        val protected = protectedFiles()
        val before = protected.map(::hash)
        ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
            await("beta-start-new")
            compose.onNodeWithTag("dash-refresh").assertDoesNotExist()
            compose.onNodeWithTag("beta-history").assertDoesNotExist()
            compose.onNodeWithTag("beta-settings").assertDoesNotExist()
            capture("m0-new-welcome")
            scenario.recreate(); await("beta-start-new")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        drain()
        BetaContainer(context).use { assertEquals(BetaStartState.NEW, it.startState()); assertTrue(it.evidencePage().isEmpty()); assertNull(it.historySession()) }
        ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
            await("beta-start-new"); compose.onNodeWithTag("beta-start-continue").performClick()
            await("dash-refresh"); compose.onNodeWithTag("beta-start-new").assertDoesNotExist()
            scenario.recreate(); await("dash-refresh")
            compose.onNodeWithTag("beta-return").performClick()
        }
        drain()
        BetaContainer(context).use {
            assertEquals(BetaStartState.READY, it.startState()); assertTrue(it.assets().isEmpty())
            assertTrue(it.bankChoices().all { choice -> choice.state == null }); assertTrue(it.evidencePage().isEmpty()); assertNull(it.historySession())
        }
        assertEquals(before, protected.map(::hash)); assertProtectedPreferences()
    }

    @Test fun previousBetaDataRequiresExplicitResumeAndIsNeverReset() {
        val protected = protectedFiles(); val before = protected.map(::hash)
        val bank = BankId("BANK_ALJAZIRA")
        val asset = OwnedAsset(AssetId(Environment.BETA, bank, "synthetic-m0-existing"), AssetKind.ACCOUNT, "4321")
        BetaContainer(context).use { beta -> beta.registerAsset(asset); beta.setBankEnabled(bank, true); beta.putMarker("human-note", "synthetic-keep"); beta.putPreferenceProbe("keep") }
        ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
            await("beta-start-existing"); compose.onNodeWithTag("dash-refresh").assertDoesNotExist()
            capture("m0-existing-preserved")
            compose.onNodeWithTag("beta-return").performClick()
        }
        drain()
        BetaContainer(context).use { assertEquals(BetaStartState.EXISTING, it.startState()); assertEquals(listOf(asset), it.assets()); assertEquals(1, it.markerCount()) }
        ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
            await("beta-start-existing"); compose.onNodeWithTag("beta-start-continue").performClick(); await("dash-refresh")
            scenario.recreate(); await("dash-refresh"); compose.onNodeWithTag("beta-start-existing").assertDoesNotExist()
        }
        drain()
        BetaContainer(context).use { assertEquals(BetaStartState.READY, it.startState()); assertEquals(listOf(asset), it.assets()); assertEquals(1, it.markerCount()); assertEquals("keep", it.preferenceProbe()) }
        assertEquals(before, protected.map(::hash)); assertProtectedPreferences()
    }

    @Test fun corruptExistingBetaIsRetainedAndOnlyOffersRetryOrReturn() {
        val protected = protectedFiles(); val before = protected.map(::hash)
        val file = context.getDatabasePath(BetaEnvironment.DATABASE_NAME)
        file.parentFile?.mkdirs(); file.writeText("synthetic-corrupt-beta-must-not-be-deleted")
        val original = hash(file)
        ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
            await("beta-startup-failure"); compose.onNodeWithTag("dash-refresh").assertDoesNotExist()
            compose.onNodeWithTag("beta-start-continue").assertDoesNotExist()
            capture("m0-corrupt-retained")
            compose.onNodeWithTag("beta-startup-retry").performClick(); await("beta-startup-failure")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        drain(); assertTrue(file.isFile); assertEquals(original, hash(file)); assertEquals(before, protected.map(::hash)); assertProtectedPreferences()
    }

    private fun await(tag: String) { compose.waitUntil(20000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }; compose.waitForIdle() }
    private fun drain() { val done = CountDownLatch(1); BetaIo.execute { done.countDown() }; assertTrue(done.await(20, TimeUnit.SECONDS)) }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    private fun protectedFiles(): List<File> {
        BetaEnvironment.forbiddenPreferenceNames.forEach { name -> assertTrue(context.getSharedPreferences(name, 0).edit().putString("m0-sentinel", "synthetic-$name").commit()) }
        return listOf(BetaEnvironment.PRODUCTION_DATABASE_NAME, BetaEnvironment.PARSING_LAB_DATABASE_NAME,
            BetaEnvironment.MIGRATION_STAGING_DATABASE_NAME).map { name -> context.getDatabasePath(name).apply { parentFile?.mkdirs(); writeText("synthetic-$name") } }
    }
    private fun assertProtectedPreferences() { BetaEnvironment.forbiddenPreferenceNames.forEach { name -> assertEquals("synthetic-$name", context.getSharedPreferences(name, 0).getString("m0-sentinel", null)) } }
    private fun capture(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun command(value: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(value)).use { it.readBytes() }
        command("mkdir -p /sdcard/beta-dashboard-screenshots")
        command("screencap -p /sdcard/beta-dashboard-screenshots/$name.png")
        assertTrue(command("cat /sdcard/beta-dashboard-screenshots/$name.png").size > 8)
        val tags = compose.onRoot(useUnmergedTree = true).printToString()
        val metadata = "api=${Build.VERSION.SDK_INT}\nhardware=${Build.HARDWARE}\n" + context.resources.configuration.toString() + "\n" + tags
        val encoded = android.util.Base64.encodeToString(metadata.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        command("echo '$encoded' | base64 -d > /sdcard/beta-dashboard-screenshots/$name.txt")
        assertTrue(command("cat /sdcard/beta-dashboard-screenshots/$name.txt").isNotEmpty())
    }
}
