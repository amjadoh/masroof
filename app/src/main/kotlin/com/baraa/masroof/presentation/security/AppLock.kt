package com.baraa.masroof.presentation.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.security.AppLockPolicy
import com.baraa.masroof.application.security.AppLockPreferencesRepository
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofText
import com.baraa.masroof.ui.theme.MasroofReference
import com.baraa.masroof.ui.theme.MasroofSpacing

/**
 * Holds the biometric app-lock state for the activity.
 *
 * [authenticate] shows the system biometric / device-credential prompt and reports success.
 * Turning the lock on or off also requires a successful prompt, so a person holding an
 * unlocked phone cannot silently disable it.
 */
class AppLockController(
    private val preferences: AppLockPreferencesRepository,
    private val canAuthenticate: () -> Boolean,
    private val authenticate: (onResult: (Boolean) -> Unit) -> Unit,
) {
    var enabled: Boolean by mutableStateOf(preferences.isBiometricLockEnabled())
        private set

    var locked: Boolean by mutableStateOf(AppLockPolicy.lockOnColdStart(enabled))
        private set

    private var backgroundedAtMillis: Long? = null

    val available: Boolean get() = canAuthenticate()

    fun onBackground(nowMillis: Long) {
        if (!locked) backgroundedAtMillis = nowMillis
    }

    fun onForeground(nowMillis: Long) {
        if (AppLockPolicy.lockOnReturn(enabled, backgroundedAtMillis, nowMillis)) locked = true
        backgroundedAtMillis = null
    }

    fun unlock() {
        if (!locked) return
        authenticate { success -> if (success) locked = false }
    }

    fun requestEnabled(on: Boolean) {
        if (on == enabled) return
        if (on && !available) return
        authenticate { success ->
            if (success) {
                preferences.setBiometricLockEnabled(on)
                enabled = on
            }
        }
    }
}

val LocalAppLockController = staticCompositionLocalOf<AppLockController?> { null }

/**
 * Opaque cover drawn over the app while locked. The app stays composed underneath so file-picker
 * results (e.g. a statement chosen just before relocking) are still delivered, but nothing is
 * visible, touchable or reachable with Back until unlock.
 */
@Composable
fun AppLockOverlay(controller: AppLockController) {
    androidx.activity.compose.BackHandler(enabled = true) {}
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        AppLockScreen(controller)
    }
}

/** Full-screen lock content. The prompt opens automatically once. */
@Composable
fun AppLockScreen(controller: AppLockController) {
    LaunchedEffect(Unit) { controller.unlock() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MasroofSpacing.screenPaddingLarge),
        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            MasroofIcons.ownership,
            contentDescription = null,
            modifier = Modifier.size(MasroofReference.iconBox),
            tint = MaterialTheme.colorScheme.primary,
        )
        MasroofText(stringResource(R.string.app_lock_title), style = MaterialTheme.typography.titleLarge)
        MasroofText(
            stringResource(R.string.app_lock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = controller::unlock) { MasroofText(stringResource(R.string.app_lock_unlock)) }
    }
}

/** Settings card: biometric unlock on/off. Hidden when no lock controller is provided. */
@Composable
fun AppLockSettingsCard() {
    val controller = LocalAppLockController.current ?: return
    val available = controller.available
    MasroofCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
                MasroofText(stringResource(R.string.app_lock_setting_title), style = MaterialTheme.typography.titleMedium)
                MasroofText(
                    stringResource(if (available) R.string.app_lock_setting_body else R.string.app_lock_setting_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = controller.enabled,
                onCheckedChange = controller::requestEnabled,
                enabled = available || controller.enabled,
            )
        }
    }
}
