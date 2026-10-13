package com.baraa.masroof.instrumentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.baraa.masroof.lab.presentation.ParsingLabEntry
import org.junit.Rule
import org.junit.Test
import java.io.File

class ParsingLabSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun seedsSamples_runsDatedBatch_andShowsExpectedComparison() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("masroof_parsing_lab.db")
        compose.setContent { ParsingLabEntry {} }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("إضافة الجزيرة وعينات توضيحية").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("إضافة الجزيرة وعينات توضيحية").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("الإصدار v1 · 5 قواعد · 4 عينات").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("النتائج").performClick()
        compose.onNodeWithText("تشغيل وحفظ النتائج").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("مطابقة كاملة: 4 / 4 عينة موسومة · 0 غير موسومة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("مطابقة كاملة: 4 / 4 عينة موسومة · 0 غير موسومة").performScrollTo().assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            File(context.getExternalFilesDir(null), "parsing-lab-results.png"))
        compose.onNodeWithText("القواعد").performClick()
        compose.onNodeWithText("قواعد v1").assertExists()
        compose.onNodeWithText("البنوك").performClick()
        compose.onNodeWithText("البنوك التجريبية").performScrollTo().assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            File(context.getExternalFilesDir(null), "parsing-lab-banks.png"))
    }
}
