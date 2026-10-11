package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditFacilityOverview
import com.baraa.masroof.application.dashboard.DebitCardOverview
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.time.ZoneId

@Composable
fun DebitCardDetailSummaryCard(debit: DebitCardOverview, network: CardNetwork?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        CardNetworkBadge(network, debit.last4)
        com.baraa.masroof.presentation.common.MasroofHeroCard(debit.displayLabel,
            formatLocalizedMoney(debit.salaryPeriodSpendingNet), stringResource(R.string.approved_total_expenses),
            role = com.baraa.masroof.presentation.theme.MasroofHeroRole.Account)
        MasroofCard {
            com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.dashboard_debit_card_linked_account),
                debit.linkedAccountLabel ?: debit.linkedAccountMaskedNumber?.let { formatCardLast4(it) }
                    ?: stringResource(R.string.dashboard_value_unavailable))
        }
    }
}

@Composable
fun DebitCardSummaryTile(debit: DebitCardOverview, network: CardNetwork?, modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null, showNavigationIcon: Boolean = onClick != null,
    trend: com.baraa.masroof.application.dashboard.DailySpendingTrend? = null,
) {
    EntitySummaryCard(debit.displayLabel, stringResource(R.string.card_network_mada), MasroofIcons.cardPayment,
        MasroofThemeExtras.extendedColors.account, modifier, onClick, leading = { CardNetworkBadge(network, debit.last4) }) {
        com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.dashboard_debit_card_linked_account),
            debit.linkedAccountLabel ?: debit.linkedAccountMaskedNumber?.let { formatCardLast4(it) } ?: stringResource(R.string.dashboard_value_unavailable))
        com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.approved_total_expenses), formatLocalizedMoney(debit.salaryPeriodSpendingNet))
        trend?.let { com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.approved_daily_average), formatLocalizedMoney(it.averageDailySpending)) }
    }
}

@Composable
fun CreditFacilityCard(facility: CreditFacilityOverview, cardNetworksByLast4: Map<String, CardNetwork?>,
    zoneId: ZoneId, ownedCards: List<OwnedCardUi> = emptyList(), modifier: Modifier = Modifier,
    allowInlineExpand: Boolean = true, onOpenCard: ((CreditCardDashboardRow) -> Unit)? = null,
    trends: Map<String, com.baraa.masroof.application.dashboard.DailySpendingTrend> = emptyMap(),
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        facility.allCards.forEach { row ->
            EntitySummaryCard(row.displayLabel(ownedCards), stringResource(if (row.last4 == facility.primaryLast4)
                R.string.dashboard_credit_facility_primary else R.string.dashboard_credit_facility_additional),
                MasroofIcons.cardPayment, MasroofThemeExtras.extendedColors.card, onClick = onOpenCard?.let { open -> { open(row) } },
                leading = { CardNetworkBadge(cardNetworksByLast4[CardOwnershipKey.of(row)], row.last4) }) {
                val unavailable = stringResource(R.string.dashboard_value_unavailable)
                com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.approved_available_balance), row.snapshot?.availableBalance?.let { formatLocalizedMoney(it) } ?: unavailable)
                com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.approved_due_amount), facility.facilityDue?.amount?.let { formatLocalizedMoney(it) } ?: unavailable)
                com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.approved_due_date), facility.facilityDue?.dueDate?.let(date::format) ?: unavailable)
                trends[CardOwnershipKey.of(row)]?.let { com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.approved_daily_average), formatLocalizedMoney(it.averageDailySpending)) }
                if (row.last4 != facility.primaryLast4) Text(stringResource(R.string.approved_shared_due), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
