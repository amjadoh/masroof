package com.baraa.masroof.instrumentation

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/**
 * Send real Android Back to the focused window, including Compose Popup/Dialog.
 * Espresso 3.6's root picker can wait on an unfocused root on API 36, before it
 * ever injects Back. Callers still assert the resulting dismissal/navigation.
 */
internal fun pressDeviceBack(compose: ComposeTestRule) {
    // Settle pending Compose state changes before delivering a hardware event.
    compose.waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    // pressBack() also requires an accessibility content-change event, which
    // Popup dismissal or a busy sheet deliberately blocking Back may not emit.
    check(UiDevice.getInstance(instrumentation).pressKeyCode(KeyEvent.KEYCODE_BACK)) {
        "Could not inject Android Back"
    }
    instrumentation.waitForIdleSync()
    compose.waitForIdle()
}
