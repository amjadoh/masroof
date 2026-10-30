package com.baraa.masroof.instrumentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.baraa.masroof.presentation.common.MasroofPeriodDisplay
import com.baraa.masroof.presentation.locale.AppLocaleContext
import com.baraa.masroof.ui.theme.MasroofTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PeriodDisplayLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun longEnglishPeriodDoesNotConsumeTheFoldedViewport() {
        compose.setContent {
            val context = AppLocaleContext.wrap(LocalContext.current, "en")
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration) {
                MasroofTheme {
                    Box(Modifier.fillMaxSize()) {
                        MasroofPeriodDisplay("Salary period: 27 September — 26 October 2026",
                            Modifier.width(320.dp).testTag("period-display"))
                    }
                }
            }
        }
        val period = compose.onNodeWithTag("period-display")
        period.assertIsDisplayed()
        val height = period.fetchSemanticsNode().boundsInRoot.height
        assertTrue("A translated date must leave space for the transaction list: $height",
            height < 160 * compose.activity.resources.displayMetrics.density)
    }
}
