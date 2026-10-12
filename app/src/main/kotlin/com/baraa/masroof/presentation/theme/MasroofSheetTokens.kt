package com.baraa.masroof.presentation.theme

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
    const val windowHeightFraction = 0.94f
}
