package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.ui.theme.MasroofSpacing
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
import com.baraa.masroof.ui.theme.MasroofThemeExtras
import java.time.ZoneId

@Composable
fun DebitCardDetailSummaryCard(debit: DebitCardOverview, network: CardNetwork?, modifier: Modifier = Modifier,
    trend: com.baraa.masroof.application.dashboard.DailySpendingTrend? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        com.baraa.masroof.presentation.common.MasroofHeroCard(debit.displayLabel,
            formatLocalizedMoney(debit.salaryPeriodSpendingNet), com.baraa.masroof.presentation.settings.settingsBankLabel(debit.bank),
            role = com.baraa.masroof.ui.theme.MasroofHeroRole.Account)
        MasroofCard {
            Text(stringResource(R.string.reference_card_data), style = MaterialTheme.typography.titleMedium)
            com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.reference_card_type), stringResource(R.string.card_network_mada))
            com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.dashboard_debit_card_linked_account),
                debit.linkedAccountLabel ?: debit.linkedAccountMaskedNumber?.let { formatCardLast4(it) }
                    ?: stringResource(R.string.dashboard_value_unavailable))
            com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.dashboard_expenses_before_refunds),
                trend?.let { formatLocalizedMoney(it.totalBeforeRefunds) } ?: stringResource(R.string.dashboard_value_unavailable))
            com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.reference_refunds),
                trend?.let { formatLocalizedMoney(it.totalRefunds) } ?: stringResource(R.string.dashboard_value_unavailable),
                valueColor = MasroofThemeExtras.extendedColors.inflow)
        }
    }
}

@Composable
fun DebitCardSummaryTile(debit: DebitCardOverview, network: CardNetwork?, modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trend: com.baraa.masroof.application.dashboard.DailySpendingTrend? = null,
) {
    EntitySummaryCard(debit.displayLabel, stringResource(R.string.card_network_mada), MasroofIcons.cardPayment,
        MasroofThemeExtras.extendedColors.account, modifier, onClick, actionLabel = stringResource(R.string.reference_card_details),
        bankLabel = com.baraa.masroof.presentation.settings.settingsBankLabel(debit.bank)) {
        com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.dashboard_debit_card_linked_account),
            debit.linkedAccountLabel ?: debit.linkedAccountMaskedNumber?.let { formatCardLast4(it) } ?: stringResource(R.string.dashboard_value_unavailable), dense = true)
        com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.dashboard_expenses_before_refunds),
            trend?.let { formatLocalizedMoney(it.totalBeforeRefunds) } ?: stringResource(R.string.dashboard_value_unavailable), dense = true)
        com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.reference_refunds),
            trend?.let { formatLocalizedMoney(it.totalRefunds) } ?: stringResource(R.string.dashboard_value_unavailable),
            dense = true, valueColor = MasroofThemeExtras.extendedColors.inflow)
        trend?.let { CardReferenceAverage(it, inCard = true) }
    }
}

@Composable
fun CreditFacilityCard(facility: CreditFacilityOverview, cardNetworksByLast4: Map<String, CardNetwork?>,
    zoneId: ZoneId, ownedCards: List<OwnedCardUi> = emptyList(), modifier: Modifier = Modifier,
    allowInlineExpand: Boolean = true, onOpenCard: ((CreditCardDashboardRow) -> Unit)? = null,
    trends: Map<String, com.baraa.masroof.application.dashboard.DailySpendingTrend> = emptyMap(),
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = java.time.format.DateTimeFormatter.ofPattern("d MMM", locale)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        facility.allCards.forEach { row ->
            EntitySummaryCard(row.displayLabel(ownedCards), stringResource(if (row.last4 == facility.primaryLast4)
                R.string.dashboard_credit_facility_primary else R.string.dashboard_credit_facility_additional),
                MasroofIcons.cardPayment, MasroofThemeExtras.extendedColors.card, onClick = onOpenCard?.let { open -> { open(row) } },
                actionLabel = stringResource(R.string.reference_card_details), bankLabel = com.baraa.masroof.presentation.settings.settingsBankLabel(row.bank)) {
                val unavailable = stringResource(R.string.dashboard_value_unavailable)
                com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.reference_salary_spending), formatLocalizedMoney(row.salaryPeriodSpendingNet), dense = true)
                com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.reference_statement_spending), formatLocalizedMoney(row.statementSpendingNet), dense = true)
                com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.approved_available_balance),
                    row.snapshot?.negativeAvailableBalance?.let { "−${formatLocalizedMoney(it)}" }
                        ?: row.snapshot?.availableBalance?.let { formatLocalizedMoney(it) } ?: unavailable, dense = true)
                com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.approved_due_amount), facility.facilityDue?.amount?.let { formatLocalizedMoney(it) } ?: unavailable,
                    guidance = if (row.last4 != facility.primaryLast4) stringResource(R.string.approved_shared_due) else null, valueColor = MasroofThemeExtras.extendedColors.liability, dense = true)
                com.baraa.masroof.presentation.common.MasroofDetailRow(stringResource(R.string.approved_due_date), facility.facilityDue?.dueDate?.let(date::format) ?: unavailable, dense = true)
                trends[CardOwnershipKey.of(row)]?.let { CardReferenceAverage(it, inCard = true) }
            }
        }
    }
}
