package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.PrepaidCardOverview
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofMoneyRow
import com.baraa.masroof.presentation.common.MasroofText
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.ui.theme.MasroofSpacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun PrepaidCardSection(card: PrepaidCardOverview) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(LocalConfiguration.current.locales[0]).withZone(ZoneId.systemDefault())
    MasroofCard {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofText(card.displayLabel, style = MaterialTheme.typography.titleMedium)
            com.baraa.masroof.presentation.common.MasroofGuidanceLabel(
                stringResource(R.string.prepaid_card_movement), stringResource(R.string.prepaid_card_currency_hint))
            if (card.unconvertedFundingCount > 0) MasroofText(
                stringResource(R.string.prepaid_card_unconverted_funding, card.unconvertedFundingCount),
                style = MaterialTheme.typography.bodySmall,
            )
            if (card.currencies.isEmpty()) MasroofText(stringResource(R.string.prepaid_card_balance_unknown))
            card.currencies.forEach { row ->
                MasroofText(row.currency.name, style = MaterialTheme.typography.titleSmall)
                MasroofMoneyRow(stringResource(R.string.prepaid_card_top_ups), formatLocalizedMoney(row.topUps))
                MasroofMoneyRow(stringResource(R.string.prepaid_card_spending), formatLocalizedMoney(row.spending))
                MasroofMoneyRow(stringResource(R.string.prepaid_card_refunds), formatLocalizedMoney(row.refunds))
                MasroofMoneyRow(stringResource(R.string.prepaid_card_movement), formatLocalizedMoney(row.netMovement))
                row.observedBalance?.let { balance ->
                    MasroofMoneyRow(stringResource(R.string.prepaid_card_observed_balance), formatLocalizedMoney(balance))
                    row.balanceReceivedAt?.let { MasroofText(dateFormat.format(it), style = MaterialTheme.typography.bodySmall) }
                } ?: MasroofText(stringResource(R.string.prepaid_card_balance_unknown), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
