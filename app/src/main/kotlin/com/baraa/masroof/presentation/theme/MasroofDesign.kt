package com.baraa.masroof.presentation.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

/** Entry point for the user-approved design; values live only in this package. */
object MasroofDesign {
    const val version = "approved-2026-10-10"
    val palette = MasroofPalette
    val lightColors = MasroofLightColors
    val darkColors = MasroofDarkColors
    val fontFamily = MasroofFontFamily
    val componentShapes = MasroofComponentShapes
    val weights = MasroofFontWeights
    val opacity = MasroofOpacity
    val spacing = MasroofSpacing
    val sizes = MasroofSizes
    val typography = MasroofTypography
    val shapes = MasroofShapes
    val iconSizes = MasroofIconSizes
    val elevation = MasroofElevation
}

/** Component geometry outside the spacing rhythm (charts, sheets and badges). */
object MasroofSizes {
    val size5 = 5.dp
    val size7 = 7.dp
    val size18 = 18.dp
    val size22 = 22.dp
    val size28 = 28.dp
    val size36 = 36.dp
    val size56 = 56.dp
    val size64 = 64.dp
    val size72 = 72.dp
    val size92 = 92.dp
    val size180 = 180.dp
}

object MasroofFontWeights {
    val normal = FontWeight.Normal
    val medium = FontWeight.Medium
    val semiBold = FontWeight.SemiBold
    val bold = FontWeight.Bold
}

object MasroofOpacity {
    const val tint = 0.12f
    const val badge = 0.14f
    const val divider = 0.25f
    const val subtle = 0.35f
    const val disabled = 0.38f
    const val muted = 0.45f
    const val emphasized = 0.55f
}
