package com.baraa.masroof.instrumentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.baraa.masroof.R
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.patterns.PatternTeachingSource
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.model.ReviewStatus
import com.baraa.masroof.engine.bank.patterns.BankMessagePatterns
import com.baraa.masroof.presentation.locale.AppLocaleContext
import com.baraa.masroof.presentation.patterns.MessagePatternsScreen
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.testsupport.TestStorageContext
import com.baraa.masroof.ui.theme.MasroofTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PatternCurrentMessageJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun ar(id: Int) = AppLocaleContext.wrap(compose.activity, "ar").getString(id)
    private fun click(id: Int) { compose.onNodeWithText(ar(id)).performScrollTo().performClick() }

    @Test fun teachApproveAndApplyFromReviewPostsOnlyTheExplicitCurrentMessage() = runBlocking {
        val context = TestStorageContext(compose.activity, "ui-current-${java.util.UUID.randomUUID()}")
        val c = AppContainer(context)
        try {
            val w = c.messagePatternsWorkflow
            w.saveBank(w.load().generation, BankMessagePatterns("UI_BANK", listOf("UI Bank"), "بنك التجربة"))
            w.confirmPatternAccount("UI_BANK", "1234")
            val body = "Purchase\nAmount:10SAR\nAt:2026-10-15 12:30"
            for ((id, text) in listOf("ui-current" to body, "ui-other" to body.replace("10SAR", "20SAR"))) {
                c.processRawSmsUseCase.ingest(RawSms(id, "UI Bank", text, Instant.parse("2026-10-15T10:00:00Z"), id, SmsBodyHasher.sha256Hex(text)))
            }
            val current = c.reviewRepository.findByRawSmsId("ui-current")!!
            val untouched = c.parsedEventRepository.findByRawSmsId("ui-other")
            compose.setContent {
                val localized = AppLocaleContext.wrap(LocalContext.current, "ar")
                CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity) {
                    MasroofTheme { Surface {
                        MessagePatternsScreen(w, source = PatternTeachingSource("UI_BANK", "UI Bank", body, current.id), onBack = {})
                    } }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("pattern-body").fetchSemanticsNodes().isNotEmpty() }
            click(R.string.teaching_next)
            compose.waitUntil(10_000) { compose.onAllNodesWithText("10SAR").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("10SAR").performScrollTo().performClick()
            compose.onNodeWithTag("pattern-bind").performScrollTo().performClick()
            click(R.string.teaching_skip_field)
            click(R.string.teaching_skip_field)
            compose.onNodeWithText("2026-10-15 12:30").performScrollTo().performClick()
            compose.onNodeWithTag("pattern-bind").performScrollTo().performClick()
            click(R.string.pattern_preview)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(ar(R.string.pattern_save)).fetchSemanticsNodes().isNotEmpty() }
            click(R.string.pattern_save)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(ar(R.string.teaching_saved_title)).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(c.financialTransactionRepository.listAll().isEmpty())
            click(R.string.pattern_financial_approve)
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("pattern-financial-save").assertIsEnabled() }.isSuccess }
            compose.onNodeWithTag("pattern-financial-save").performScrollTo().performClick()
            try {
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("pattern-apply-current").fetchSemanticsNodes().isNotEmpty() }
            } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                println(compose.onRoot(useUnmergedTree = true).printToString())
                throw failure
            }
            compose.onNodeWithTag("pattern-apply-current").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText(ar(R.string.teaching_apply_posted)).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(ReviewStatus.RESOLVED, c.reviewRepository.getById(current.id)?.status)
            assertEquals(1, c.financialTransactionRepository.listAll().size)
            assertEquals(ReviewStatus.REQUIRED, c.reviewRepository.findByRawSmsId("ui-other")!!.status)
            assertEquals(untouched, c.parsedEventRepository.findByRawSmsId("ui-other"))
            compose.onNodeWithText(ar(R.string.teaching_apply_posted)).assertExists()
        } finally { c.close(); context.getDatabasePath("masroof.db").parentFile?.deleteRecursively() }
        Unit
    }
}
