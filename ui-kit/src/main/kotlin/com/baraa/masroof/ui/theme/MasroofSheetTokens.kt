package com.baraa.masroof.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Geometry shared by every modal sheet, measured against the available app window. */
object MasroofSheetTokens {
    val maxWidth = 560.dp
    val twoColumnContentWidth = 480.dp
    val horizontalPadding = MasroofSpacing.screenHorizontal
    val sectionGap = MasroofSpacing.bottomSheetSectionGap
    val choiceMinHeight = 48.dp
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val handleWidth = 32.dp
    val handleHeight = 4.dp
    val dismissDragDistance = 80.dp
    const val scrimAlpha = 0.32f
    const val motionMillis = 180
    const val windowHeightFraction = 0.94f
}
