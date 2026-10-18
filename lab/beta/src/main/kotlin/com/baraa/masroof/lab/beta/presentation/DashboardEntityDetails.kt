package com.baraa.masroof.lab.beta.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.baraa.masroof.ui.components.MasroofCard
import com.baraa.masroof.ui.theme.*

/** Beta-only detail layout using the unchanged shared production display primitives. */
@Composable
internal fun DashboardEntityDetail(modifier: Modifier = Modifier, hero: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        hero()
        MasroofCard { content() }
    }
}

@Composable
internal fun DashboardDetailRow(label: @Composable (Modifier) -> Unit, value: String, modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface, dense: Boolean = false, divider: Boolean = true) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = if (dense) MasroofReference.rowGap else MasroofReference.rowVertical,
            bottom = if (dense) MasroofSpacing.none else MasroofReference.rowVertical),
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap), verticalAlignment = Alignment.CenterVertically) {
            label(Modifier.weight(1f))
            Text(value, Modifier.weight(1f), style = (if (dense) MaterialTheme.typography.bodySmall else MasroofReference.pair)
                .copy(fontWeight = MasroofFontWeights.semiBold), textAlign = TextAlign.End, color = valueColor)
        }
        if (divider && !dense) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
