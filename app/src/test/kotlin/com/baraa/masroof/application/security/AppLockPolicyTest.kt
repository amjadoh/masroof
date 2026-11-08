package com.baraa.masroof.application.security

import com.baraa.masroof.presentation.security.AppLockController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test
    fun disabledLock_neverLocks() {
        assertFalse(AppLockPolicy.lockOnColdStart(enabled = false))
        assertFalse(AppLockPolicy.lockOnReturn(enabled = false, backgroundedAtMillis = 0L, nowMillis = 999_999L))
    }

    @Test
    fun shortTripOut_doesNotRelock_longAbsenceDoes() {
        assertFalse(AppLockPolicy.lockOnReturn(true, backgroundedAtMillis = 1_000L, nowMillis = 1_000L + 29_999L))
        assertTrue(AppLockPolicy.lockOnReturn(true, backgroundedAtMillis = 1_000L, nowMillis = 1_000L + 30_000L))
    }

    @Test
    fun clockMovedBackwards_relocks() {
        assertTrue(AppLockPolicy.lockOnReturn(true, backgroundedAtMillis = 50_000L, nowMillis = 10_000L))
    }

    @Test
    fun controller_failedPromptStaysLocked_successUnlocks() {
        val prefs = FakePrefs(enabled = true)
        var nextResult = false
        val controller = AppLockController(prefs, canAuthenticate = { true }, authenticate = { it(nextResult) })

        assertTrue(controller.locked)
        controller.unlock()
        assertTrue(controller.locked)
        nextResult = true
        controller.unlock()
        assertFalse(controller.locked)
    }

    @Test
    fun controller_changingTheSettingNeedsSuccessfulPrompt() {
        val prefs = FakePrefs(enabled = false)
        var nextResult = false
        val controller = AppLockController(prefs, canAuthenticate = { true }, authenticate = { it(nextResult) })

        controller.requestEnabled(true)
        assertFalse(prefs.isBiometricLockEnabled())
        nextResult = true
        controller.requestEnabled(true)
        assertTrue(prefs.isBiometricLockEnabled())
        assertTrue(controller.enabled)
    }

    @Test
    fun controller_cannotEnableWithoutDeviceSecurity() {
        val prefs = FakePrefs(enabled = false)
        val controller = AppLockController(prefs, canAuthenticate = { false }, authenticate = { it(true) })

        controller.requestEnabled(true)

        assertFalse(prefs.isBiometricLockEnabled())
    }

    @Test
    fun controller_relocksAfterLongBackground() {
        val prefs = FakePrefs(enabled = true)
        val controller = AppLockController(prefs, canAuthenticate = { true }, authenticate = { it(true) })
        controller.unlock()

        controller.onBackground(nowMillis = 0L)
        controller.onForeground(nowMillis = 5_000L)
        assertFalse(controller.locked)

        controller.onBackground(nowMillis = 10_000L)
        controller.onForeground(nowMillis = 60_000L)
        assertTrue(controller.locked)
    }

    private class FakePrefs(private var enabled: Boolean) : AppLockPreferencesRepository {
        override fun isBiometricLockEnabled(): Boolean = enabled

        override fun setBiometricLockEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }
}
