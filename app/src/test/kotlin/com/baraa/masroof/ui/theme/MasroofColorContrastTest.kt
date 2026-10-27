package com.baraa.masroof.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class MasroofColorContrastTest {
    @Test
    fun lightPrimaryContainer_supportsLargeText() {
        assertTrue(
            meetsWcagAaLargeText(
                MasroofLightColors.OnPrimaryContainer,
                MasroofLightColors.PrimaryContainer,
            ),
        )
    }

    @Test
    fun lightPrimary_supportsLargeText() {
        assertTrue(
            meetsWcagAaLargeText(
                MasroofLightColors.OnPrimary,
                MasroofLightColors.Primary,
            ),
        )
    }

    @Test
    fun darkPrimaryContainer_supportsLargeText() {
        assertTrue(
            meetsWcagAaLargeText(
                MasroofDarkColors.OnPrimaryContainer,
                MasroofDarkColors.PrimaryContainer,
            ),
        )
    }

    @Test
    fun darkPrimary_supportsLargeText() {
        assertTrue(
            meetsWcagAaLargeText(
                MasroofDarkColors.OnPrimary,
                MasroofDarkColors.Primary,
            ),
        )
    }

    @Test
    fun darkOnSurface_supportsNormalTextOnCardSurface() {
        assertTrue(
            meetsWcagAaNormalText(
                MasroofDarkColors.OnSurface,
                MasroofDarkColors.SurfaceContainer,
            ),
        )
    }

    @Test
    fun darkSpending_supportsLargeTextOnSoftBackground() {
        val dark = darkExtendedColors()
        assertTrue(meetsWcagAaLargeText(dark.outflow, dark.outflowSoft))
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val (darker, lighter) = listOf(foreground.luminance(), background.luminance()).sorted()
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun meetsWcagAaNormalText(foreground: Color, background: Color) = contrastRatio(foreground, background) >= 4.5

    private fun meetsWcagAaLargeText(foreground: Color, background: Color) = contrastRatio(foreground, background) >= 3.0
}
