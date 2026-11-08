package com.baraa.masroof.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Physical-card face used by the credit card companion. A card looks the same in light and
 * dark themes, so these colors are intentionally theme-independent.
 */
object MasroofCardFace {
    val ink = Color(0xFF143E43)
    val inkDeep = Color(0xFF0D2D31)
    val gold = Color(0xFFE8BB72)
    val onInk = Color(0xFFFFFFFF)
    val onInkMuted = Color(0xB3FFFFFF)
    val onInkFaint = Color(0x40FFFFFF)
    val usedShade = Color(0x1AFFFFFF)

    const val aspectRatio = 1.586f
    val corner = 22.dp
    val padding = 20.dp
    val chipWidth = 34.dp
    val chipHeight = 26.dp
    val dot = 7.dp
    val dotGap = 4.dp
    val monthCell = 10.dp
    val monthBorder = 2.dp
    val compositionBar = 10.dp
    val legendDot = 8.dp
}
