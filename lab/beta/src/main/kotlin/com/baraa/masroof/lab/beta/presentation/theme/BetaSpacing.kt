package com.baraa.masroof.lab.beta.presentation.theme

import androidx.compose.ui.unit.dp

/** Neutral spacing mirrors MasroofSpacing; Beta cannot depend on the production UI module. */
internal object BetaSpacing {
    val screen = com.baraa.masroof.ui.theme.MasroofSpacing.screenHorizontal
    val section = com.baraa.masroof.ui.theme.MasroofSpacing.bottomSheetSectionGap
    val badgeHorizontal = com.baraa.masroof.ui.theme.MasroofSpacing.badgeHorizontalPadding
    val badgeVertical = com.baraa.masroof.ui.theme.MasroofSpacing.badgeVerticalPadding
}
