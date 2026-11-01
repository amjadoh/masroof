package com.baraa.masroof.presentation.common

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofIconSizes
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.launch

/** Destructive confirmation: the user copies the exact [name] and pastes it before Delete is enabled. */
@Composable
fun MasroofTypeToConfirmDeleteDialog(
    title: String,
    help: String,
    name: String,
    identity: String,
    nameLabel: String,
    copyLabel: String,
    confirmationLabel: String,
    deleteLabel: String,
    testTagPrefix: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    busy: Boolean = false,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    var confirmation by remember(identity) { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val cancel = stringResource(R.string.settings_cancel)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
                Icon(MasroofIcons.delete, null, Modifier.size(MasroofIconSizes.md), tint = MaterialTheme.colorScheme.error)
                Text(title)
            }
        },
        text = {
            val keyboard = LocalSoftwareKeyboardController.current
            val focus = LocalFocusManager.current
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(MasroofSpacing.smallGap)) {
                Text(help)
                extra()
                OutlinedTextField(name, {}, readOnly = true, singleLine = true,
                    label = { Text(nameLabel) }, trailingIcon = {
                        IconButton({ scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(name, name))) } }, modifier = Modifier.testTag("$testTagPrefix-copy")) {
                            Icon(MasroofIcons.copy, copyLabel, Modifier.size(MasroofIconSizes.md))
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("$testTagPrefix-name"))
                OutlinedTextField(confirmation, { confirmation = it }, singleLine = true,
                    label = { Text(confirmationLabel) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag("$testTagPrefix-confirmation"))
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
                MasroofOutlinedButton(onDismiss, enabled = !busy, modifier = Modifier.weight(1f).testTag("$testTagPrefix-cancel")) { Text(cancel) }
                MasroofButton(onDelete, enabled = !busy && confirmation == name, modifier = Modifier.weight(1f).testTag("$testTagPrefix-confirm"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                    Icon(MasroofIcons.delete, null, Modifier.size(MasroofIconSizes.md))
                    Spacer(Modifier.width(MasroofSpacing.inlineGap))
                    Text(deleteLabel)
                }
            }
        })
}
