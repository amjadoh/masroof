package com.baraa.masroof.lab.beta.presentation

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.posting.PostingKey
import com.baraa.masroof.lab.finance.value.*
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** All evidence/storage is synthetic in the isolated test APK. No production/inbox reads. */
class BetaCleanJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val bank = BankId("BANK_ALJAZIRA")
    private fun await(tag: String) { compose.waitUntil(20000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }; compose.waitForIdle() }
    private fun click(tag: String) { await(tag); compose.onNodeWithTag(tag).performScrollTo().assertIsEnabled().performClick() }
    private fun drain() { val done = CountDownLatch(1); BetaIo.execute { done.countDown() }; assertTrue(done.await(20, TimeUnit.SECONDS)) }

    private fun capture(name: String) {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /sdcard/beta-dashboard-screenshots")).use { it.readBytes() }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /sdcard/beta-dashboard-screenshots/$name.png")).use { it.readBytes() }
    }

    @Test fun optionalSetupPersistsAndSettingsContainOnlyFinancialManagement() {
        BetaContainer.resetOffline(context)
        try {
            ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
                click("beta-start-continue"); await("onboarding-BANK")
                capture("m10-bank-step")
                scenario.recreate(); await("onboarding-BANK")
                click("onboarding-bank-BANK_ALJAZIRA"); await("onboarding-IMPORT")
                click("onboarding-import"); await("history-start")
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                await("onboarding-IMPORT")
                click("onboarding-next"); await("onboarding-ASSETS")
                click("onboarding-manage"); await("beta-management-settings")
                compose.onNodeWithTag("beta-theme-DARK").assertDoesNotExist()
                compose.onNodeWithTag("beta-settings-reset").assertDoesNotExist()
                click("manage-ACCOUNTS"); click("manage-add-BANK_ALJAZIRA")
                compose.onNodeWithTag("asset-last4").performScrollTo().performTextInput("1234")
                compose.onNodeWithTag("asset-save").performScrollTo().assertIsNotEnabled()
                click("asset-owned-confirm"); click("asset-save")
                await("manage-add-BANK_ALJAZIRA")
                click("beta-settings-close"); await("beta-management-settings")
                click("beta-settings-close"); await("onboarding-ASSETS")
                click("onboarding-next"); await("onboarding-REVIEW")
                scenario.recreate(); await("onboarding-REVIEW")
                click("onboarding-next"); await("dash-refresh")
                val settings = compose.onNode(hasText(context.getString(com.baraa.masroof.lab.beta.R.string.beta_nav_settings)) and hasAnyAncestor(hasTestTag("beta-bottom-navigation")))
                settings.performClick(); await("beta-management-settings"); capture("m10-management-settings")
                compose.onNodeWithTag("beta-developer-tools").assertDoesNotExist()
                compose.onNodeWithTag("beta-settings-backup").assertDoesNotExist()
                click("manage-COMMITMENTS"); await("controls-add")
                compose.onNodeWithTag("controls-salary").assertDoesNotExist()
                click("beta-settings-close"); click("beta-settings-close"); await("dash-refresh")
            }
            drain()
            BetaContainer(context).use {
                assertEquals(BetaOnboardingStep.COMPLETE, it.onboardingStep())
                assertEquals(1, it.assets().size)
                assertTrue(it.evidencePage().isEmpty()); assertNull(it.historySession())
            }
            ActivityScenario.launch(BetaActivity::class.java).use { await("dash-refresh"); compose.onNodeWithTag("onboarding-BANK").assertDoesNotExist() }
        } finally { drain(); BetaContainer.resetOffline(context) }
    }

    @Test fun currentEngineDiscoveryAndExplicitApprovalReachHomeOnce() {
        BetaContainer.resetOffline(context)
        lateinit var evidenceId: String
        try {
            BetaContainer(context).use { beta ->
                beta.beginOnboarding(); beta.setBankEnabled(bank, true); beta.advanceOnboarding(BetaOnboardingStep.BANK)
                val at = Instant.parse("2026-10-18T12:00:00Z")
                val message = EvidenceMessage("clean-journey", "AlJazira", "Cash withdrawal\nFrom account: 1234\nAmount: 42 SAR\nDate: 2026-10-18 12:00", at, ReceiptTimeSource.USER_SUPPLIED)
                evidenceId = (beta.importEvidence(EvidenceRequest("clean-journey", EvidenceOrigin.EXPLICIT_SAMPLE), listOf(message)).single() as EvidenceResult.Stored).summary.id
                assertNull(requireNotNull(beta.financialApprovalPreview(evidenceId)).blockedReason)
                assertNull(beta.financialPosting(PostingKey(Environment.BETA, "evidence:$evidenceId")))
                beta.advanceOnboarding(BetaOnboardingStep.IMPORT)
            }
            ActivityScenario.launch(BetaActivity::class.java).use {
                await("onboarding-ASSETS"); click("onboarding-discovery"); click("proposal-open-0")
                click("asset-owned-confirm"); click("asset-save"); await("proposal-close"); click("proposal-close")
                await("onboarding-ASSETS"); click("onboarding-next"); click("onboarding-approval")
                click("approval-item-0"); click("approval-type-CASH_WITHDRAWAL"); click("approval-source-0"); click("approval-destination-external")
                compose.onNodeWithTag("approval-save").performScrollTo().assertIsNotEnabled()
                click("approval-confirm"); click("approval-save"); await("approval-item-0")
                click("approval-close"); await("onboarding-REVIEW"); click("onboarding-next"); await("dash-refresh")
            }
            drain()
            val key = PostingKey(Environment.BETA, "evidence:$evidenceId")
            BetaContainer(context).use { beta ->
                assertEquals(Money.of("42", Currency.SAR), requireNotNull(beta.financialPosting(key)).command.amount)
                assertEquals(1, beta.financialHistory(key).size)
                assertEquals(BetaOnboardingStep.COMPLETE, beta.onboardingStep())
            }
            ActivityScenario.launch(BetaActivity::class.java).use { await("dash-refresh") }
            drain()
            BetaContainer(context).use { assertEquals(1, it.financialHistory(key).size) }
        } finally { drain(); BetaContainer.resetOffline(context) }
    }
}
