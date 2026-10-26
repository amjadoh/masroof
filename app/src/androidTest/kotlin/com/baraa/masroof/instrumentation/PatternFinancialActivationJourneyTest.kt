package com.baraa.masroof.instrumentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.R
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.patterns.PatternOperation
import com.baraa.masroof.application.patterns.PatternTeachingSource
import com.baraa.masroof.domain.model.*
import com.baraa.masroof.presentation.locale.AppLocaleContext
import com.baraa.masroof.presentation.patterns.MessagePatternsScreen
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.testsupport.TestStorageContext
import com.baraa.masroof.testsupport.ScreenshotArtifactWriter
import com.baraa.masroof.ui.theme.MasroofTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PatternFinancialActivationJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val body = "Internal incoming transfer\nAmount:10SAR\nFrom:TEST PERSON\nAcc:321\nAt:15/10/26 12:30"
    private fun sms(id: String, amount: String) = body.replace("10SAR", "${amount}SAR").let {
        RawSms(id, "STC Bank", it, Instant.parse("2026-10-15T10:00:00Z"), id, SmsBodyHasher.sha256Hex(it))
    }
    private fun ar(id: Int) = AppLocaleContext.wrap(compose.activity, "ar").getString(id)
    private fun click(id: Int) { compose.onNodeWithText(ar(id)).performScrollTo().performClick(); compose.waitForIdle() }

    @Test fun approveExplicitReferenceThenProcessPendingAndFutureWithoutChangingPostedEvidence() = runBlocking {
        val context = TestStorageContext(compose.activity, "financial-pattern-${java.util.UUID.randomUUID()}")
        val c = AppContainer(context)
        try {
            val approvedGeneration = c.messagePatternsWorkflow.load().generation + 1
            c.processRawSmsUseCase.ingest(sms("pending", "10"))
            val dark = mutableStateOf(false)
            compose.setContent {
                val localized = AppLocaleContext.wrap(LocalContext.current, "ar")
                CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity) {
                    MasroofTheme(darkTheme = dark.value) { Surface {
                        MessagePatternsScreen(c.messagePatternsWorkflow, "BANK_STC", PatternTeachingSource("BANK_STC", "STC Bank", body), onBack = {})
                    } }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText(ar(R.string.pattern_financial_approve)).fetchSemanticsNodes().size == 6 }
            compose.onAllNodesWithText(ar(R.string.pattern_financial_approve))[0].performScrollTo().performClick()
            compose.onNodeWithTag("pattern-financial-body").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetText) {
                it(androidx.compose.ui.text.AnnotatedString("changed body"))
            }
            compose.onNodeWithTag("pattern-financial-body").assertTextContains(body)
            click(R.string.pattern_financial_select_operation)
            val names = AppLocaleContext.wrap(compose.activity, "ar").resources.getStringArray(R.array.pattern_operations)
            compose.onNodeWithText(names[PatternOperation.TRANSFER_IN.ordinal]).performClick()
            compose.onNodeWithTag("pattern-account-last4").performScrollTo().performTextInput("4321")
            click(R.string.pattern_financial_account_owned)
            click(R.string.pattern_financial_confirm_account)
            compose.waitUntil(10_000) { runBlocking { c.accountRegistryRepository.listAll().size == 1 } }
            click(R.string.pattern_financial_fixed_account)
            val keys = AppLocaleContext.wrap(compose.activity, "ar").resources.getStringArray(R.array.pattern_field_keys)
            val fields = AppLocaleContext.wrap(compose.activity, "ar").resources.getStringArray(R.array.pattern_field_names)
            compose.onNodeWithText(fields[keys.indexOf("account_reference")]).performClick()
            click(R.string.pattern_financial_validate)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("pattern-financial-save").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("pattern-financial-save").performScrollTo()
            compose.waitForIdle()
            ScreenshotArtifactWriter.publish(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(), "pattern-financial", "explicit-reference-light")
            compose.runOnIdle { dark.value = true }
            compose.waitForIdle()
            ScreenshotArtifactWriter.publish(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(), "pattern-financial", "explicit-reference-dark")
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText(ar(R.string.pattern_financial_approve)).fetchSemanticsNodes().size == 6 }
            assertTrue(c.messagePatternsWorkflow.load().banks.single { it.bankId == "BANK_STC" }.currentPatterns.all { it.factContract == null })
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            compose.onAllNodesWithText(ar(R.string.pattern_financial_approve))[0].performScrollTo().performClick()
            click(R.string.pattern_financial_select_operation)
            compose.onNodeWithText(names[PatternOperation.TRANSFER_IN.ordinal]).performClick()
            click(R.string.pattern_financial_select_account)
            compose.onNodeWithText(" • 4321").performClick()
            click(R.string.pattern_financial_fixed_account)
            compose.onNodeWithText(fields[keys.indexOf("account_reference")]).performClick()
            click(R.string.pattern_financial_validate)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("pattern-financial-save").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("pattern-financial-save").performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { c.messagePatternsWorkflow.load().generation == approvedGeneration } }
            click(R.string.pattern_apply_pending)
            compose.waitUntil(15_000) { runBlocking {
                c.financialTransactionRepository.listAll().size == 1 && c.reviewRepository.findByRawSmsId("pending")?.status == ReviewStatus.RESOLVED
            } }
            assertEquals(ReviewStatus.RESOLVED, c.reviewRepository.findByRawSmsId("pending")!!.status)
            assertEquals(FinancialTransactionType.EXTERNAL_TRANSFER_IN, c.financialTransactionRepository.listAll().single().type)
            val evidence = c.parsedEventRepository.findByRawSmsId("pending")
            c.processRawSmsUseCase.ingest(sms("future", "20"))
            assertEquals(2, c.financialTransactionRepository.listAll().size)
            c.messagePatternsWorkflow.setEnabled(approvedGeneration, "BANK_STC", "stc-internal-in", false)
            c.processRawSmsUseCase.ingest(sms("disabled", "30"))
            c.processStoredSmsUseCase.process("pending")
            assertEquals(evidence, c.parsedEventRepository.findByRawSmsId("pending"))
            assertEquals(2, c.financialTransactionRepository.listAll().size)
        } finally { c.close(); context.getDatabasePath("masroof.db").parentFile?.deleteRecursively() }
    }
}
