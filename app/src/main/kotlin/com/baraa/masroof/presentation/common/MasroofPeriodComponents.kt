package com.baraa.masroof.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.baraa.masroof.presentation.common.MasroofTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.theme.*

@Composable
fun MasroofPeriodPill(label: String, onPrevious: () -> Unit, onNext: () -> Unit,
    modifier: Modifier = Modifier, previousContentDescription: String? = null,
    nextContentDescription: String? = null, onCustomize: (() -> Unit)? = null,
    customizeLabel: String? = null, guidance: String? = null) {
    var open by remember { mutableStateOf(false) }
    Surface(onClick = { open = true }, modifier = modifier.padding(bottom = MasroofSpacing.inlineGap).fillMaxWidth(), shape = MasroofShapes.small,
        border = BorderStroke(MasroofSpacing.borderWidth, MasroofThemeExtras.extendedColors.cardBorder)) {
        ReferencePeriodContent(label, guidance)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { MasroofText(label) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
                TextButton(onClick = { onPrevious(); open = false }) { MasroofText(previousContentDescription.orEmpty()) }
                TextButton(onClick = { onNext(); open = false }) { MasroofText(nextContentDescription.orEmpty()) }
            }
        }, confirmButton = {
            if (onCustomize != null && customizeLabel != null) TextButton(onClick = { open = false; onCustomize() }) { MasroofText(customizeLabel) }
        }, dismissButton = { TextButton(onClick = { open = false }) { MasroofText(stringResource(R.string.settings_cancel)) } })
}

@Composable
fun MasroofPeriodDisplay(label: String, modifier: Modifier = Modifier, guidance: String? = null) {
    Surface(modifier.padding(bottom = MasroofSpacing.inlineGap).fillMaxWidth(), shape = MasroofShapes.small,
        border = BorderStroke(MasroofSpacing.borderWidth, MasroofThemeExtras.extendedColors.cardBorder)) {
        ReferencePeriodContent(label, guidance)
    }
}

@Composable
private fun ReferencePeriodContent(label: String, guidance: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = MasroofSpacing.periodDisplayHorizontalPadding + MasroofSpacing.borderWidth,
        vertical = MasroofReference.navigationItemVertical + MasroofSpacing.borderWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Icon(MasroofOutlineIcons.CalendarDays, null, Modifier.size(MasroofReference.icon))
        MasroofText(label, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            MasroofText(stringResource(R.string.dashboard_salary_period_short), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            guidance?.let { MasroofGuidanceButton(label, it) }
        }
    }
}
