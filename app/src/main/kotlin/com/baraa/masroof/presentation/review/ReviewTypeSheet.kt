package com.baraa.masroof.presentation.review

import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.*

/** A local draft; only Apply invokes the existing resolution workflow. */
@Composable
internal fun ReviewTypeSheet(busy: Boolean, onDismiss: () -> Unit, onSave: (FinancialTransactionType) -> Unit, error: String? = null) {
    var selected by remember { mutableStateOf<FinancialTransactionType?>(null) }
    MasroofBottomSheet(
        title = stringResource(R.string.teaching_correct_type), onDismiss = onDismiss,
        onApply = { selected?.let(onSave) }, applyLabel = stringResource(R.string.transaction_reclassify_sheet_save),
        canApply = selected != null, busy = busy,
    ) {
        error?.let { MasroofText(it, color = MaterialTheme.colorScheme.error) }
        MasroofGuidanceLabel(stringResource(R.string.teaching_correct_type), stringResource(R.string.teaching_correct_type_help))
        MasroofSheetChoices(REVIEW_RESOLUTION_TYPE_ACTIONS, setOfNotNull(selected),
            label = { stringResource(it.toUiLabelRes()) }, onToggle = { selected = it },
            optionKey = { reviewResolveTypeTestTag(it) })
    }
}
