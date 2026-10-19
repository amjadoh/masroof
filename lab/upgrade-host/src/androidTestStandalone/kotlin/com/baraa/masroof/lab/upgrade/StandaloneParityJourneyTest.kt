package com.baraa.masroof.lab.upgrade

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.application.sms.BetaLiveSms
import com.baraa.masroof.lab.beta.presentation.BetaActivity
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.time.*
import java.util.concurrent.TimeUnit
import org.junit.*
import org.junit.Assert.*

/** Installed only with the standalone lab host. The production package is never launched. */
class StandaloneParityJourneyTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val bank=BankId("BANK_ALJAZIRA")
    @Before fun fresh() {
        assertEquals(BetaLiveSms.HOST_PACKAGE,context.packageName)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName,Manifest.permission.READ_SMS)
        automation.grantRuntimePermission(context.packageName,Manifest.permission.RECEIVE_SMS)
        WorkManager.getInstance(context).cancelAllWork().result.get(10,TimeUnit.SECONDS)
        BetaContainer.resetOffline(context)
    }
    private fun await(tag: String) {compose.waitUntil(20000) {compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()};compose.waitForIdle()}
    private fun click(tag: String) {
        await(tag);val node=compose.onNodeWithTag(tag)
        compose.waitUntil(20000) {runCatching {node.assertIsEnabled();true}.getOrDefault(false)}
        runCatching {node.performScrollTo()}.getOrElse {node.assertIsDisplayed()}
        node.performClick()
    }
    private fun capture(name: String) {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /sdcard/beta-parity")).use {it.readBytes()}
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /sdcard/beta-parity/$name.png")).use {it.readBytes()}
    }
    @Test fun knownCreditCardSetupReachesHomeWithSpendingWithoutFacilityQuestions() {
        ActivityScenario.launch(BetaActivity::class.java).use { scenario ->
            click("beta-start-continue");await("onboarding-IMPORT")
            BetaContainer(context).use {it.setLanguage("ar")}
            scenario.recreate();await("onboarding-IMPORT");capture("date")
            val receipt=Instant.now();val date=receipt.atZone(ZoneId.of("Asia/Riyadh")).toLocalDateTime().withSecond(0).withNano(0)
            BetaContainer(context).use {beta -> beta.importEvidence(EvidenceRequest("standalone-journey",EvidenceOrigin.EXPLICIT_SAMPLE),listOf(
                EvidenceMessage("purchase","AlJazira","POS Purchase\nCard: 5678\nCard type: credit\nAmount: 42 SAR\nAvailable balance: 100 SAR\nDate: $date",receipt,ReceiptTimeSource.USER_SUPPLIED))) }
            scenario.recreate();await("onboarding-IMPORT");click("history-close");await("onboarding-ASSETS")
            click("ownership-BANK_ALJAZIRA-CARD-5678-owned");capture("ownership")
            click("ownership-continue");await("onboarding-REVIEW");capture("complete")
            click("onboarding-next");await("beta-home-hero");capture("home")
            compose.onNodeWithTag("asset-create-facility").assertDoesNotExist()
            BetaContainer(context).use {beta ->
                assertEquals(BetaOnboardingStep.COMPLETE,beta.onboardingStep())
                assertEquals(1,beta.assets().size)
                assertTrue(beta.evidencePage(needsReviewOnly=true).isEmpty())
                val id=beta.evidencePage().single().id
                assertEquals("42.00",requireNotNull(beta.financialPosting(PostingKey(Environment.BETA,"evidence:$id"))).command.amount.amount.toPlainString())
            }
        }
    }
    @Test fun realWorkManagerConsumesOnlyDurableIdsAndPostsOnce() {
        val receipt=Instant.now();val date=receipt.atZone(ZoneId.of("Asia/Riyadh")).toLocalDateTime().withSecond(0).withNano(0)
        lateinit var id: String;lateinit var generation: String
        BetaContainer(context).use {beta ->
            beta.beginOnboarding();beta.advanceOnboarding(BetaOnboardingStep.PERMISSION);beta.advanceOnboarding(BetaOnboardingStep.IMPORT)
            beta.advanceOnboarding(BetaOnboardingStep.ASSETS);beta.advanceOnboarding(BetaOnboardingStep.REVIEW)
            beta.saveManualAsset(ManualAssetDraft(AssetId(Environment.BETA,bank,"account"),AssetKind.ACCOUNT,"1234",ownedConfirmed=true))
            id=requireNotNull(beta.captureLive(EvidenceMessage("live-native","AlJazira","Cash withdrawal\nFrom account: 1234\nAmount: 42 SAR\nDate: $date",receipt,ReceiptTimeSource.SERVICE_CENTER_TIMESTAMP)))
            generation=requireNotNull(beta.storageGeneration())
        }
        BetaLiveSms.schedule(context,id,generation)
        val manager=WorkManager.getInstance(context);val name="beta-live:$generation:$id"
        compose.waitUntil(30000) {manager.getWorkInfosForUniqueWork(name).get(3,TimeUnit.SECONDS).singleOrNull()?.state?.isFinished==true}
        assertEquals(WorkInfo.State.SUCCEEDED,manager.getWorkInfosForUniqueWork(name).get().single().state)
        BetaContainer(context).use {beta -> assertNotNull(beta.financialPosting(PostingKey(Environment.BETA,"evidence:$id")));assertEquals(1,beta.financialHistory(PostingKey(Environment.BETA,"evidence:$id")).size)}
    }
}
