package com.baraa.masroof.data.preferences

import android.content.SharedPreferences
import com.baraa.masroof.application.security.AppLockPreferencesRepository

class SharedPrefsAppLockPreferencesRepository(
    private val prefs: SharedPreferences,
) : AppLockPreferencesRepository {
    override fun isBiometricLockEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)

    override fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
    }

    companion object {
        const val PREFS_NAME: String = "app_lock_prefs"
        const val KEY_BIOMETRIC_LOCK: String = "biometric_lock_enabled"
    }
}
