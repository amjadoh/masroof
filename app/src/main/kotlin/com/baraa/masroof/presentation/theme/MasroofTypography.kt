package com.baraa.masroof.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun masroofTextStyle(
    weight: FontWeight,
    fontSize: Int,
    lineHeight: Int,
) = TextStyle(
    fontFamily = MasroofFontFamily,
    fontWeight = weight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
)

val MasroofTypography = Typography(
    displaySmall = masroofTextStyle(MasroofFontWeights.bold, 30, 38),
    displayMedium = masroofTextStyle(MasroofFontWeights.bold, 34, 42),
    displayLarge = masroofTextStyle(MasroofFontWeights.bold, 38, 46),
    titleLarge = masroofTextStyle(MasroofFontWeights.bold, 22, 30),
    headlineLarge = masroofTextStyle(MasroofFontWeights.bold, 32, 40),
    headlineSmall = masroofTextStyle(MasroofFontWeights.bold, 18, 24),
    headlineMedium = masroofTextStyle(MasroofFontWeights.bold, 22, 28),
    titleMedium = masroofTextStyle(MasroofFontWeights.bold, 16, 22),
    titleSmall = masroofTextStyle(MasroofFontWeights.semiBold, 14, 20),
    bodyLarge = masroofTextStyle(MasroofFontWeights.normal, 16, 24),
    bodyMedium = masroofTextStyle(MasroofFontWeights.normal, 14, 20),
    bodySmall = masroofTextStyle(MasroofFontWeights.normal, 12, 18),
    labelLarge = masroofTextStyle(MasroofFontWeights.semiBold, 14, 18),
    labelMedium = masroofTextStyle(MasroofFontWeights.medium, 12, 16),
    labelSmall = masroofTextStyle(MasroofFontWeights.semiBold, 11, 14),
)
