package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofNetworkColors
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofSizes
import com.baraa.masroof.presentation.theme.MasroofFontWeights
import com.baraa.masroof.presentation.theme.MasroofComponentShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.baraa.masroof.domain.model.CardNetwork

@Composable
fun CardNetworkBadge(
    network: CardNetwork?,
    last4: String,
    modifier: Modifier = Modifier,
) {
    val colors = networkColors(network, last4)
    Box(
        modifier = modifier
            .size(width = MasroofSizes.size36, height = MasroofSpacing.screenPaddingLarge)
            .clip(MasroofComponentShapes.radius6)
            .background(brush = Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            networkAbbreviation(network),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = MasroofFontWeights.bold),
            color = MasroofNetworkColors.onBadge,
        )
    }
}

private fun networkColors(network: CardNetwork?, last4: String): List<Color> =
    when (network) {
        CardNetwork.MADA -> listOf(MasroofNetworkColors.madaStart, MasroofNetworkColors.madaEnd)
        CardNetwork.VISA -> listOf(MasroofNetworkColors.visa, MasroofNetworkColors.visa)
        CardNetwork.MASTERCARD -> listOf(MasroofNetworkColors.mastercardRed, MasroofNetworkColors.mastercardOrange)
        CardNetwork.AMEX -> listOf(MasroofNetworkColors.amex, MasroofNetworkColors.amex)
        CardNetwork.UNKNOWN, null -> listOf(MasroofNetworkColors.neutral, MasroofNetworkColors.neutral)
    }

private fun networkAbbreviation(network: CardNetwork?): String =
    when (network) {
        CardNetwork.MADA -> "MD"
        CardNetwork.VISA -> "VI"
        CardNetwork.MASTERCARD -> "MC"
        CardNetwork.AMEX -> "AX"
        CardNetwork.UNKNOWN, null -> "••"
    }
