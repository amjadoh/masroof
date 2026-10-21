package com.baraa.masroof.instrumentation

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/**
 * Send real Android Back to the focused window, including Compose Popup/Dialog.
 * Espresso 3.6's root picker can wait on an unfocused root on API 36, before it
 * ever injects Back. Callers still assert the resulting dismissal/navigation.
 */
internal fun pressDeviceBack() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    check(UiDevice.getInstance(instrumentation).pressBack()) { "Could not inject Android Back" }
    instrumentation.waitForIdleSync()
}
