package com.baraa.masroof.application.security

/** Device-local app-lock preference. Not part of backups: a restored device must opt in again. */
interface AppLockPreferencesRepository {
    fun isBiometricLockEnabled(): Boolean

    fun setBiometricLockEnabled(enabled: Boolean)

    /** The eye button's "hide amounts" choice, remembered across launches. */
    fun isAmountsHidden(): Boolean = false

    fun setAmountsHidden(hidden: Boolean) = Unit
}

/**
 * When the financial screens must be hidden behind biometric / device-credential unlock.
 *
 * The app locks on a cold start and after it has been in the background for at least
 * [RELOCK_AFTER_BACKGROUND_MILLIS]. A short trip out (e.g. picking a statement file) does not relock.
 */
object AppLockPolicy {
    const val RELOCK_AFTER_BACKGROUND_MILLIS: Long = 30_000L

    fun lockOnColdStart(enabled: Boolean): Boolean = enabled

    fun lockOnReturn(
        enabled: Boolean,
        backgroundedAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (!enabled) return false
        val since = backgroundedAtMillis ?: return false
        // A clock moved backwards is treated as a long absence.
        val elapsed = nowMillis - since
        return elapsed < 0 || elapsed >= RELOCK_AFTER_BACKGROUND_MILLIS
    }
}
