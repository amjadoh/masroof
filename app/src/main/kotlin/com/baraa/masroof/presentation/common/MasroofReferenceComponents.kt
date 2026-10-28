package com.baraa.masroof.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.baraa.masroof.ui.theme.*

@Composable
fun MasroofMetric(label: String, value: String, color: Color,
    modifier: Modifier = Modifier, guidance: String? = null) {
    com.baraa.masroof.ui.dashboard.DashboardMetric(value, color, modifier) {
        MasroofGuidanceLabel(label, guidance)
    }
}

@Composable
fun MasroofAverageBand(value: String, periodHint: String, guidance: String?,
    modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MasroofReference.selectedNavigationColor(),
        shape = MasroofShapes.small) {
        Row(Modifier.padding(MasroofReference.contextualBandPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
                MasroofGuidanceLabel(androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.approved_daily_average),
                    guidance, style = MaterialTheme.typography.bodySmall.copy(fontWeight = MasroofFontWeights.semiBold),
                    contentColor = MaterialTheme.colorScheme.onSurface)
                MasroofText(periodHint, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MasroofText(value.substringBeforeLast(' '), style = MasroofReference.averageValue)
                MasroofText(androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.reference_average_unit,
                    value.substringAfterLast(' ')), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MasroofReferenceNavigationRow(title: String, subtitle: String, icon: ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier, divider: Boolean = true,
    status: String? = null, showChevron: Boolean = true) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
    // Narrow rows stack the badge under the subtitle so neither the title nor the status is squeezed.
    val stackStatus = maxWidth < MasroofReference.inlineStatusMinWidth
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(top = MasroofReference.rowVertical,
            bottom = if (divider) MasroofReference.rowVertical else MasroofReference.lastRowBottom),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofReference.rowGap)) {
            Surface(shape = MasroofShapes.small, color = MasroofThemeExtras.extendedColors.accountSoft) {
                Box(Modifier.size(MasroofReference.iconBox), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(MasroofReference.icon), tint = MasroofThemeExtras.extendedColors.account)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MasroofReference.metadataGap)) {
                MasroofText(title, style = MasroofReference.pair.copy(fontWeight = MasroofFontWeights.semiBold))
                MasroofText(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (stackStatus) status?.let { MasroofBadge(it, MasroofCardAccent.Liability) }
            }
            if (!stackStatus) status?.let { MasroofBadge(it, MasroofCardAccent.Liability, Modifier.widthIn(max = MasroofReference.statusBadgeMaxWidth)) }
            if (showChevron) Icon(if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl)
                MasroofIcons.periodPrevious else MasroofIcons.periodNext,
                null, Modifier.size(MasroofReference.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (divider) HorizontalDivider(color = MasroofThemeExtras.extendedColors.cardBorder)
    }
    }
}
