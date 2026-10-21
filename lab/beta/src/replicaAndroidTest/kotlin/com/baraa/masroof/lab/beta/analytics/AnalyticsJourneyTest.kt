package com.baraa.masroof.lab.beta.analytics

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.replica.*
import com.baraa.masroof.lab.replica.application.AppContainer
import com.baraa.masroof.lab.replica.application.analytics.*
import com.baraa.masroof.lab.replica.data.room.entity.*
import com.baraa.masroof.lab.replica.presentation.analytics.*
import com.baraa.masroof.lab.replica.presentation.common.*
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
    @Test fun forecastsKeepLedgerParityAcrossCorrectionsAndReopenAndUiDraftsOnlySaveExplicitly() {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand("pm grant ${host.packageName} android.permission.READ_SMS")).use { it.readBytes() }
        runBlocking { LabResetWorkflow.reset(host) }
        val container = LabRuntime.container(host)
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
                assertEquals(workflow.state.value.snapshot!!.total, workflow.state.value.snapshot!!.chart().sumOf { it.amount }.movePointRight(2).longValueExact())
                val rows = container.financialTransactionRepository.listAll()
                val changed = rows.first { it.id == "cash" }
                val priorHash = workflow.state.value.snapshot!!.sourceHash
                container.financialTransactionRepository.update(changed.copy(amount = com.baraa.masroof.lab.replica.core.money.Money.of(java.math.BigDecimal("70.00"), changed.amount.currency)))
                workflow.request()
                withTimeout(90_000) { while (workflow.state.value.updating || workflow.state.value.snapshot?.sourceHash == priorHash) delay(100) }
                assertEquals(expected + 2000, workflow.state.value.snapshot!!.actual)
                val afterCorrection = workflow.state.value.snapshot!!.sourceHash
                container.database.openHelper.writableDatabase.execSQL("DELETE FROM financial_transaction WHERE id='cash'")
                workflow.request()
                withTimeout(90_000) { while (workflow.state.value.updating || workflow.state.value.snapshot?.sourceHash == afterCorrection) delay(100) }
                assertEquals(expected - 5000, workflow.state.value.snapshot!!.actual)
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
                val about = text(R.string.lab_replica_guidance_about, language).format(text(R.string.lab_analysis_initial, language))
                compose.onNodeWithContentDescription(about).performClick()
                compose.onNodeWithTag("guidance-popup").assertIsDisplayed()
                compose.onNodeWithText(text(R.string.lab_analysis_forecast_hint, language)).assertIsDisplayed()
                screen("$language-${if (isDark) "dark" else "light"}-forecast-help")
                androidx.test.espresso.Espresso.pressBack()
                compose.onNodeWithTag("guidance-popup").assertDoesNotExist()
                compose.onNodeWithText(text(R.string.lab_analysis_title, language)).assertIsDisplayed()
                compose.onNodeWithText(text(R.string.lab_analysis_show_data, language)).performScrollTo().performClick()
                compose.onNodeWithText(text(R.string.lab_analysis_scope_title, language)).performScrollTo().assertIsDisplayed()
                screen("$language-${if (isDark) "dark" else "light"}-data-details")
                compose.onNodeWithText(text(R.string.lab_analysis_hide_data, language)).performScrollTo().performClick()
                compose.onNodeWithText(text(R.string.lab_analysis_scope_title, language)).assertDoesNotExist()
                compose.onNodeWithText(text(R.string.lab_analysis_settings, language)).performClick()
                screen("$language-${if (isDark) "dark" else "light"}-settings")
                compose.onNodeWithText(text(R.string.lab_analysis_cancel, language)).performScrollTo().performClick()
                compose.onNodeWithText(text(R.string.lab_analysis_commitments, language), useUnmergedTree = true).performClick()
                screen("$language-${if (isDark) "dark" else "light"}-commitments")
                compose.onNodeWithText(text(R.string.lab_analysis_alerts, language), useUnmergedTree = true).performClick()
                screen("$language-${if (isDark) "dark" else "light"}-alerts")
            }
            compose.runOnUiThread { locale = "en"; dark = false }
            compose.onNodeWithText(text(R.string.lab_analysis_settings, "en")).performClick()
            val budget = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("lab-analysis-budget")))
            budget.performTextReplacement("1200")
            compose.onNodeWithText(text(R.string.lab_analysis_cancel, "en")).performScrollTo().performClick()
            assertNull(workflow.state.value.preferences.budget)
            compose.onNodeWithText(text(R.string.lab_analysis_settings, "en")).performClick()
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
            compose.onNodeWithText(text(R.string.lab_replica_settings_commitment_save, "en")).performScrollTo().performClick()
            compose.waitUntil(30_000) { runBlocking { container.commitmentRepository.listAll().size == originalCommitments.size + 1 } }
            runBlocking {
                // Repeating a Save after a crash cannot insert a second financial commitment.
                workflow.acceptCandidate(candidate.key, workflow.editorDraft(candidate))
                assertEquals(originalCommitments.size + 1, container.commitmentRepository.listAll().size)
                val archive = LabAnalyticsBackup.capture(LabStorageContext(host))!!
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
                    compose.onNodeWithText(text(R.string.lab_analysis_purchases, "en")).assertIsSelected()
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
            LabRuntime.close()
            runBlocking { assertTrue(detached.service.loadOverview(period).spendingTrends!!.purchases.points.isNotEmpty()) }
            val reopened = LabRuntime.container(host)
            runBlocking {
                reopened.awaitStartupMaintenance(); reopened.awaitPostStartupBackgroundWorkForTesting()
                assertEquals(beforeReopen, reopened.financialTransactionRepository.listAll())
                reopened.labAnalytics.refresh()
                assertEquals(120000L, reopened.labAnalytics.state.value.preferences.budget)
                assertTrue(reopened.labAnalytics.state.value.history.isNotEmpty())
            }
        } finally {
            compose.runOnUiThread { showing = false }; compose.waitForIdle()
            runBlocking { LabResetWorkflow.reset(host) }
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
        val bitmap = if (compose.onAllNodesWithTag("guidance-popup").fetchSemanticsNodes().isNotEmpty()) {
            instrument.waitForIdleSync()
            // The popup has its own Android window; wait for its first rendered frame.
            android.os.SystemClock.sleep(200)
            requireNotNull(instrument.uiAutomation.takeScreenshot())
        } else compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
