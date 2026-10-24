package com.baraa.masroof.presentation.common

import com.baraa.masroof.ui.theme.MasroofSpacing
import com.baraa.masroof.ui.theme.MasroofSizes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.common.MasroofTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.dashboard.SmsRescanStatus
import com.baraa.masroof.ui.theme.MasroofThemeExtras

@Composable
fun SmsRescanStatusNotice(
    status: SmsRescanStatus,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isError = status == SmsRescanStatus.FAILED || status == SmsRescanStatus.PERMISSION_DENIED
    val extended = MasroofThemeExtras.extendedColors
    MasroofCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isError) MasroofIcons.error else MasroofIcons.rescan,
                    contentDescription = null,
                    tint = if (isError) extended.outflow else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(MasroofSizes.size18),
                )
                Text(
                    smsRescanStatusMessage(status),
                    modifier = Modifier.padding(start = MasroofSpacing.cardInnerGap),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dashboard_rescan_dismiss))
            }
        }
    }
}

@Composable
internal fun smsRescanStatusMessage(status: SmsRescanStatus): String =
    stringResource(
        when (status) {
            SmsRescanStatus.OK -> R.string.dashboard_rescan_ok
            SmsRescanStatus.ALREADY_UP_TO_DATE -> R.string.dashboard_rescan_already_up_to_date
            SmsRescanStatus.NEEDS_REVIEW -> R.string.dashboard_rescan_needs_review
            SmsRescanStatus.NO_MESSAGES -> R.string.dashboard_rescan_no_messages
            SmsRescanStatus.NO_BANK_SMS -> R.string.dashboard_rescan_no_bank_sms
            SmsRescanStatus.NO_TRANSACTIONS -> R.string.dashboard_rescan_no_transactions
            SmsRescanStatus.PERMISSION_DENIED -> R.string.dashboard_rescan_permission_denied
            SmsRescanStatus.FAILED -> R.string.dashboard_rescan_failed
        },
    )
