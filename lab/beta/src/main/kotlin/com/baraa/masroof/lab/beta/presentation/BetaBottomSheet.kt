package com.baraa.masroof.lab.beta.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.presentation.theme.BetaSpacing

/** Isolated mirror of MasroofBottomSheet geometry: fixed actions, scrollable draft, adaptive width. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BetaBottomSheet(title: String, busy: Boolean, onCancel: () -> Unit,
    onApply: () -> Unit, canApply: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val currentBusy by rememberUpdatedState(busy)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !currentBusy })
    ModalBottomSheet(onDismissRequest = { if (!currentBusy) onCancel() }, sheetState = state,
        sheetMaxWidth = com.baraa.masroof.ui.theme.MasroofSheetTokens.maxWidth) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(com.baraa.masroof.ui.theme.MasroofSheetTokens.windowHeightFraction).testTag("dash-sheet")) {
            Text(title, Modifier.padding(BetaSpacing.screen), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(BetaSpacing.screen).testTag("dash-sheet-body"),
                verticalArrangement = Arrangement.spacedBy(BetaSpacing.section), content = content)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(BetaSpacing.screen), horizontalArrangement = Arrangement.spacedBy(BetaSpacing.section)) {
                OutlinedButton(enabled = !busy, onClick = onCancel, modifier = Modifier.weight(1f).testTag("dash-sheet-cancel")) { Text(stringResource(R.string.beta_controls_cancel)) }
                Button(enabled = !busy && canApply, onClick = onApply, modifier = Modifier.weight(1f).testTag("dash-sheet-apply")) { Text(stringResource(R.string.beta_controls_apply)) }
            }
        }
    }
}
