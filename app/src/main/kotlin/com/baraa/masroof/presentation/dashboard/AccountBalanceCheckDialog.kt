package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.application.settings.AccountBalanceInput
import com.baraa.masroof.application.settings.BalanceCheckResult
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.presentation.common.MasroofGuidanceLabel
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate
import com.baraa.masroof.ui.theme.MasroofSpacing
import java.math.BigDecimal
import java.time.ZoneId
import com.baraa.masroof.presentation.common.MasroofFormField as OutlinedTextField
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.common.MasroofTextButton as TextButton

/** The account's balance check: type the bank's figure, then see whether the app agrees. */
@Composable
fun AccountBalanceCheckDialog(
    accountLabel: String,
    result: BalanceCheckResult?,
    onCheck: (BigDecimal) -> Unit,
    onAdoptBankBalance: (BigDecimal) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(accountLabel) { mutableStateOf("") }
    val parsed = AccountBalanceInput.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            MasroofGuidanceLabel(stringResource(R.string.balance_check_title, accountLabel),
                stringResource(R.string.balance_check_hint), style = MaterialTheme.typography.titleLarge,
                contentColor = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
                when (result) {
                    null -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(stringResource(R.string.balance_check_amount)) },
                            isError = text.isNotBlank() && parsed == null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth().testTag("balance-check-amount"),
                        )
                    }
                    BalanceCheckResult.Matched ->
                        Text(stringResource(R.string.balance_check_matched), style = MaterialTheme.typography.bodyMedium)
                    BalanceCheckResult.Started ->
                        Text(stringResource(R.string.balance_check_started), style = MaterialTheme.typography.bodyMedium)
                    is BalanceCheckResult.Differs -> DifferenceDetails(result)
                }
            }
        },
        confirmButton = {
            when (result) {
                null -> TextButton(onClick = { parsed?.let(onCheck) }, enabled = parsed != null,
                    modifier = Modifier.testTag("balance-check-submit")) { Text(stringResource(R.string.balance_check_submit)) }
                is BalanceCheckResult.Differs -> TextButton(onClick = { onAdoptBankBalance(result.bankBalance) },
                    modifier = Modifier.testTag("balance-check-adopt")) { Text(stringResource(R.string.balance_check_adopt)) }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.balance_check_done)) }
            }
        },
        dismissButton = {
            if (result !is BalanceCheckResult.Matched && result !is BalanceCheckResult.Started) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(if (result == null) R.string.settings_cancel else R.string.balance_check_keep))
                }
            }
        },
    )
}

@Composable
private fun DifferenceDetails(result: BalanceCheckResult.Differs) {
    val since = result.since.atZone(ZoneId.systemDefault()).toLocalDate()
    Text(
        stringResource(
            if (result.difference.signum() > 0) R.string.balance_check_app_higher else R.string.balance_check_app_lower,
            sar(result.difference.abs()),
            formatLocalizedTransactionDate(since, "d MMMM yyyy"),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(stringResource(R.string.balance_check_figures, sar(result.appBalance), sar(result.bankBalance)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val hints = result.hints
    if (hints.unconfirmedOtpCount > 0) {
        Text(stringResource(R.string.balance_check_hint_otp, hints.unconfirmedOtpCount, sar(hints.unconfirmedOtpTotal)),
            style = MaterialTheme.typography.bodyMedium)
    }
    if (hints.pendingReviewCount > 0) {
        Text(stringResource(R.string.balance_check_hint_review, hints.pendingReviewCount),
            style = MaterialTheme.typography.bodyMedium)
    }
    MasroofGuidanceLabel(stringResource(R.string.balance_check_next_step), stringResource(R.string.balance_check_statement_hint))
}

@Composable
private fun sar(amount: BigDecimal): String = formatLocalizedMoney(SignedMoneyAmount(amount, Currency.SAR))
