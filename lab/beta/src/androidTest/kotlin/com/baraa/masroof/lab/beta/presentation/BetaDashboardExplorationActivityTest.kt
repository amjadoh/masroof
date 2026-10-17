package com.baraa.masroof.lab.beta.presentation

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.dashboard.TrendScope
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BetaDashboardExplorationActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val key = PostingKey(Environment.BETA, "synthetic-full-activity")
    private fun waitLoaded() { compose.waitUntil(20000) { compose.onAllNodesWithTag("dash-refresh").fetchSemanticsNodes().isNotEmpty() } }
    private fun route(name: String) { compose.onNodeWithTag("dash-route-$name").performScrollTo().performClick() }
    private fun closed() { val latch = CountDownLatch(1); BetaIo.execute { latch.countDown() }; assertTrue(latch.await(15, TimeUnit.SECONDS)) }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.onNodeWithTag("beta-badge").assertIsDisplayed(); compose.onNodeWithTag("beta-return").assertIsDisplayed()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun command(value: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(value)).use { it.readBytes() }
        command("mkdir -p /sdcard/beta-dashboard-screenshots")
        command("screencap -p /sdcard/beta-dashboard-screenshots/$name.png")
        val png = command("cat /sdcard/beta-dashboard-screenshots/$name.png")
        assertTrue(png.size > 8); assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), png.copyOfRange(0, 8))
    }
    @Test @SdkSuppress(minSdkVersion = 33)
    fun actualRtlActivityDrilldownAndChartPreferencesSurviveRecreationAndFreshLaunchWithoutPosting() = runBlocking {
        BetaContainer.resetOffline(context)
        val bank = BankId("BANK_ALJAZIRA")
        val account = OwnedAsset(AssetId(Environment.BETA, bank, "synthetic-full-account"), AssetKind.ACCOUNT, "4567")
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            beta.saveManualAsset(ManualAssetDraft(account.id, account.kind, checkNotNull(account.last4), ownedConfirmed = true))
        }
        val seeded = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        try {
            assertTrue(RoomBetaFinancialLedger(seeded).post(FinancialClassificationInput(key, PostingRevision(0, 0), FinancialType.EXPENSE,
                Money.of("42.35", Currency.SAR), TransactionTime(Instant.now().minusSeconds(60), ZoneId.of("Asia/Riyadh")),
                FinancialEndpoint.Owned(account.id), FinancialEndpoint.External, mapOf(account.id to account), setOf("synthetic-full-evidence"), true)) is FinancialPostingResult.Applied)
        } finally { seeded.close() }
        val locales = context.getSystemService(LocaleManager::class.java)
        val original = locales.applicationLocales
        try {
            locales.applicationLocales = LocaleList.forLanguageTags("ar")
            ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
                waitLoaded(); compose.onNodeWithText("نظرة عامة").assertExists(); screenshot("activity-rtl-overview")
                route("TRANSACTIONS")
                compose.onNodeWithTag("dash-transaction-${key.transactionId}").performScrollTo().performClick()
                compose.onNodeWithTag("transaction-detail").assertExists(); compose.onNodeWithText("42.35 SAR").assertExists()
                screenshot("activity-rtl-transaction-detail")
                scenario.recreate(); waitLoaded(); compose.onNodeWithTag("transaction-detail").assertExists()
                compose.onNodeWithTag("dash-detail-back").performScrollTo().performClick()
                route("ANALYSIS"); compose.onNodeWithTag("dash-customize").performScrollTo().performClick()
                compose.onNodeWithTag("chart-scope-FINANCIAL_OUTFLOWS").performScrollTo().performClick()
                compose.onNodeWithTag("chart-category-FEES").performScrollTo().performClick()
                compose.onNodeWithTag("chart-table").performScrollTo().performClick()
                compose.onNodeWithTag("dash-sheet-cancel").performClick()
                assertEquals(BetaDashboardViewPreferences(), BetaDashboardViewPreferencesWorkflow(context).read())
                compose.onNodeWithTag("dash-customize").performScrollTo().performClick()
                compose.onNodeWithTag("chart-scope-FINANCIAL_OUTFLOWS").performScrollTo().performClick()
                compose.onNodeWithTag("chart-category-FEES").performScrollTo().performClick()
                compose.onNodeWithTag("chart-table").performScrollTo().performClick()
                compose.onNodeWithTag("dash-sheet-apply").performClick()
                compose.waitUntil { compose.onAllNodesWithTag("dash-sheet").fetchSemanticsNodes().isEmpty() }
                val chosen = BetaDashboardViewPreferencesWorkflow(context).read()
                assertEquals(TrendScope.FINANCIAL_OUTFLOWS, chosen.choice.scope); assertFalse(chosen.bars)
                route("OVERVIEW"); compose.onNodeWithText("42.35 SAR").assertExists()
            }
            closed()
            ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
                waitLoaded(); route("ANALYSIS"); compose.onNodeWithTag("dash-customize").performScrollTo().performClick()
                compose.onNodeWithTag("chart-scope-FINANCIAL_OUTFLOWS").assertIsSelected()
                compose.onNodeWithTag("chart-category-FEES").performScrollTo().assertIsSelected()
                compose.onNodeWithTag("chart-table").performScrollTo().assertIsSelected()
                compose.onNodeWithTag("dash-sheet-cancel").performClick()
                route("CONFIGURATION"); compose.onNodeWithTag("dash-configuration").assertExists()
                screenshot("activity-rtl-configuration")
                scenario.recreate(); waitLoaded(); compose.onNodeWithTag("dash-route-CONFIGURATION").assertIsSelected()
            }
            closed()
            val verified = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
            try { assertEquals(1, RoomBetaFinancialLedger(verified).history(key).size) } finally { verified.close() }
        } finally { locales.applicationLocales = original; closed(); BetaContainer.resetOffline(context) }
    }
}
