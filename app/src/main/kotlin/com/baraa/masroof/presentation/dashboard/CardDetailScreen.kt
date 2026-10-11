package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import java.time.ZoneId

@Composable
fun CardDetailScreen(
    row: CreditCardDashboardRow,
    state: DashboardUiState,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    val cardTransactions = DashboardSummaryTransactionFilter.forCard(
        transactions = state.allTransactions,
        bank = row.bank,
        last4 = row.last4,
        cardInvolvementByTransactionId = state.transactionCardInvolvement,
    )
    val facility = state.followedCreditFacilities()?.facilities?.firstOrNull { it.allCards.any { card -> card.bank == row.bank && card.last4 == row.last4 } }
    val title = row.displayLabel(state.ownedCards)
    val cardNetwork = state.ownedCards
        .find { it.bank == row.bank && it.last4 == row.last4 }
        ?.cardNetwork

    DashboardSummaryScaffold(
        title = androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.reference_card_detail_title),
        state = state,
        onBack = onBack,
        showPeriod = false,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
        ) {
            CreditCardDetailSummaryCard(
                row = row,
                facility = facility,
                zoneId = ZoneId.systemDefault(),
                cardNetwork = cardNetwork,
                ownedCards = state.ownedCards,
                modifier = Modifier.fillMaxWidth(),
            )

            val trend = state.spendingTrends?.byCard?.get(CardOwnershipKey.of(row))
            trend?.let { CardReferenceAverage(it) }
            SpendingTrendSection(trend, androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.approved_card_daily),
                role = com.baraa.masroof.presentation.theme.MasroofHeroRole.Card)
            DashboardSummaryTransactionsSection(
                transactions = cardTransactions,
                onOpenTransaction = onOpenTransaction,
                ownedCards = state.ownedCards,
                title = androidx.compose.ui.res.stringResource(com.baraa.masroof.R.string.reference_card_movements),
            )
        }
    }
}
