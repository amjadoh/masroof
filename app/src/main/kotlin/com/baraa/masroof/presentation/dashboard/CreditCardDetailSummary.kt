package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditFacilityOverview
import com.baraa.masroof.application.dashboard.statementDueFor
import com.baraa.masroof.presentation.common.MasroofHeroCard
import com.baraa.masroof.presentation.common.MasroofDetailRow
import com.baraa.masroof.presentation.theme.MasroofHeroRole
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CreditCardDetailSummaryCard(
    row: CreditCardDashboardRow,
    zoneId: ZoneId,
    cardNetwork: CardNetwork? = null,
    ownedCards: List<OwnedCardUi> = emptyList(),
    modifier: Modifier = Modifier,
    facility: CreditFacilityOverview? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    val unavailable = stringResource(R.string.dashboard_value_unavailable)
    val statement = row.statementDueFor(facility)
    val due = statement?.amount
    val dueDate = statement?.dueDate
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        MasroofHeroCard(
            title = row.displayLabel(ownedCards) + " · " + stringResource(R.string.approved_available_balance),
            value = row.snapshot?.availableBalance?.let { formatLocalizedMoney(it) } ?: unavailable,
            hint = listOfNotNull(
                stringResource(R.string.dashboard_credit_card_credit_badge),
                cardNetwork?.let { com.baraa.masroof.presentation.settings.cardNetworkLabel(it) },
                com.baraa.masroof.presentation.common.formatCardLast4(row.last4),
            ).joinToString(" · "),
            role = MasroofHeroRole.Card,
        )
        MasroofCard {
            MasroofDetailRow(stringResource(R.string.approved_due_amount), due?.let { formatLocalizedMoney(it) } ?: unavailable)
            MasroofDetailRow(stringResource(R.string.approved_due_date), dueDate?.let(date::format) ?: unavailable)
            row.snapshot?.updatedAt?.let { MasroofDetailRow(stringResource(R.string.approved_balance_date), date.format(it.atZone(zoneId))) }
            if (facility != null && facility.primaryLast4 != row.last4) {
                MasroofDetailRow(stringResource(R.string.approved_linked_primary), facility.primaryLast4)
                Text(stringResource(R.string.approved_shared_due), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
