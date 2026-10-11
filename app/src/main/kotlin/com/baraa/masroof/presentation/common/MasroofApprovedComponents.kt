package com.baraa.masroof.presentation.common

import com.baraa.masroof.presentation.theme.MasroofOpacity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.baraa.masroof.presentation.theme.MasroofCardShape
import com.baraa.masroof.presentation.theme.MasroofHeroRole
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.approvedHeroColors

/** Shared hero, amount rows and spacing for every account/card/loan detail. */
@Composable
fun MasroofHeroCard(
    title: String,
    value: String,
    subtitle: String? = null,
    role: MasroofHeroRole = MasroofHeroRole.Brand,
    modifier: Modifier = Modifier,
    guidance: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val (background, foreground) = approvedHeroColors(role)
    Surface(modifier.fillMaxWidth(), shape = MasroofCardShape, color = background, contentColor = foreground) {
        Column(Modifier.padding(MasroofSpacing.largeGap), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
            MasroofGuidanceLabel(title, guidance, style = MaterialTheme.typography.bodyMedium, contentColor = foreground)
            Text(value, style = MaterialTheme.typography.displayMedium, color = foreground)
            subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = foreground) }
            if (footer != null) {
                HorizontalDivider(color = foreground.copy(alpha = MasroofOpacity.divider))
                footer()
            }
        }
    }
}

@Composable
fun MasroofDetailRow(label: String, value: String, modifier: Modifier = Modifier, guidance: String? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = MasroofSpacing.rowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MasroofGuidanceLabel(label, guidance, Modifier.weight(1f))
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
