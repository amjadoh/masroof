package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofFontWeights
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CommitmentDashboardRow
import com.baraa.masroof.application.dashboard.CommitmentPaymentStatus
import com.baraa.masroof.application.dashboard.CommitmentDashboardSource
import com.baraa.masroof.application.dashboard.CommitmentsOverview
import com.baraa.masroof.presentation.common.MasroofAmountRole
import com.baraa.masroof.presentation.common.MasroofAmountText
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofDonutChart
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSectionHeader
import com.baraa.masroof.presentation.common.commitmentDonutPaidFraction
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.time.format.DateTimeFormatter

@Composable
fun CommitmentsSection(overview: CommitmentsOverview, modifier: Modifier = Modifier,
    onOpenCredit: (() -> Unit)? = null) {
    if (!overview.hasContent) return
    val colors = MasroofThemeExtras.extendedColors
    val pending = overview.rows.filter { it.status == CommitmentPaymentStatus.UNPAID }
    Column(modifier.padding(top = com.baraa.masroof.presentation.theme.MasroofReference.sectionHeadingTop - MasroofSpacing.sectionGap),
        verticalArrangement = Arrangement.spacedBy(com.baraa.masroof.presentation.theme.MasroofReference.sectionHeadingBottom)) {
        Text(stringResource(R.string.reference_period_commitments), style = MaterialTheme.typography.titleMedium)
        androidx.compose.material3.Surface(color = colors.liabilitySoft,
            shape = com.baraa.masroof.presentation.theme.MasroofComponentShapes.radius14) {
            Row(Modifier.fillMaxWidth().padding(com.baraa.masroof.presentation.theme.MasroofReference.contextualBandPadding),
                horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap),
                verticalAlignment = Alignment.Top) {
                androidx.compose.material3.Icon(MasroofIcons.clock, null,
                    Modifier.size(com.baraa.masroof.presentation.theme.MasroofReference.icon), tint = colors.liability)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reference_waiting_payment, formatLocalizedMoney(overview.remaining)),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = MasroofFontWeights.semiBold))
                    pending.take(2).forEach { row ->
                        Text(row.displayName + (row.expectedDate?.let {
                            " · " + java.time.format.DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0]).format(it)
                        } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (onOpenCredit != null && pending.any { it.source == CommitmentDashboardSource.CREDIT_CARD }) {
                        com.baraa.masroof.presentation.common.MasroofTextButton(onClick = onOpenCredit) {
                            Text(stringResource(R.string.reference_card_details), style = MaterialTheme.typography.bodySmall, color = colors.liability)
                        }
                    }
                }
            }
        }
    }
}
