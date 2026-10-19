package com.baraa.masroof.lab.beta.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.baraa.masroof.ui.theme.*

/** Production MasroofChoice geometry and color semantics, with no dependency on :app. */
@Composable
internal fun BetaChoice(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, label: @Composable () -> Unit) {
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.semantics {this.selected=selected},
        shape=MasroofBadgeShape,
        color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor=if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border=BorderStroke(MasroofSpacing.borderWidth,if(selected) MaterialTheme.colorScheme.primary else MasroofThemeExtras.extendedColors.cardBorder)) {
        Row(Modifier.padding(horizontal=MasroofSpacing.periodDisplayHorizontalPadding,
            vertical=MasroofReference.shortcutGap+MasroofSpacing.borderWidth)) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium,label)
        }
    }
}
