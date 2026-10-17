package com.baraa.masroof.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.ui.theme.MasroofCardShape
import com.baraa.masroof.ui.theme.MasroofSpacing
import com.baraa.masroof.ui.theme.MasroofThemeExtras

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
