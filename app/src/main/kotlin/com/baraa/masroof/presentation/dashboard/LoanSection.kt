package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofOpacity
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofSizes
import com.baraa.masroof.presentation.theme.MasroofComponentShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.LoanOverview
import com.baraa.masroof.presentation.common.MasroofHeroCard
import com.baraa.masroof.presentation.common.MasroofDetailRow
import com.baraa.masroof.presentation.theme.MasroofHeroRole
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LoanIdentityBadge(
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    Box(
        modifier = modifier
            .size(width = MasroofSizes.size36, height = MasroofSpacing.screenPaddingLarge)
            .clip(MasroofComponentShapes.radius6)
            .background(extended.liability.copy(alpha = MasroofOpacity.tint)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MasroofIcons.savings,
            contentDescription = null,
            tint = extended.liability,
            modifier = Modifier.size(MasroofSpacing.compactCardContentPadding),
        )
    }
}

@Composable
fun LoanSummaryTile(loan: LoanOverview, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    showNavigationIcon: Boolean = onClick != null) {
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", LocalConfiguration.current.locales[0])
    val unavailable = stringResource(R.string.dashboard_value_unavailable)
    EntitySummaryCard(loan.displayLabel, stringResource(R.string.dashboard_loan_type_label), MasroofIcons.savings,
        MasroofThemeExtras.extendedColors.liability, modifier, onClick) {
        MasroofDetailRow(stringResource(R.string.approved_loan_remaining), loan.remainingBalance?.let { formatLocalizedMoney(it) } ?: unavailable)
        MasroofDetailRow(stringResource(R.string.approved_last_installment), loan.latestInstallmentAmount?.let { formatLocalizedMoney(it) } ?: unavailable)
        MasroofDetailRow(stringResource(R.string.approved_installment_date), loan.latestInstallmentAsOf?.let { date.format(it.atZone(ZoneId.systemDefault())) } ?: unavailable)
    }
}

@Composable
fun LoanDetailSummaryCard(
    loan: LoanOverview,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    val unavailable = stringResource(R.string.dashboard_value_unavailable)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        MasroofHeroCard(loan.displayLabel + " · " + stringResource(R.string.approved_loan_remaining), loan.remainingBalance?.let { formatLocalizedMoney(it) } ?: unavailable, role = MasroofHeroRole.Liability)
        MasroofCard {
            MasroofDetailRow(stringResource(R.string.approved_last_installment), loan.latestInstallmentAmount?.let { formatLocalizedMoney(it) } ?: unavailable)
            MasroofDetailRow(stringResource(R.string.approved_installment_date), loan.latestInstallmentAsOf?.let { date.format(it.atZone(zoneId)) } ?: unavailable)
            MasroofDetailRow(stringResource(R.string.approved_balance_date), loan.remainingBalanceAsOf?.let { date.format(it.atZone(zoneId)) } ?: unavailable)
        }
    }
}
