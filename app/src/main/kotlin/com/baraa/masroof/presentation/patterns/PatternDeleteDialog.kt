package com.baraa.masroof.presentation.patterns

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.common.MasroofOutlinedButton
import com.baraa.masroof.presentation.common.MasroofButton
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.ui.theme.MasroofIconSizes
import com.baraa.masroof.ui.theme.MasroofSpacing

@Composable
internal fun PatternDeleteDialog(name: String, identity: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var confirmation by remember(identity) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    // Dialog windows may expose a different Android context; resolve copy in the app locale before opening one.
    val title = stringResource(R.string.pattern_delete_title)
    val help = stringResource(R.string.pattern_delete_help)
    val copy = stringResource(R.string.pattern_delete_copy)
    val fieldLabel = stringResource(R.string.pattern_delete_confirmation)
    val nameLabel = stringResource(R.string.pattern_delete_name)
    val delete = stringResource(R.string.pattern_delete)
    val cancel = stringResource(R.string.settings_cancel)
    AlertDialog(onDismissRequest = onDismiss,
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
                OutlinedTextField(name, {}, readOnly = true, singleLine = true,
                    label = { Text(nameLabel) }, trailingIcon = {
                        IconButton({ clipboard.setText(AnnotatedString(name)) }, modifier = Modifier.testTag("pattern-delete-copy")) {
                            Icon(MasroofIcons.copy, copy, Modifier.size(MasroofIconSizes.md))
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("pattern-delete-name"))
                OutlinedTextField(confirmation, { confirmation = it }, singleLine = true,
                    label = { Text(fieldLabel) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag("pattern-delete-confirmation"))
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
                MasroofOutlinedButton(onDismiss, modifier = Modifier.weight(1f).testTag("pattern-delete-cancel")) { Text(cancel) }
                MasroofButton(onDelete, enabled = confirmation == name, modifier = Modifier.weight(1f).testTag("pattern-delete-confirm"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                    Icon(MasroofIcons.delete, null, Modifier.size(MasroofIconSizes.md))
                    Spacer(Modifier.width(MasroofSpacing.inlineGap))
                    Text(delete)
                }
            }
        })
}
