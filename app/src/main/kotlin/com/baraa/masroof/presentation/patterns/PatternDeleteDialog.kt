package com.baraa.masroof.presentation.patterns

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofTypeToConfirmDeleteDialog

@Composable
internal fun PatternDeleteDialog(name: String, identity: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    // Dialog windows may expose a different Android context; resolve copy in the app locale before opening one.
    MasroofTypeToConfirmDeleteDialog(
        title = stringResource(R.string.pattern_delete_title),
        help = stringResource(R.string.pattern_delete_help),
        name = name,
        identity = identity,
        nameLabel = stringResource(R.string.pattern_delete_name),
        copyLabel = stringResource(R.string.pattern_delete_copy),
        confirmationLabel = stringResource(R.string.pattern_delete_confirmation),
        deleteLabel = stringResource(R.string.pattern_delete),
        testTagPrefix = "pattern-delete",
        onDismiss = onDismiss,
        onDelete = onDelete,
    )
}
