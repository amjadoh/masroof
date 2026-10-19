package com.baraa.masroof.lab.beta.presentation

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.R

@Composable
internal fun BetaReviewInboxPanel(container: BetaContainer, onClose: () -> Unit,
    onBusyChanged: (Boolean) -> Unit, onApprove: (String) -> Unit, onConfirmAccounts: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    fun updateBusy(value: Boolean) { busy = value; onBusyChanged(value) }
    var corrections by rememberSaveable { mutableStateOf(false) }
    if (corrections) BetaReviewPanel(container, onClose = { corrections = false }, onBusyChanged = ::updateBusy)
    else {
        Text(stringResource(R.string.beta_history_filter_review), style = MaterialTheme.typography.headlineSmall)
        BetaHistoryPanel(container, onClose, ::updateBusy, onFinancialReview = onApprove, reviewOnly = true, onConfirmAccounts = onConfirmAccounts)
        OutlinedButton(enabled = !busy, onClick = { corrections = true }, modifier = Modifier.testTag("review-existing-transactions")) { Text(stringResource(R.string.beta_setup_edit_transactions)) }
    }
}
