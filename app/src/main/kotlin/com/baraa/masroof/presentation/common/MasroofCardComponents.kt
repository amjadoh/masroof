package com.baraa.masroof.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.baraa.masroof.ui.theme.MasroofBadgeShape
import com.baraa.masroof.ui.theme.MasroofCardShape
import com.baraa.masroof.ui.theme.MasroofSpacing
import com.baraa.masroof.ui.theme.MasroofThemeExtras

@Composable
fun MasroofCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(MasroofSpacing.cardInnerPadding + MasroofSpacing.borderWidth),
    content: @Composable ColumnScope.() -> Unit,
) = com.baraa.masroof.ui.components.MasroofCard(modifier, contentPadding, content)

@Composable
fun MasroofBadge(
    text: String,
    accent: MasroofCardAccent,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    val (background, foreground) = when (accent) {
        MasroofCardAccent.Account -> extended.accountSoft to extended.account
        MasroofCardAccent.Credit -> extended.cardSoft to extended.card
        MasroofCardAccent.Liability -> extended.liabilitySoft to extended.liability
        MasroofCardAccent.Inflow -> extended.inflowSoft to extended.inflow
        MasroofCardAccent.Outflow -> extended.outflowSoft to extended.outflow
        MasroofCardAccent.None -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = MasroofBadgeShape,
        color = background,
    ) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = MasroofSpacing.badgeHorizontalPadding,
                vertical = MasroofSpacing.badgeVerticalPadding,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasroofNavCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extended = MasroofThemeExtras.extendedColors
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth(),
        shape = MasroofCardShape,
        colors = CardDefaults.cardColors(
            containerColor = extended.navCardSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = MasroofSpacing.none),
        border = androidx.compose.foundation.BorderStroke(MasroofSpacing.borderWidth, extended.navCardBorder),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = MasroofSpacing.navCardHorizontalPadding,
                vertical = MasroofSpacing.navCardVerticalPadding,
            ),
            content = content,
        )
    }
}
