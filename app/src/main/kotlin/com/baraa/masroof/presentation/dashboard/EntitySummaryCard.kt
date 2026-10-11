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
    guidance: String? = null, content: @Composable ColumnScope.() -> Unit) {
    MasroofCard(modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
            if (leading != null) leading() else Icon(icon, null, tint = accent, modifier = Modifier.size(MasroofIconSizes.logo))
            Column(Modifier.weight(1f)) {
                com.baraa.masroof.presentation.common.MasroofGuidanceLabel(title, guidance,
                    style = MaterialTheme.typography.titleMedium, contentColor = MaterialTheme.colorScheme.onSurface)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Icon(MasroofIcons.periodNext, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(MasroofSpacing.cardInnerGap))
        content()
    }
}
