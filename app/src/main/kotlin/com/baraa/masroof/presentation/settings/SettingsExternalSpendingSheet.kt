package com.baraa.masroof.presentation.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofBottomSheet
import com.baraa.masroof.presentation.common.MasroofHintBox
import com.baraa.masroof.presentation.common.MasroofMoneyRow
import com.baraa.masroof.presentation.common.MasroofText
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate

@Composable
internal fun SettingsExternalSpendingSheet(state: SettingsUiState, onClose: () -> Unit) {
    val account = state.externalSpendingTarget ?: return
    MasroofBottomSheet(
        title = registryLabel(account), onDismiss = onClose, onApply = onClose,
        applyLabel = stringResource(R.string.settings_external_close),
        subtitle = stringResource(R.string.settings_external_spending),
    ) {
        MasroofHintBox(stringResource(R.string.settings_external_spending_hint))
        when {
            state.externalSpendingLoading -> MasroofText(stringResource(R.string.settings_external_loading))
            state.externalSpendingFailed -> MasroofText(stringResource(R.string.settings_update_failed), color = MaterialTheme.colorScheme.error)
            else -> state.externalSpending?.let { report ->
                report.totals.forEach { total ->
                    MasroofMoneyRow(stringResource(R.string.settings_external_gross), formatLocalizedMoney(total.purchasesAndFees))
                    MasroofMoneyRow(stringResource(R.string.settings_external_refunds), formatLocalizedMoney(total.refunds))
                    MasroofMoneyRow(stringResource(R.string.settings_external_net), formatLocalizedMoney(total.net))
                }
                if (report.unconfirmedCount > 0) MasroofHintBox(
                    stringResource(R.string.settings_external_pending, report.unconfirmedCount),
                )
                if (report.rows.isEmpty()) MasroofText(stringResource(R.string.settings_external_empty))
                report.rows.forEach { row ->
                    val date = row.date?.let { formatLocalizedTransactionDate(it, "d MMM yyyy") }
                    val label = listOfNotNull(date, row.merchant,
                        if (row.refund) stringResource(R.string.settings_external_refunds) else null)
                        .joinToString(" · ").ifEmpty { stringResource(R.string.settings_external_gross) }
                    MasroofMoneyRow(label, formatLocalizedMoney(row.amount))
                }
            }
        }
    }
}
