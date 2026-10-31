package com.baraa.masroof.instrumentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baraa.masroof.ui.components.LocalMasroofCoachMarkTargets
import com.baraa.masroof.ui.components.MasroofCoachMark
import com.baraa.masroof.ui.components.MasroofCoachMarkTargets
import com.baraa.masroof.ui.components.MasroofCoachMarkTour
import com.baraa.masroof.ui.components.coachMarkTarget
import com.baraa.masroof.ui.theme.MasroofTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoachMarkTourTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tourWalksVisibleTargetsInOrderBlocksTheScreenAndFinishesOnce() {
        val targets = MasroofCoachMarkTargets()
        var finished = 0
        var tapsBehind = 0
        var open by mutableStateOf(true)
        compose.setContent { MasroofTheme { CompositionLocalProvider(LocalMasroofCoachMarkTargets provides targets) {
            Box(Modifier.fillMaxSize()) {
                Column {
                    Box(Modifier.size(120.dp).coachMarkTarget("a"))
                    androidx.compose.material3.Button({ tapsBehind++ }, Modifier.coachMarkTarget("b")) {
                        androidx.compose.material3.Text("behind")
                    }
                }
                if (open) MasroofCoachMarkTour(targets, listOf(MasroofCoachMark("a", "First", "One"),
                    MasroofCoachMark("missing", "Skipped", "Never shown"), MasroofCoachMark("b", "Second", "Two")),
                    "Next", "Done", "Skip", { current, total -> "$current/$total" }) { finished++; open = false }
            }
        } } }
        compose.onNodeWithText("First").assertIsDisplayed()
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithText("behind").performClick()
        assertEquals(0, tapsBehind)
        compose.onNodeWithTag("coach-mark-next").performClick()
        compose.onNodeWithText("Second").assertIsDisplayed()
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.onNodeWithTag("coach-mark-skip").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        assertEquals(1, finished)
        compose.onNodeWithTag("coach-mark-tour").assertDoesNotExist()
        assertFalse(open)
    }
}
