package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofOpacity
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofComponentShapes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.application.dashboard.DashboardSectionSize
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun Modifier.dashboardSectionFrame(
    size: DashboardSectionSize,
    editing: Boolean,
): Modifier {
    val extended = MasroofThemeExtras.extendedColors
    val padding = when (size) {
        DashboardSectionSize.SMALL -> MasroofSpacing.none
        DashboardSectionSize.MEDIUM -> MasroofSpacing.none
        DashboardSectionSize.LARGE -> MasroofSpacing.hairlineGap
    }
    val editBorder = if (editing) {
        Modifier.border(MasroofSpacing.borderWidth, extended.inflow.copy(alpha = MasroofOpacity.subtle), MasroofComponentShapes.radius16)
    } else {
        Modifier
    }
    return this
        .padding(vertical = padding)
        .then(editBorder)
}
