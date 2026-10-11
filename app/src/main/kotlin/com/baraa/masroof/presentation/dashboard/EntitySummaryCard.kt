package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofIconSizes

@Composable
internal fun EntitySummaryCard(title: String, subtitle: String, icon: ImageVector, accent: Color,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, leading: (@Composable () -> Unit)? = null,
    guidance: String? = null,
    bankLabel: String? = null,
    actionLabel: String = androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.reference_details),
    content: @Composable ColumnScope.() -> Unit) {
    MasroofCard(modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
            androidx.compose.material3.Surface(shape = com.baraa.masroof.presentation.theme.MasroofShapes.small,
                color = when (accent) {
                    com.baraa.masroof.presentation.theme.MasroofThemeExtras.extendedColors.card -> com.baraa.masroof.presentation.theme.MasroofThemeExtras.extendedColors.cardSoft
                    com.baraa.masroof.presentation.theme.MasroofThemeExtras.extendedColors.liability -> com.baraa.masroof.presentation.theme.MasroofThemeExtras.extendedColors.liabilitySoft
                    else -> com.baraa.masroof.presentation.theme.MasroofThemeExtras.extendedColors.accountSoft
                }) {
                Box(Modifier.size(com.baraa.masroof.presentation.theme.MasroofReference.iconBox), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(com.baraa.masroof.presentation.theme.MasroofReference.icon))
                }
            }
            Column(Modifier.weight(1f)) {
                com.baraa.masroof.presentation.common.MasroofGuidanceLabel(title, guidance,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = com.baraa.masroof.presentation.theme.MasroofFontWeights.semiBold), contentColor = MaterialTheme.colorScheme.onSurface)
            }
            bankLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(com.baraa.masroof.presentation.theme.MasroofReference.chartHeadGap))
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
        if (onClick != null) {
            Spacer(Modifier.height(MasroofSpacing.cardInnerGap))
            androidx.compose.material3.Button(onClick = onClick, modifier = Modifier.fillMaxWidth(),
                shape = com.baraa.masroof.presentation.theme.MasroofShapes.small) {
                Text(actionLabel)
            }
        }
    }
}
