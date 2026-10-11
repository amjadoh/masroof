package com.baraa.masroof.presentation.common

import com.baraa.masroof.presentation.theme.MasroofOpacity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import com.baraa.masroof.presentation.theme.MasroofReference
import androidx.compose.ui.graphics.Color

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
        Column(Modifier.padding(MasroofReference.heroPadding)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
                MasroofGuidanceLabel(title, guidance, Modifier.weight(1f), style = MasroofReference.pair, contentColor = foreground)
                Icon(if (role == MasroofHeroRole.Card) MasroofIcons.cardPayment else MasroofIcons.appLogo,
                    null, Modifier.size(MasroofReference.icon), tint = foreground)
            }
            Spacer(Modifier.height(MasroofSpacing.carouselGap))
            MasroofReferenceMoney(value, style = MaterialTheme.typography.displayMedium, color = foreground)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(MasroofSpacing.inlineGap))
                Text(it, style = MaterialTheme.typography.labelSmall, color = foreground)
            }
            if (footer != null) {
                Spacer(Modifier.height(MasroofSpacing.cardInnerPadding))
                HorizontalDivider(color = foreground.copy(alpha = MasroofOpacity.divider))
                Spacer(Modifier.height(MasroofReference.shortcutVertical))
                footer()
            }
        }
    }
}

@Composable
fun MasroofDetailRow(label: String, value: String, modifier: Modifier = Modifier, guidance: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface, dense: Boolean = false, divider: Boolean = true) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = if (dense) MasroofReference.rowGap else MasroofReference.rowVertical,
                bottom = if (dense) MasroofSpacing.none else MasroofReference.rowVertical),
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MasroofGuidanceLabel(label, guidance, Modifier.weight(1f), style = if (dense) MaterialTheme.typography.bodySmall else MasroofReference.pair,
                contentColor = MaterialTheme.colorScheme.onSurface)
            Text(value, Modifier.weight(1f), style = (if (dense) MaterialTheme.typography.bodySmall else MasroofReference.pair).copy(fontWeight = com.baraa.masroof.presentation.theme.MasroofFontWeights.semiBold),
                textAlign = androidx.compose.ui.text.style.TextAlign.End, color = valueColor)
        }
        if (divider && !dense) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
