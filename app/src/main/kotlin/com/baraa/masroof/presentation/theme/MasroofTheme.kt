package com.baraa.masroof.presentation.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.baraa.masroof.application.locale.AppLocale

private val LightColorScheme = lightColorScheme(
    primary = MasroofLightColors.Primary,
    onPrimary = MasroofLightColors.OnPrimary,
    primaryContainer = MasroofLightColors.PrimaryContainer,
    onPrimaryContainer = MasroofLightColors.OnPrimaryContainer,
    secondaryContainer = MasroofLightColors.SecondaryContainer,
    onSecondaryContainer = MasroofLightColors.OnSecondaryContainer,
    tertiary = MasroofLightColors.Tertiary,
    onTertiary = MasroofLightColors.OnTertiary,
    background = MasroofLightColors.Background,
    onBackground = MasroofLightColors.OnSurface,
    surface = MasroofLightColors.Surface,
    onSurface = MasroofLightColors.OnSurface,
    onSurfaceVariant = MasroofLightColors.OnSurfaceVariant,
    surfaceVariant = MasroofLightColors.SurfaceVariant,
    surfaceDim = MasroofLightColors.SurfaceDim,
    surfaceBright = MasroofLightColors.SurfaceBright,
    surfaceContainerLowest = MasroofLightColors.SurfaceContainerLowest,
    surfaceContainerLow = MasroofLightColors.SurfaceContainerLow,
    surfaceContainer = MasroofLightColors.SurfaceContainer,
    surfaceContainerHigh = MasroofLightColors.SurfaceContainerHigh,
    surfaceContainerHighest = MasroofLightColors.SurfaceContainerHighest,
    outline = MasroofLightColors.Outline,
    outlineVariant = MasroofLightColors.OutlineVariant,
    error = MasroofLightColors.Error,
    onError = MasroofLightColors.OnError,
    errorContainer = MasroofLightColors.ErrorContainer,
    onErrorContainer = MasroofLightColors.OnErrorContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = MasroofDarkColors.Primary,
    onPrimary = MasroofDarkColors.OnPrimary,
    primaryContainer = MasroofDarkColors.PrimaryContainer,
    onPrimaryContainer = MasroofDarkColors.OnPrimaryContainer,
    secondaryContainer = MasroofDarkColors.SecondaryContainer,
    onSecondaryContainer = MasroofDarkColors.OnSecondaryContainer,
    tertiary = MasroofDarkColors.Tertiary,
    onTertiary = MasroofDarkColors.OnTertiary,
    background = MasroofDarkColors.Background,
    onBackground = MasroofDarkColors.OnSurface,
    surface = MasroofDarkColors.Surface,
    onSurface = MasroofDarkColors.OnSurface,
    onSurfaceVariant = MasroofDarkColors.OnSurfaceVariant,
    surfaceVariant = MasroofDarkColors.SurfaceVariant,
    surfaceDim = MasroofDarkColors.SurfaceDim,
    surfaceBright = MasroofDarkColors.SurfaceBright,
    surfaceContainerLowest = MasroofDarkColors.SurfaceContainerLowest,
    surfaceContainerLow = MasroofDarkColors.SurfaceContainerLow,
    surfaceContainer = MasroofDarkColors.SurfaceContainer,
    surfaceContainerHigh = MasroofDarkColors.SurfaceContainerHigh,
    surfaceContainerHighest = MasroofDarkColors.SurfaceContainerHighest,
    outline = MasroofDarkColors.Outline,
    outlineVariant = MasroofDarkColors.OutlineVariant,
    error = MasroofDarkColors.Error,
    onError = MasroofDarkColors.OnError,
    errorContainer = MasroofDarkColors.ErrorContainer,
    onErrorContainer = MasroofDarkColors.OnErrorContainer,
)

@Composable
fun MasroofTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val layoutDirection = if (AppLocale.isRtl(languageTag)) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) darkExtendedColors() else lightExtendedColors()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalMasroofExtendedColors provides extendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MasroofTypography,
            shapes = MasroofShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object MasroofThemeExtras {
    val extendedColors
        @Composable
        get() = LocalMasroofExtendedColors.current
}
