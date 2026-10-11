package com.baraa.masroof.presentation.common

import com.baraa.masroof.presentation.theme.MasroofComponentShapes
import com.baraa.masroof.presentation.theme.MasroofFontWeights
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.baraa.masroof.presentation.theme.MasroofBadgeShape
import com.baraa.masroof.presentation.theme.MasroofCardShape
import com.baraa.masroof.presentation.theme.MasroofIconSizes
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun MasroofCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(MasroofSpacing.cardInnerPadding + MasroofSpacing.borderWidth),
    content: @Composable ColumnScope.() -> Unit,
) {
    val extended = MasroofThemeExtras.extendedColors
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = MasroofCardShape,
        colors = CardDefaults.cardColors(
            containerColor = extended.cardSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MasroofSpacing.none,
            pressedElevation = MasroofSpacing.none,
            focusedElevation = MasroofSpacing.none,
            hoveredElevation = MasroofSpacing.none,
            draggedElevation = MasroofSpacing.none,
            disabledElevation = MasroofSpacing.none,
        ),
        border = androidx.compose.foundation.BorderStroke(MasroofSpacing.borderWidth, extended.cardBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}

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

@Suppress("UNUSED_PARAMETER")
@Composable
fun MasroofCompactCard(
    label: String,
    value: String,
    valueColor: Color,
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = MasroofSpacing.compactCardContentPadding,
    clickable: Boolean = false,
    onClick: (() -> Unit)? = null,
    guidance: String? = null,
) {
    MasroofMetric(label, value, valueColor,
        modifier = modifier.then(if (clickable && onClick != null) Modifier.clickable(onClick = onClick) else Modifier), guidance = guidance)
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
