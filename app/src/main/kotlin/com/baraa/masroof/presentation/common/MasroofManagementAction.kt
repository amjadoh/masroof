package com.baraa.masroof.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.ui.theme.MasroofShapes
import com.baraa.masroof.ui.theme.MasroofSpacing
import com.baraa.masroof.ui.theme.MasroofThemeExtras

@Composable
fun MasroofManagementAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, danger: Boolean = false, warning: Boolean = false) {
    Surface(onClick = onClick, modifier = modifier, enabled = enabled, shape = MasroofShapes.extraSmall,
        border = BorderStroke(MasroofSpacing.borderWidth, MasroofThemeExtras.extendedColors.cardBorder),
        contentColor = when {
            danger -> MasroofThemeExtras.extendedColors.outflow
            warning -> MasroofThemeExtras.extendedColors.liability
            else -> MaterialTheme.colorScheme.primary
        }) {
        MasroofText(label, Modifier.padding(horizontal = MasroofSpacing.rowHorizontalPadding,
            vertical = MasroofSpacing.rowVerticalPadding), style = MaterialTheme.typography.bodySmall)
    }
}
