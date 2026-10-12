package com.baraa.masroof.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.theme.*

/** Header and actions never scroll; only the body does. All dismissal paths share one policy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasroofBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    applyLabel: String,
    subtitle: String? = null,
    canApply: Boolean = true,
    busy: Boolean = false,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Dialogs install their own Android configuration; preserve the app's selected locale.
    val parentContext = LocalContext.current
    val parentConfiguration = LocalConfiguration.current
    val parentDirection = LocalLayoutDirection.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { !busy || it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState,
        modifier = Modifier.widthIn(max = MasroofSheetTokens.maxWidth),
        shape = MasroofSheetTokens.shape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = MasroofElevation.sheetTonal,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        CompositionLocalProvider(LocalContext provides parentContext, LocalConfiguration provides parentConfiguration,
            LocalLayoutDirection provides parentDirection) {
        BoxWithConstraints {
            // Mark the positioned content, after Material applies the sheet anchor offset.
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * MasroofSheetTokens.windowHeightFraction)
                .testTag("masroof-bottom-sheet")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = MasroofSheetTokens.horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MasroofText(title, style = MaterialTheme.typography.titleMedium, fontWeight = MasroofFontWeights.bold)
                        subtitle?.let { MasroofText(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = onDismiss, enabled = !busy,
                        modifier = Modifier.testTag("sheet-close")) {
                        Icon(Icons.Default.Close, stringResource(R.string.sheet_close_without_saving), Modifier.size(MasroofIconSizes.md))
                    }
                }
                Column(Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = MasroofSheetTokens.horizontalPadding,
                        vertical = MasroofSheetTokens.sectionGap).testTag("sheet-body"),
                    verticalArrangement = Arrangement.spacedBy(MasroofSheetTokens.sectionGap), content = content)
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = MasroofSheetTokens.horizontalPadding, vertical = MasroofSpacing.sectionHeaderGap)
                    .testTag("sheet-actions")) {
                    if (onReset != null && resetLabel != null) MasroofTextButton(onClick = onReset, enabled = !busy,
                        modifier = Modifier.testTag("sheet-reset")) { MasroofText(resetLabel) }
                    Row(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
                        MasroofOutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)
                            .heightIn(min = MasroofSpacing.minimumTouchTarget).testTag("sheet-cancel"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                            MasroofText(stringResource(R.string.sheet_cancel))
                        }
                        MasroofButton(onClick = onApply, enabled = canApply && !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = MasroofPalette.Primary,
                                contentColor = MasroofLightColors.OnPrimary),
                            modifier = Modifier.weight(1f).heightIn(min = MasroofSpacing.minimumTouchTarget).testTag("sheet-apply")) {
                            MasroofText(applyLabel)
                        }
                    }
                }
            }
        }
        }
    }
}
