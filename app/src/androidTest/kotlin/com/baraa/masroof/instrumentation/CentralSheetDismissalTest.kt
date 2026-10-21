package com.baraa.masroof.instrumentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.baraa.masroof.presentation.common.MasroofBottomSheet
import com.baraa.masroof.presentation.common.MasroofText
import com.baraa.masroof.ui.theme.MasroofTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CentralSheetDismissalTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun scrimSwipeAndBackDiscardDraft_butBusyWriteBlocksDismissal() {
        val open = mutableStateOf(true)
        val busy = mutableStateOf(false)
        var saves = 0
        compose.setContent {
            MasroofTheme {
                Box(Modifier.fillMaxSize()) {
                    if (open.value) MasroofBottomSheet(
                        title = "Window test", onDismiss = { open.value = false },
                        onApply = { saves++ }, applyLabel = "Save", busy = busy.value,
                    ) { MasroofText("Draft") }
                }
            }
        }
        fun scrimTap() = compose.onNodeWithTag("sheet-scrim").performTouchInput { click(Offset(8f, 8f)) }
        fun swipe() = compose.onNodeWithTag("sheet-drag-handle").performTouchInput {
            swipe(center, center + Offset(0f, 120f * compose.activity.resources.displayMetrics.density), 500)
        }
        scrimTap()
        compose.onNodeWithTag("sheet-overlay").assertDoesNotExist()
        compose.runOnIdle { open.value = true }
        swipe()
        compose.onNodeWithTag("sheet-overlay").assertDoesNotExist()
        compose.runOnIdle { open.value = true; busy.value = true }
        compose.onNodeWithTag("sheet-close").assertIsNotEnabled()
        compose.onNodeWithTag("sheet-cancel").assertIsNotEnabled()
        compose.onNodeWithTag("sheet-apply").assertIsNotEnabled()
        scrimTap(); swipe(); pressDeviceBack(compose)
        compose.onNodeWithTag("sheet-overlay").assertExists()
        compose.runOnIdle { busy.value = false }
        pressDeviceBack(compose)
        compose.onNodeWithTag("sheet-overlay").assertDoesNotExist()
        assertEquals(0, saves)
    }
}
