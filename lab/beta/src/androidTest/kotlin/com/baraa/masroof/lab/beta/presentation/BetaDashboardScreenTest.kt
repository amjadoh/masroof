package com.baraa.masroof.lab.beta.presentation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.BetaDashboardWorkflow
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.dashboard.producer.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale

class BetaDashboardScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val at = Instant.parse("2026-10-17T10:00:00Z")
    private val clock = Clock.fixed(at, ZoneOffset.UTC)
    private val zone = ZoneId.of("Asia/Riyadh")
    private val bank = BankId("BANK_ALJAZIRA")
    private val account = OwnedAsset(AssetId(Environment.BETA, bank, "synthetic-account"), AssetKind.ACCOUNT, "1234")
    private lateinit var db: BetaDatabase
    private val statements = CopyOnWriteArrayList<String>()

    @Before fun seed() = runBlocking {
        BetaContainer.resetOffline(context)
        db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME)
            .setQueryCallback({ sql, _ -> statements += sql }, Executor { it.run() }).build()
        BetaAssetRegistry(db).register(account)
        val input = FinancialClassificationInput(PostingKey(Environment.BETA, "synthetic-ui"), PostingRevision(0, 0),
            FinancialType.EXPENSE, Money.of("42.35", Currency.SAR), TransactionTime(at, zone),
            FinancialEndpoint.Owned(account.id), FinancialEndpoint.External, mapOf(account.id to account), setOf("synthetic-evidence"), true)
        assertTrue(RoomBetaFinancialLedger(db).post(input) is FinancialPostingResult.Applied)
        statements.clear()
    }
    @After fun cleanup() { db.close(); BetaContainer.resetOffline(context) }
    private fun workflow(fail: AtomicBoolean? = null) = BetaDashboardWorkflow({ query, periods ->
        withContext(Dispatchers.IO) {
            if (fail?.get() == true) error("synthetic read error")
            BetaDashboardProducer(RoomBetaDashboardSource(db, periods, clock = clock)).readWithCoverage(query)
        }
    }, clock)
    private fun waitLoaded() { compose.waitUntil(15000) { compose.onAllNodesWithTag("dash-refresh").fetchSemanticsNodes().isNotEmpty() } }
    private fun route(value: String) { compose.onNodeWithTag("dash-route-$value").performScrollTo().performClick() }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        shell("mkdir -p /sdcard/beta-dashboard-screenshots")
        shell("screencap -p /sdcard/beta-dashboard-screenshots/$name.png")
        assertEquals("OK", shell("test -s /sdcard/beta-dashboard-screenshots/$name.png && echo OK").trim())
    }

    @Test fun realRoomReadShowsUnavailableBalanceAndNeverWritesWhenNavigating() {
        val workflow = workflow()
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BetaDashboardPanel(workflow) } } }
        waitLoaded()
        compose.onAllNodesWithText("42.35 SAR").assertCountEquals(1)
        route("ACCOUNTS")
        compose.onNodeWithTag("dash-asset-BANK_ALJAZIRA-synthetic-account").performScrollTo().performClick()
        compose.onNodeWithTag("dash-detail-back").assertExists()
        compose.onAllNodesWithText(context.getString(com.baraa.masroof.lab.beta.R.string.beta_dash_unavailable)).fetchSemanticsNodes().also { assertTrue(it.isNotEmpty()) }
        compose.onNodeWithTag("dash-detail-back").performScrollTo().performClick()
        route("CARDS"); compose.onNodeWithTag("dash-empty").assertExists()
        route("LOANS"); compose.onNodeWithTag("dash-empty").assertExists()
        route("COMMITMENTS"); compose.onNodeWithTag("dash-unavailable").assertExists()
        assertTrue(statements.none { sql -> sql.contains("beta_") && listOf("INSERT", "UPDATE", "DELETE").any { sql.trimStart().startsWith(it, true) } })
        runBlocking { assertEquals(1, RoomBetaFinancialLedger(db).history(PostingKey(Environment.BETA, "synthetic-ui")).size) }
    }

    @Test fun rtlSavedMonthRouteAndQualifiedSelectionSurviveStateRestore() {
        val state = StateRestorationTester(compose)
        val workflow = workflow()
        val arabic = Configuration(context.resources.configuration).apply { setLocale(Locale("ar")); setLayoutDirection(Locale("ar")) }
        val arabicContext = context.createConfigurationContext(arabic)
        state.setContent { MaterialTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalContext provides arabicContext, LocalConfiguration provides arabic) {
            Column(Modifier.verticalScroll(rememberScrollState())) { BetaDashboardPanel(workflow) }
        } } }
        waitLoaded(); screenshot("rtl-overview")
        route("ACCOUNTS")
        compose.onNodeWithTag("dash-asset-BANK_ALJAZIRA-synthetic-account").performScrollTo().performClick()
        compose.onNodeWithTag("dash-detail-back").performScrollTo(); screenshot("rtl-account-unknown")
        state.emulateSavedInstanceStateRestore(); waitLoaded()
        compose.onNodeWithTag("dash-detail-back").assertExists()
        compose.onNodeWithTag("dash-month").assertTextEquals("2026-10")
        compose.onNodeWithTag("dash-detail-back").performScrollTo().performClick()
        compose.onNodeWithTag("dash-route-ACCOUNTS").assertIsSelected()
        compose.onNodeWithTag("dash-month-previous").performScrollTo().performClick(); waitLoaded()
        state.emulateSavedInstanceStateRestore(); waitLoaded()
        compose.onNodeWithTag("dash-month").assertTextEquals("2026-09")
        compose.onNodeWithTag("dash-route-ACCOUNTS").assertIsSelected()
    }

    @Test fun failedNewPeriodClearsOldMoneyAndExplicitRetryRecovers() {
        val fail = AtomicBoolean(false)
        val workflow = workflow(fail)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BetaDashboardPanel(workflow) } } }
        waitLoaded(); compose.onAllNodesWithText("42.35 SAR").assertCountEquals(1)
        fail.set(true)
        compose.onNodeWithTag("dash-month-previous").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("dash-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dash-error").performScrollTo(); screenshot("period-error")
        compose.onAllNodesWithText("42.35 SAR").assertCountEquals(0)
        fail.set(false); compose.onNodeWithTag("dash-retry").performScrollTo().performClick(); waitLoaded()
        compose.onNodeWithTag("dash-month").assertTextEquals("2026-09")
        compose.onAllNodesWithText("42.35 SAR").assertCountEquals(0)
        compose.onNodeWithTag("dash-month-next").performScrollTo().performClick(); waitLoaded()
        compose.onAllNodesWithText("42.35 SAR").assertCountEquals(1)
    }

    @Test fun removedQualifiedSelectionDoesNotOpenSameLocalIdAtAnotherBank() = runBlocking {
        val other = account.copy(id = account.id.copy(bank = BankId("SYNTHETIC_OTHER_BANK")))
        BetaAssetRegistry(db).register(other)
        val workflow = workflow()
        val state = StateRestorationTester(compose)
        state.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BetaDashboardPanel(workflow) } } }
        waitLoaded(); route("ACCOUNTS")
        compose.onNodeWithTag("dash-asset-SYNTHETIC_OTHER_BANK-synthetic-account").performScrollTo().performClick()
        db.openHelper.writableDatabase.execSQL("DELETE FROM beta_asset WHERE bankId='SYNTHETIC_OTHER_BANK'")
        state.emulateSavedInstanceStateRestore(); waitLoaded()
        compose.onNodeWithTag("dash-missing").assertExists()
        compose.onNodeWithTag("dash-detail-back").performScrollTo().performClick()
        compose.onNodeWithTag("dash-asset-BANK_ALJAZIRA-synthetic-account").assertExists()
        Unit
    }
}
