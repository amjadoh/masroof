package com.baraa.masroof.presentation.dashboard

import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CommitmentPaymentStatus
import com.baraa.masroof.application.dashboard.CommitmentDashboardSource
import com.baraa.masroof.application.dashboard.CommitmentsOverview
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.ui.theme.MasroofThemeExtras

@Composable
fun CommitmentsSection(overview: CommitmentsOverview, modifier: Modifier = Modifier,
    onOpenCredit: (() -> Unit)? = null) {
    if (!overview.hasContent) return
    val colors = MasroofThemeExtras.extendedColors
    val pending = overview.rows.filter { it.status == CommitmentPaymentStatus.UNPAID }
    val today = java.time.LocalDate.now()
    com.baraa.masroof.ui.dashboard.DashboardCommitmentBand(
        stringResource(R.string.reference_period_commitments),
        stringResource(R.string.reference_waiting_payment, formatLocalizedMoney(overview.remaining)),
        // The name is isolated so an English bill name never reorders the Arabic around it.
        pending.take(2).map { row -> com.baraa.masroof.application.locale.AppDigits.display("\u2068" + commitmentRowLabel(row) +
            "\u2069" + (row.daysUntil(today)?.let { " · " + dueCountdown(it) } ?: "")) },
        modifier, details = if (onOpenCredit != null && pending.any { it.source == CommitmentDashboardSource.CREDIT_CARD }) ({
            com.baraa.masroof.presentation.common.MasroofTextButton(onClick = onOpenCredit) {
                Text(stringResource(R.string.reference_card_details), style = MaterialTheme.typography.bodySmall, color = colors.liability)
            }
        }) else null)
}

/** Today, tomorrow, in N days, or N days late, with the right Arabic number form. */
@Composable
internal fun dueCountdown(days: Long): String = when {
    days == 0L -> stringResource(R.string.commitment_due_today)
    days == 1L -> stringResource(R.string.commitment_due_tomorrow)
    days > 0 -> pluralStringResource(R.plurals.commitment_due_in, days.toInt(), days.toInt())
    else -> pluralStringResource(R.plurals.commitment_due_late, (-days).toInt(), (-days).toInt())
}

/** Row title; an overdrawn account reads "حساب ••0400 بالسالب" instead of its bare number. */
@Composable
internal fun commitmentRowLabel(row: com.baraa.masroof.application.dashboard.CommitmentDashboardRow): String =
    if (row.source == CommitmentDashboardSource.ACCOUNT_OVERDRAFT) stringResource(R.string.commitment_overdraft_label, row.displayName)
    else row.displayName
