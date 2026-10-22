package com.baraa.masroof.lab.beta.analytics

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.R
import com.baraa.masroof.testsupport.*
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.analytics.*
import com.baraa.masroof.data.room.entity.*
import com.baraa.masroof.presentation.analytics.*
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.ui.theme.MasroofTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AnalyticsJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val host = instrument.targetContext
    @Test fun publishedComparisonShowsItsActualLateOriginAndContextualHelpInBothLanguages() {
        val start=LocalDate.of(2026,10,1).toEpochDay()
        val origin=start+10
        val pair=com.baraa.masroof.data.analytics.ForecastComparisonRow("late",start,start+30,
            LocalDate.ofEpochDay(origin).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            "a".repeat(64),com.baraa.masroof.lab.analytics.AnalyticsVersion.MODEL,
            ForecastPerformancePolicy.VERSION,20000,21000,originDay=origin)
        var language by mutableStateOf("ar")
        var dark by mutableStateOf(false)
        compose.setContent {
            val config=Configuration(host.resources.configuration).apply { setLocale(Locale(language));fontScale=1.3f }
            CompositionLocalProvider(LocalContext provides host.createConfigurationContext(config),LocalConfiguration provides config) {
                MasroofTheme(darkTheme=dark) { MasroofScreenBackground {
                    Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) {
                        LearningAndForecastPerformance(AnalysisUiState(forecastComparisons=listOf(pair)))
                    }
                } }
            }
        }
        for(locale in listOf("ar","en")) for(isDark in listOf(false,true)) {
            compose.runOnUiThread { language=locale;dark=isDark }
            val context=host.createConfigurationContext(Configuration(host.resources.configuration).apply { setLocale(Locale(locale)) })
            val date=java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy",Locale(locale)).format(LocalDate.ofEpochDay(origin))
            val label=com.baraa.masroof.application.locale.AppDigits.display(context.getString(R.string.learning_forecast_origin,date,20))
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(text(R.string.learning_forecast_hint,locale)).assertDoesNotExist()
            val about=context.getString(R.string.guidance_about,context.getString(R.string.learning_forecast_title))
            compose.onNodeWithContentDescription(about).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.learning_forecast_hint,locale)).assertIsDisplayed()
            screen("$locale-${if(isDark) "dark" else "light"}-late-origin-help")
            instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("guidance-popup").assertDoesNotExist()
        }
    }
    @Test fun forecastsKeepLedgerParityAcrossCorrectionsAndReopenAndUiDraftsOnlySaveExplicitly() {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("pm grant ${host.packageName} android.permission.READ_SMS")).use { it.readBytes() }
        runBlocking { TestRuntime.reset(host) }
        val container = TestRuntime.container(host)
        var showing by mutableStateOf(true)
        try {
            runBlocking { container.awaitStartupMaintenance(); container.awaitPostStartupBackgroundWorkForTesting(); seed(container) }
            val workflow = container.labAnalytics
            runBlocking { workflow.refresh() }
            compose.waitUntil(90_000) { !workflow.state.value.updating && workflow.state.value.snapshot?.status == "READY" }
            runBlocking {
                val overview = container.dashboardService.loadOverview(container.dashboardPeriodWorkflow.currentPeriod())
                val expected = overview.spendingTrends!!.purchases.totalSpending.amount.movePointRight(2).longValueExact()
                assertEquals("Purchase scope exactly matches the current graph", expected, workflow.state.value.snapshot!!.actual)
                val snapshot = workflow.state.value.snapshot!!
                assertEquals(snapshot.actual, snapshot.insights!!.components.sumOf { it.amount })
                assertNotNull(snapshot.insights!!.comparison)
                assertTrue(snapshot.insights!!.merchants.isNotEmpty())
                assertEquals(com.baraa.masroof.lab.analytics.AnalyticsVersion.EVALUATION, snapshot.evaluationVersion)
                for (past in workflow.state.value.purchasePeriods.filter { it.actual != null }) {
                    val period = com.baraa.masroof.domain.period.FinancialPeriod(LocalDate.ofEpochDay(past.start), LocalDate.ofEpochDay(past.end))
                    val actual = container.dashboardService.loadOverview(period).spendingTrends!!.purchases.totalSpending.amount.movePointRight(2).longValueExact()
                    assertEquals("Historical periods use the existing purchases scope", actual, past.actual)
                }
                assertEquals(workflow.state.value.snapshot!!.total, workflow.state.value.snapshot!!.chart().sumOf { it.amount }.movePointRight(2).longValueExact())
                val rows = container.financialTransactionRepository.listAll()
                val changed = rows.first { it.id == "cash" }
                val priorHash = workflow.state.value.snapshot!!.sourceHash
                container.financialTransactionRepository.update(changed.copy(amount = com.baraa.masroof.core.money.Money.of(java.math.BigDecimal("70.00"), changed.amount.currency)))
                workflow.request()
                withTimeout(90_000) { while (workflow.state.value.updating || workflow.state.value.snapshot?.sourceHash == priorHash) delay(100) }
                assertEquals(expected + 2000, workflow.state.value.snapshot!!.actual)
                assertEquals(expected + 2000, workflow.state.value.snapshot!!.insights!!.components.sumOf { it.amount })
                val afterCorrection = workflow.state.value.snapshot!!.sourceHash
                container.database.openHelper.writableDatabase.execSQL("DELETE FROM financial_transaction WHERE id='cash'")
                workflow.request()
                withTimeout(90_000) { while (workflow.state.value.updating || workflow.state.value.snapshot?.sourceHash == afterCorrection) delay(100) }
                assertEquals(expected - 5000, workflow.state.value.snapshot!!.actual)
                assertEquals(expected - 5000, workflow.state.value.snapshot!!.insights!!.components.sumOf { it.amount })
                assertEquals(rows.size - 1, container.financialTransactionRepository.listAll().size)
            }
            var locale by mutableStateOf("ar")
            var dark by mutableStateOf(false)
            var open by mutableStateOf(false)
            compose.setContent { if (showing) {
                val registryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
                val config = Configuration(host.resources.configuration).apply { setLocale(Locale(locale)); fontScale = if (locale == "en") 1.3f else 1f }
                val context = host.createConfigurationContext(config)
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config, LocalLabAnalyticsWorkflow provides workflow,
                    LocalActivityResultRegistryOwner provides registryOwner) {
                    MasroofTheme(darkTheme = dark) { MasroofScreenBackground {
                        if (open) LabAnalyticsRoute(onBack = { open = false }, onOpenTransaction = {}) else LabForecastCard { open = true }
                    } }
                }
            } }
            for (language in listOf("ar", "en")) for (isDark in listOf(false, true)) {
                compose.runOnUiThread { locale = language; dark = isDark; open = false }
                compose.onNodeWithTag("lab-forecast-entry").assertIsDisplayed().performClick()
                compose.onNodeWithText(text(R.string.lab_analysis_title, language)).assertIsDisplayed()
                screen("$language-${if (isDark) "dark" else "light"}-purchases")
                compose.onNodeWithText(text(R.string.lab_analysis_need_data, language)).assertDoesNotExist()
                val about = text(R.string.guidance_about, language).format(text(R.string.lab_analysis_initial, language))
                compose.onNodeWithContentDescription(about).performClick()
                compose.onNodeWithTag("guidance-popup").assertIsDisplayed()
                compose.onNodeWithText(text(R.string.lab_analysis_forecast_hint, language)).assertIsDisplayed()
                screen("$language-${if (isDark) "dark" else "light"}-forecast-help")
                compose.waitForIdle(); instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); compose.waitForIdle()
                compose.onNodeWithTag("guidance-popup").assertDoesNotExist()
                compose.onNodeWithText(text(R.string.lab_analysis_title, language)).assertIsDisplayed()
                compose.onNodeWithTag("analysis-spending-explanation").performScrollTo().assertIsDisplayed()
                screen("$language-${if (isDark) "dark" else "light"}-spending-explanation")
                compose.onNodeWithText(text(R.string.lab_analysis_show_data, language)).performScrollTo().performClick()
                compose.onNodeWithText(text(R.string.lab_analysis_scope_title, language)).performScrollTo().assertIsDisplayed()
                screen("$language-${if (isDark) "dark" else "light"}-data-details")
                compose.onNodeWithTag("learning-insights").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(text(R.string.learning_statistical_paused,language)).assertIsDisplayed()
                screen("$language-${if (isDark) "dark" else "light"}-learning-insights")
                compose.onNodeWithTag("forecast-performance").performScrollTo().assertIsDisplayed()
                screen("$language-${if (isDark) "dark" else "light"}-forecast-performance")
                compose.onNodeWithText(text(R.string.lab_analysis_hide_data, language)).performScrollTo().performClick()
                compose.onNodeWithText(text(R.string.lab_analysis_scope_title, language)).assertDoesNotExist()
                compose.onNodeWithTag("lab-analysis-settings").performClick()
                screen("$language-${if (isDark) "dark" else "light"}-settings")
                compose.onNodeWithText(text(R.string.lab_analysis_cancel, language)).performScrollTo().performClick()
                compose.onNodeWithText(text(R.string.lab_analysis_commitments, language), useUnmergedTree = true).performClick()
                screen("$language-${if (isDark) "dark" else "light"}-commitments")
                compose.onNodeWithText(text(R.string.lab_analysis_alerts, language), useUnmergedTree = true).assertDoesNotExist()
                compose.onNodeWithText(text(R.string.lab_analysis_spending_tab, language), useUnmergedTree = true).performClick()
                compose.onNodeWithTag("lab-history-3").performScrollTo().performClick()
                compose.onNodeWithText(text(R.string.lab_analysis_history_purchases, language)).performScrollTo().assertIsDisplayed()
                screen("$language-${if (isDark) "dark" else "light"}-history")
                compose.onNodeWithTag("lab-history-0").performScrollTo().performClick()
            }
            compose.runOnUiThread { locale = "en"; dark = false }
            compose.onNodeWithTag("lab-analysis-settings").performClick()
            val budget = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("lab-analysis-budget")))
            budget.performTextReplacement("1200")
            compose.onNodeWithText(text(R.string.lab_analysis_cancel, "en")).performScrollTo().performClick()
            assertNull(workflow.state.value.preferences.budget)
            compose.onNodeWithTag("lab-analysis-settings").performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("lab-analysis-budget"))).performTextReplacement("1200")
            compose.onNodeWithText(text(R.string.lab_analysis_save, "en")).performScrollTo().performClick()
            compose.waitUntil(30_000) { !workflow.state.value.updating && workflow.state.value.preferences.budget == 120000L }
            val candidate = workflow.state.value.snapshot!!.candidates.first()
            val originalCommitments = runBlocking { container.commitmentRepository.listAll() }
            compose.onNodeWithText(text(R.string.lab_analysis_commitments, "en"), useUnmergedTree = true).performClick()
            compose.onNodeWithText(text(R.string.lab_analysis_review_candidate, "en")).performScrollTo().performClick()
            screen("en-light-commitment-editor")
            compose.onNodeWithContentDescription(text(R.string.lab_analysis_back, "en")).performClick()
            assertEquals(originalCommitments, runBlocking { container.commitmentRepository.listAll() })
            compose.onNodeWithText(text(R.string.lab_analysis_review_candidate, "en")).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.settings_commitment_save, "en")).performScrollTo().performClick()
            compose.waitUntil(30_000) { runBlocking { container.commitmentRepository.listAll().size == originalCommitments.size + 1 } }
            runBlocking {
                // Repeating a Save after a crash cannot insert a second financial commitment.
                workflow.acceptCandidate(candidate.key, workflow.editorDraft(candidate))
                assertEquals(originalCommitments.size + 1, container.commitmentRepository.listAll().size)
                val archive = LabAnalyticsBackup.capture(TestStorageContext(host))!!
                LabAnalyticsBackup.validate(archive)
                val ledger = container.financialTransactionRepository.listAll()
                assertEquals(ledger.size, container.database.financialTransactionDao().listAll().size)
                container.database.openHelper.readableDatabase.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(18, it.getInt(0)) }
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, host.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS))
                    workflow.updatePreferences(workflow.state.value.preferences.copy(budget = 1, phone = true))
                    workflow.refresh()
                    withTimeout(30_000) { while (workflow.state.value.updating || workflow.state.value.preferences.budget != 1L) delay(100) }
                    assertTrue(workflow.state.value.alerts.any { it.kind == "ACTUAL" })
                    assertTrue(host.getSystemService(android.app.NotificationManager::class.java).activeNotifications.none { it.tag == "lab-analysis" })
                    val alertId = workflow.state.value.alerts.first { it.kind == "ACTUAL" }.id
                    compose.runOnUiThread { AnalyticsNavigation.open(alertId) }
                    compose.waitForIdle()
                    compose.onNodeWithText(text(R.string.lab_analysis_spending_tab, "en")).assertIsSelected()
                    workflow.updatePreferences(workflow.state.value.preferences.copy(budget = 120000, phone = false))
                    workflow.refresh()
                    withTimeout(30_000) { while (workflow.state.value.updating || workflow.state.value.preferences.budget != 120000L) delay(100) }
                }
            }
            runBlocking {
                container.database.financialTransactionDao().insertTransactionIfAbsent(FinancialTransactionEntity(
                    "large-refund", "REFUND", "5000.00", "SAR", Instant.now().toEpochMilli(),
                    "card:BANK_ALJAZIRA:1234", null, "استرداد اختبار", null, null))
                workflow.request(); workflow.refresh()
            }
            compose.waitUntil(30_000) { !workflow.state.value.updating && (workflow.state.value.snapshot?.actual ?: 0) < 0 }
            screen("en-light-negative-net")
            compose.runOnUiThread { showing = false }; compose.waitForIdle()
            val beforeReopen = runBlocking { container.financialTransactionRepository.listAll() }
            val period = container.dashboardPeriodWorkflow.currentPeriod()
            val detached = runBlocking { FinancialAnalysisReader.capture(container, LocalDate.now().minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant(), period.endDateExclusive.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()) }
            TestRuntime.close()
            runBlocking { assertTrue(detached.service.loadOverview(period).spendingTrends!!.purchases.points.isNotEmpty()) }
            val reopened = TestRuntime.container(host)
            runBlocking {
                reopened.awaitStartupMaintenance(); reopened.awaitPostStartupBackgroundWorkForTesting()
                assertEquals(beforeReopen, reopened.financialTransactionRepository.listAll())
                reopened.labAnalytics.refresh()
                assertEquals(120000L, reopened.labAnalytics.state.value.preferences.budget)
                assertTrue(reopened.labAnalytics.state.value.history.isNotEmpty())
            }
        } finally {
            compose.runOnUiThread { showing = false }; compose.waitForIdle()
            runBlocking { TestRuntime.reset(host) }
        }
    }
    @Test fun usabilityShowsActualRowsClearSettingsAndAlertsInTheExistingCenter() {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("pm grant ${host.packageName} android.permission.READ_SMS")).use { it.readBytes() }
        runBlocking { TestRuntime.reset(host) }
        val c = TestRuntime.container(host)
        var showing by mutableStateOf(true)
        var center by mutableStateOf(false)
        var language by mutableStateOf("en")
        var dark by mutableStateOf(false)
        var openedTransaction: String? = null
        try {
            runBlocking { c.awaitStartupMaintenance(); c.awaitPostStartupBackgroundWorkForTesting(); seed(c); c.labAnalytics.refresh() }
            val workflow = c.labAnalytics
            compose.waitUntil(90_000) { !workflow.state.value.updating && workflow.state.value.snapshot != null }
            compose.setContent { if (showing) {
                val registryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
                val config = Configuration(host.resources.configuration).apply { setLocale(Locale(language)); fontScale = if (language == "en") 1.3f else 1f }
                CompositionLocalProvider(LocalContext provides host.createConfigurationContext(config), LocalConfiguration provides config, LocalLabAnalyticsWorkflow provides workflow, LocalActivityResultRegistryOwner provides registryOwner) {
                    MasroofTheme(darkTheme = dark) { MasroofScreenBackground {
                        if (center) {
                            val target by AnalyticsNavigation.target.collectAsState()
                            LaunchedEffect(target) { if (target != null) center = false }
                            com.baraa.masroof.presentation.notification.NotificationCenterScreen(
                                com.baraa.masroof.presentation.notification.NotificationCenterUiState(loading = false,
                                    items = listOf(com.baraa.masroof.application.notification.NotificationItem("review_required",
                                        com.baraa.masroof.application.notification.NotificationType.REVIEW_REQUIRED, count = 2,
                                        action = com.baraa.masroof.application.notification.NotificationAction.OPEN_REVIEW)), unreadCount = 1), {}, {})
                        } else LabAnalyticsRoute({}, { openedTransaction = it })
                    } }
                }
            } }
            compose.onNodeWithText(text(R.string.lab_analysis_show_data, "en")).performScrollTo().performClick()
            val reason = text(R.string.lab_analysis_excluded_scope, "en")
            compose.onNode(hasText(reason, substring = true) and hasClickAction()).performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("lab-evidence-settlement").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("lab-evidence-settlement").performScrollTo().assertIsDisplayed().performClick()
            assertEquals("settlement", openedTransaction)
            screen("en-light-quality-transactions")
            compose.onNodeWithText(text(R.string.lab_analysis_payment_number, "en").format(1)).assertDoesNotExist()
            compose.onNodeWithTag("lab-analysis-settings").performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("lab-analysis-budget"))).performTextReplacement("invalid")
            compose.onNodeWithText(text(R.string.lab_analysis_save, "en")).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.lab_analysis_budget_invalid, "en")).performScrollTo().assertIsDisplayed()
            assertNull(workflow.state.value.preferences.budget)
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("lab-analysis-budget"))).performScrollTo().performTextReplacement("١٢٠٠٫٥٠")
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("lab-analysis-reminder"))).performScrollTo().performTextReplacement("٣")
            compose.onNodeWithText(text(R.string.lab_analysis_save, "en")).performScrollTo().performClick()
            compose.waitUntil(30_000) { !workflow.state.value.updating && workflow.state.value.preferences.budget == 120050L }
            runBlocking { workflow.updatePreferences(workflow.state.value.preferences.copy(budget = 1)); workflow.refresh() }
            compose.waitUntil(30_000) { !workflow.state.value.updating && workflow.state.value.alerts.any { it.kind == "ACTUAL" } }
            val alert = workflow.state.value.alerts.first { it.kind == "ACTUAL" }
            compose.runOnUiThread { center = true }
            for (locale in listOf("ar", "en")) for (isDark in listOf(false, true)) {
                compose.runOnUiThread { language = locale; dark = isDark }
                compose.onNodeWithTag("lab-alert-${alert.id}").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(text(R.string.settings_hub_review_title, locale)).assertExists()
                screen("$locale-${if (isDark) "dark" else "light"}-unified-alerts")
            }
            compose.runOnUiThread { language = "en"; dark = false }
            compose.onNodeWithTag("lab-alert-${alert.id}").performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.lab_analysis_spending_tab, "en")).assertIsSelected()
            compose.waitUntil(30_000) { workflow.state.value.alerts.first { it.id == alert.id }.read }
            compose.runOnUiThread { center = true }
            compose.onNode(hasText(text(R.string.lab_analysis_actions, "en")) and hasAnyAncestor(hasTestTag("lab-alert-container-${alert.id}"))).performClick()
            compose.onNodeWithText(text(R.string.lab_analysis_snooze, "en")).performClick()
            compose.waitUntil(30_000) { workflow.state.value.alerts.first { it.id == alert.id }.snoozedUntil > 0 }
            compose.onNodeWithTag("lab-alert-${alert.id}").assertDoesNotExist()
            compose.onNodeWithText(text(R.string.lab_analysis_settings, "en")).performClick()
            compose.onNodeWithText(text(R.string.lab_analysis_budget_section, "en")).assertIsDisplayed()
        } finally {
            compose.runOnUiThread { showing = false }; compose.waitForIdle()
            runBlocking { TestRuntime.reset(host) }
        }
    }
    @Test fun qualityActionsAndSpendingEvidenceAreReachableWithoutChangingFinancialData() {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("pm grant ${host.packageName} android.permission.READ_SMS")).use { it.readBytes() }
        runBlocking { TestRuntime.reset(host) }
        val c = TestRuntime.container(host)
        var showing by mutableStateOf(true)
        var insufficient by mutableStateOf(true)
        var opened: String? = null
        var review = false; var permission = false; var rescan = false
        try {
            runBlocking { c.awaitStartupMaintenance(); c.awaitPostStartupBackgroundWorkForTesting(); seed(c); c.labAnalytics.refresh() }
            val workflow = c.labAnalytics
            compose.waitUntil(30_000) { !workflow.state.value.updating && workflow.state.value.snapshot != null }
            val original = runBlocking { c.financialTransactionRepository.listAll() }
            val snapshot = requireNotNull(workflow.state.value.snapshot).let { it.copy(quality = it.quality.copy(
                pendingReviews = 1, blocked = true, reasons = listOf("SMS_PERMISSION", "PROCESSING_RETRY"))) }
            val config = Configuration(host.resources.configuration).apply { setLocale(Locale("en")) }
            compose.setContent { if (showing) {
                CompositionLocalProvider(LocalContext provides host.createConfigurationContext(config), LocalConfiguration provides config) {
                    MasroofTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                        AnalysisSpendingExplanation(snapshot, workflow, { opened = it })
                        val displayed = if (insufficient) snapshot.copy(status = "INSUFFICIENT_DATA", method = "NONE",
                            quality = snapshot.quality.copy(blocked = false, pendingReviews = 0, reasons = emptyList())) else snapshot
                        AnalysisDataQuality(displayed, workflow, { opened = it }, { review = true }, { permission = true }, { rescan = true })
                    } }
                }
            } }
            compose.onNodeWithText(text(R.string.lab_analysis_need_data, "en")).assertDoesNotExist()
            val aboutInsufficient = text(R.string.guidance_about, "en").format(text(R.string.lab_analysis_quality_insufficient, "en"))
            compose.onNodeWithContentDescription(aboutInsufficient).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.lab_analysis_need_data, "en")).assertIsDisplayed()
            instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            compose.onNodeWithText(text(R.string.lab_analysis_need_data, "en")).assertDoesNotExist()
            compose.runOnUiThread { insufficient = false }
            compose.onNodeWithTag("analysis-expand-insights").performScrollTo().performClick()
            compose.onNodeWithTag("analysis-component-PURCHASES").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("lab-evidence-today-spend").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("lab-evidence-today-spend").performScrollTo().performClick()
            assertEquals("today-spend", opened)
            compose.onNodeWithTag("analysis-expand-insights").performScrollTo().performClick()
            compose.onNodeWithTag("analysis-open-review").performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.lab_analysis_quality_permission_action, "en")).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.lab_analysis_quality_retry_action, "en")).performScrollTo().performClick()
            assertTrue(review); assertTrue(permission); assertTrue(rescan)
            assertEquals(original, runBlocking { c.financialTransactionRepository.listAll() })
            screen("en-light-quality-actions")
        } finally {
            compose.runOnUiThread { showing = false }; compose.waitForIdle()
            runBlocking { TestRuntime.reset(host) }
        }
    }
    private suspend fun seed(c: AppContainer) {
        val today = LocalDate.now()
        c.onboardingPreferencesRepository.setOnboardingStarted(true)
        c.onboardingPreferencesRepository.setOnboardingCompleted(true)
        c.onboardingPreferencesRepository.setHistoricalImportStartEpochMillis(today.minusDays(150).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        c.onboardingPreferencesRepository.setHistoricalImportCompleted(true)
        c.database.withTransaction {
            c.database.cardRegistryDao().insertFacilityIfAbsent("test-facility", "BANK_ALJAZIRA", "1234")
            c.database.cardRegistryDao().insertIfAbsent(CardRegistryEntity("test-card", "BANK_ALJAZIRA", "1234", "OWNED", cardType = "CREDIT", cardRole = "PRIMARY", creditFacilityId = "test-facility", firstSeenRawSmsId = null, lastSeenRawSmsId = null))
            fun entity(id: String, type: String, amount: String, date: LocalDate, merchant: String = "متجر الاختبار", currency: String = "SAR", rate: String? = null) =
                FinancialTransactionEntity(id, type, amount, currency, date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    "card:BANK_ALJAZIRA:1234", null, merchant, null, null, rate, if (rate != null) "SMS" else null)
            val dao = c.database.financialTransactionDao()
            for (ago in 1..150) dao.insertTransactionIfAbsent(entity("daily-$ago", "EXPENSE", "${20 + today.minusDays(ago.toLong()).dayOfWeek.value * 5}.00", today.minusDays(ago.toLong())))
            for (month in 0..3) dao.insertTransactionIfAbsent(entity("utility-$month", "BILL_PAYMENT", "${if (month == 0) 150 else 100}.00", today.minusMonths(month.toLong()).minusDays(2), "شركة الخدمات"))
            listOf(entity("cash", "CASH_WITHDRAWAL", "50.00", today.minusDays(1)), entity("external", "EXTERNAL_TRANSFER_OUT", "40.00", today.minusDays(1)),
                entity("refund", "REFUND", "20.00", today.minusDays(1)), entity("settlement", "CREDIT_CARD_PAYMENT", "500.00", today.minusDays(1)),
                entity("today-spend", "EXPENSE", "10.00", today), entity("today-refund", "REFUND", "20.00", today),
                entity("loan", "LOAN_REPAYMENT", "100.00", today.minusDays(1)), entity("self", "SELF_TRANSFER", "1000.00", today.minusDays(1)),
                entity("fx", "EXPENSE", "6.00", today.minusDays(1), currency = "USD", rate = "3.75"))
                .forEach { dao.insertTransactionIfAbsent(it) }
        }
        c.labAnalytics.request()
    }
    private fun text(id: Int, language: String): String = host.createConfigurationContext(Configuration(host.resources.configuration).apply { setLocale(Locale(language)) }).getString(id)
    private fun screen(name: String) {
        compose.waitForIdle()
        val directory = File(host.getExternalFilesDir(null), "analytics-screens").apply { mkdirs() }
        instrument.waitForIdleSync()
        // Capture the composed display without drawing AndroidComposeView on the test thread.
        android.os.SystemClock.sleep(200)
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
