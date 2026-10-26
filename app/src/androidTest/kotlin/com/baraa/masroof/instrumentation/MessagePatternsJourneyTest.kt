package com.baraa.masroof.instrumentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.MessagePatternsWorkflow
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.lab.bank.patterns.BankMessagePatterns
import com.baraa.masroof.presentation.locale.AppLocaleContext
import com.baraa.masroof.presentation.patterns.MessagePatternsScreen
import com.baraa.masroof.testsupport.ScreenshotArtifactWriter
import com.baraa.masroof.ui.theme.MasroofTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessagePatternsJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun teachPreviewSaveAndDisableInArabic() = runBlocking {
        val file = compose.activity.cacheDir.resolve("synthetic-pattern-journey.json")
        file.delete()
        val store = MessagePatternStore(file)
        val workflow = MessagePatternsWorkflow(store, DatabaseAccessGate())
        workflow.saveBank(0, BankMessagePatterns("STC", listOf("STC Bank"), "بنك التجربة"))
        val dark = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            val localized = AppLocaleContext.wrap(LocalContext.current, "ar")
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
                MasroofTheme(darkTheme = dark.value) { Surface { MessagePatternsScreen(workflow, "STC", onBack = {}) } }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ar(R.string.pattern_teach)).fetchSemanticsNodes().isNotEmpty() }
        capture("pattern-bank")
        click(R.string.pattern_teach)
        compose.onNodeWithTag("pattern-body").performScrollTo().performTextInput("Payment\nAmount:10SAR\nTo:SHOP")
        click(R.string.pattern_extract)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("10SAR").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("10SAR").performScrollTo().performClick()
        click(R.string.pattern_bind)
        click(R.string.pattern_preview)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("pattern-sample").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(store.load().banks.single().patterns.isEmpty())
        compose.onNodeWithTag("pattern-sample").performScrollTo().performTextInput("Payment\nAmount:250SAR\nTo:SHOP")
        click(R.string.pattern_test)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("مبلغ العملية: 250SAR").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("مبلغ العملية: 250SAR").performScrollTo().assertIsDisplayed()
        capture("pattern-preview")
        click(R.string.pattern_save)
        compose.waitUntil(10_000) { store.load().banks.single().patterns.size == 1 }
        click(R.string.pattern_disable)
        compose.waitUntil(10_000) { store.load().banks.single().patterns.size == 2 }
        assertFalse(store.load().banks.single().currentPatterns.single().enabled)
        capture("pattern-disabled")
        compose.runOnIdle { dark.value = true }
        capture("pattern-disabled-dark")
        click(R.string.pattern_teach)
        compose.onNodeWithTag("pattern-body").performScrollTo().performTextInput("Payment\nAmount:30SAR")
        click(R.string.pattern_extract)
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(ar(R.string.pattern_teach)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, store.load().banks.single().patterns.size)
        file.delete()
        Unit
    }

    private fun ar(id: Int) = AppLocaleContext.wrap(compose.activity, "ar").getString(id)
    private fun click(id: Int) { compose.onNodeWithText(ar(id)).performScrollTo().performClick(); compose.waitForIdle() }
    private fun capture(name: String) {
        compose.waitForIdle()
        ScreenshotArtifactWriter.publish(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(), "pattern-journey", name)
    }
}
