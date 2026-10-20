package com.baraa.masroof.lab.beta.replica

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.replica.application.AppContainer
import com.baraa.masroof.lab.replica.domain.model.*
import com.baraa.masroof.lab.replica.presentation.onboarding.*
import com.baraa.masroof.lab.replica.presentation.dashboard.*
import com.baraa.masroof.lab.replica.presentation.review.*
import com.baraa.masroof.lab.replica.presentation.settings.*
import com.baraa.masroof.lab.replica.presentation.statement.*
import com.baraa.masroof.lab.replica.presentation.notification.*
import com.baraa.masroof.lab.replica.presentation.navigation.MasroofRoot
import com.baraa.masroof.ui.theme.MasroofTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real source UI + real ViewModels + scanner + Room; only the device inbox is a fixture. */
@RunWith(AndroidJUnit4::class)
class ReplicaSetupJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val host = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun setupProvidesFinishButtonThenDisplaysFinancialDataAndOriginalNavigation() {
        runBlocking { LabResetWorkflow.reset(host) }
        val container = AppContainer(LabStorageContext(host),smsDataSourceOverride = ReferenceHistory.source(host))
        container.appLocaleRepository.setLanguageTag("en")
        container.runStartupMaintenance()
        runBlocking { container.awaitStartupMaintenance() }
        val store = ViewModelStore()
        lateinit var onboarding: OnboardingViewModel
        lateinit var dashboard: DashboardViewModel
        lateinit var review: ReviewViewModel
        lateinit var settings: SettingsViewModel
        lateinit var statement: StatementReconciliationViewModel
        lateinit var notifications: NotificationCenterViewModel
        try {
            compose.runOnUiThread {
                onboarding = ViewModelProvider(store,OnboardingViewModelFactory(container,container.onboardingPreferencesRepository){true})[OnboardingViewModel::class.java]
                dashboard = ViewModelProvider(store,DashboardViewModelFactory(container,{true}))[DashboardViewModel::class.java]
                review = ViewModelProvider(store,ReviewViewModelFactory(container))[ReviewViewModel::class.java]
                settings = ViewModelProvider(store,SettingsViewModelFactory(container,"test",{true}))[SettingsViewModel::class.java]
                statement = ViewModelProvider(store,StatementReconciliationViewModelFactory(container.statementReconciliationWorkflow))[StatementReconciliationViewModel::class.java]
                notifications = ViewModelProvider(store,NotificationCenterViewModelFactory(container,{true}))[NotificationCenterViewModel::class.java]
            }
            compose.setContent { MasroofTheme {
                MasroofRoot(onboarding,dashboard,review,settings,statement,notifications,
                    onRequestPermissions = {},onOpenAppSettings = {},onLocaleChanged = {},onRequestExport = {},onRequestImport = {},onRequestExportLogs = {},onRequestRestoreBackup = {})
            } }
            click(R.string.lab_replica_reference_start_setup)
            click(R.string.lab_replica_onboarding_start_import)
            compose.waitUntil(30_000) { onboarding.uiState.value.step == OnboardingStep.OWNERSHIP }
            screenshot("01-discovered-ownership")
            val accounts = onboarding.uiState.value.accounts
            for ((index,candidate) in accounts.withIndex()) {
                selectOwnership(host.getString(R.string.lab_replica_onboarding_account_suffix,candidate.suffix),R.string.lab_replica_onboarding_is_mine_account)
                compose.waitUntil(20_000) { onboarding.uiState.value.accounts.first { it.bank==candidate.bank && it.suffix==candidate.suffix }.ownership == OwnershipStatus.OWNED }
            }
            val cards = onboarding.uiState.value.cards
            for ((index,candidate) in cards.withIndex()) {
                selectOwnership(host.getString(R.string.lab_replica_onboarding_card_suffix,candidate.suffix),R.string.lab_replica_onboarding_is_mine_card)
                compose.waitUntil(20_000) { onboarding.uiState.value.cards.first { it.bank==candidate.bank && it.suffix==candidate.suffix }.ownership == OwnershipStatus.OWNED }
            }
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text(R.string.lab_replica_onboarding_finalize)))
            compose.onNodeWithText(text(R.string.lab_replica_onboarding_finalize)).assertIsEnabled()
            screenshot("02-finish-setup-enabled")
            click(R.string.lab_replica_onboarding_finalize)
            compose.waitUntil(20_000) { onboarding.uiState.value.step == OnboardingStep.FINALIZE }
            click(R.string.lab_replica_onboarding_enter_app)
            compose.waitUntil(30_000) { !dashboard.uiState.value.loading && (dashboard.uiState.value.summary?.transactionCount ?: 0) > 0 }
            assertNull(dashboard.uiState.value.error)
            assertTrue(dashboard.uiState.value.ownedAccounts.isNotEmpty())
            assertTrue(dashboard.uiState.value.creditFacilities?.hasContent == true)
            screenshot("03-dashboard-with-data")
            click(R.string.lab_replica_approved_nav_transactions)
            screenshot("04-transactions")
            click(R.string.lab_replica_approved_nav_review)
            screenshot("05-review")
            click(R.string.lab_replica_approved_nav_settings)
            screenshot("06-settings")
            click(R.string.lab_replica_settings_loans_followed)
            compose.waitUntil(20_000) { settings.uiState.value.loans.any { it.loanType == LoanType.PERSONAL && it.ownership == OwnershipStatus.OWNED } }
            assertTrue(settings.uiState.value.loans.toString(),settings.uiState.value.loans.any { it.loanType == LoanType.PERSONAL && it.ownership == OwnershipStatus.OWNED })
            compose.onNodeWithText(text(R.string.lab_replica_settings_loan_type_personal)).assertExists()
            screenshot("07-recognized-loan")
            click(R.string.lab_replica_approved_nav_home)
            compose.waitUntil(20_000) { dashboard.uiState.value.loansOverview?.hasContent == true }
            assertEquals(0,runBlocking { container.reviewRepository.listRequired().size })
            screenshot("08-dashboard-with-loan")
            click(R.string.lab_replica_approved_nav_review)
            screenshot("09-review-after-ownership")
        } finally {
            compose.runOnUiThread { store.clear() }
            container.close()
            runBlocking { LabResetWorkflow.reset(host) }
        }
    }
    private fun selectOwnership(entityLabel: String, action: Int) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(entityLabel))
        val label = compose.onNodeWithText(entityLabel).fetchSemanticsNode().boundsInRoot
        val button = compose.onAllNodesWithText(text(action)).fetchSemanticsNodes()
            .filter { it.boundsInRoot.center.y >= label.bottom }.minBy { it.boundsInRoot.top - label.bottom }
        compose.onNode(SemanticsMatcher("ownership action for $entityLabel") { it.id == button.id }).performScrollTo().performClick()
    }
    private fun text(id: Int) = host.getString(id)
    private fun click(id: Int) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text(id)).fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithText(text(id)).performClick(); compose.waitForIdle() }
    private fun screenshot(name: String) {
        val file = File(host.getExternalFilesDir(null),"replica-journey/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
