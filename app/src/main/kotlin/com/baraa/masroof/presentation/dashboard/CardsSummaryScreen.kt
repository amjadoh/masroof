package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.common.MasroofSectionHeader
import com.baraa.masroof.presentation.locale.formatLocalizedMoney

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.DebitCardOverview
import com.baraa.masroof.application.dashboard.aggregateDebitSalaryPeriodSpending
import java.time.ZoneId
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import com.baraa.masroof.application.dashboard.aggregateCreditSalaryPeriodSpending
import com.baraa.masroof.application.dashboard.aggregateCreditStatementSpending

@Composable
fun CardsSummaryRoute(
    viewModel: DashboardViewModel,
    initialSelectedCardKey: String? = null,
    initialSelectedDebitKey: String? = null,
    detailBackExitsSummary: Boolean = false,
    onInitialSelectionConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onManageCards: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenAllTransactions: (TransactionListFilterState) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedCardKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDebitKey by rememberSaveable { mutableStateOf<String?>(null) }
    val followedFacilities = state.followedCreditFacilities()
    val cardNetworks = state.ownedCards.associate { CardOwnershipKey.of(it) to it.cardNetwork }

    androidx.compose.runtime.LaunchedEffect(initialSelectedCardKey, initialSelectedDebitKey) {
        when {
            initialSelectedCardKey != null -> {
                selectedCardKey = initialSelectedCardKey
                onInitialSelectionConsumed()
            }
            initialSelectedDebitKey != null -> {
                selectedDebitKey = initialSelectedDebitKey
                onInitialSelectionConsumed()
            }
        }
    }

    val selectedCard = selectedCardKey?.let { key ->
        followedFacilities?.facilities
            ?.flatMap { it.allCards }
            ?.find { CardOwnershipKey.of(it) == key }
    }
    val selectedDebit = selectedDebitKey?.let { key ->
        followedFacilities?.debitCards?.find { CardOwnershipKey.of(it) == key }
    }

    fun clearDetailSelection() {
        if (detailBackExitsSummary) {
            onBack()
        } else {
            selectedDebitKey = null
            selectedCardKey = null
        }
    }

    BackHandler {
        when {
            selectedDebit != null || selectedCard != null -> clearDetailSelection()
            else -> onBack()
        }
    }

    when {
        selectedDebit != null -> {
            DebitCardDetailScreen(
                debit = selectedDebit,
                state = state,
                cardNetwork = cardNetworks[CardOwnershipKey.of(selectedDebit)] ?: selectedDebit.network,
                onBack = { clearDetailSelection() },
                onOpenTransaction = onOpenTransaction,
            )
        }

        selectedCard != null -> {
            CardDetailScreen(
                row = selectedCard,
                state = state,
                onBack = { clearDetailSelection() },
                onOpenTransaction = onOpenTransaction,
            )
        }

        else -> {
            CardsSummaryScreen(
                state = state,
                onBack = onBack,
                onManageCards = onManageCards,
                onOpenCard = { row -> selectedCardKey = CardOwnershipKey.of(row) },
                onOpenDebit = { debit -> selectedDebitKey = CardOwnershipKey.of(debit) },
                cardNetworksByLast4 = cardNetworks,
            )
        }
    }
}

@Composable
fun CardsSummaryScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onManageCards: () -> Unit,
    onOpenCard: (CreditCardDashboardRow) -> Unit,
    onOpenDebit: (DebitCardOverview) -> Unit,
    cardNetworksByLast4: Map<String, com.baraa.masroof.domain.model.CardNetwork?>,
) {
    val followedFacilities = state.followedCreditFacilities()
    var creditSelected by rememberSaveable(followedFacilities?.facilities?.isEmpty()) { mutableStateOf(followedFacilities?.facilities?.isNotEmpty() == true) }
    val locale = LocalConfiguration.current.locales[0]

    DashboardSummaryScaffold(
        title = stringResource(R.string.dashboard_cards_summary_screen_title),
        state = state,
        onBack = onBack,
        showPeriod = true,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
        ) {
            if (followedFacilities == null || !followedFacilities.hasContent) {
                Text(
                    stringResource(R.string.dashboard_cards_summary_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(com.baraa.masroof.presentation.theme.MasroofReference.shortcutGap)) {
                    com.baraa.masroof.presentation.common.MasroofChoice(stringResource(R.string.reference_credit_tab), creditSelected, { creditSelected = true })
                    com.baraa.masroof.presentation.common.MasroofChoice(stringResource(R.string.reference_mada_tab), !creditSelected, { creditSelected = false })
                }
                if (creditSelected && followedFacilities.facilities.isNotEmpty()) {
                    DashboardSummaryHeroCard(spec = creditFacilitiesSummaryHeroSpec(followedFacilities, locale))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(com.baraa.masroof.presentation.theme.MasroofReference.metricGap)) {
                        com.baraa.masroof.presentation.common.MasroofMetric(stringResource(R.string.reference_salary_spending),
                            formatLocalizedMoney(followedFacilities.aggregateCreditSalaryPeriodSpending()), com.baraa.masroof.presentation.theme.MasroofThemeExtras.extendedColors.card, Modifier.weight(1f))
                        com.baraa.masroof.presentation.common.MasroofMetric(stringResource(R.string.reference_statement_spending),
                            formatLocalizedMoney(followedFacilities.aggregateCreditStatementSpending()), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    }
                    SpendingTrendSection(state.spendingTrends?.creditCards, stringResource(R.string.approved_credit_daily), role = com.baraa.masroof.presentation.theme.MasroofHeroRole.Card)
                } else {
                    com.baraa.masroof.presentation.common.MasroofHeroCard(
                        stringResource(R.string.approved_total_expenses),
                        formatLocalizedMoney(followedFacilities.aggregateDebitSalaryPeriodSpending()),
                        role = com.baraa.masroof.presentation.theme.MasroofHeroRole.Account,
                    )
                    state.spendingTrends?.debitCards?.let { trend ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(com.baraa.masroof.presentation.theme.MasroofReference.metricGap)) {
                            com.baraa.masroof.presentation.common.MasroofMetric(stringResource(R.string.dashboard_expenses_before_refunds), formatLocalizedMoney(trend.totalBeforeRefunds),
                                com.baraa.masroof.presentation.theme.MasroofThemeExtras.extendedColors.outflow, Modifier.weight(1f))
                            com.baraa.masroof.presentation.common.MasroofMetric(stringResource(R.string.reference_refunds), formatLocalizedMoney(trend.totalRefunds),
                                com.baraa.masroof.presentation.theme.MasroofThemeExtras.extendedColors.inflow, Modifier.weight(1f))
                        }
                    }
                }
                CardsSummaryHeader(
                    cardCount = followedFacilities.cardCount,
                    onManageCards = onManageCards,
                )
                if (creditSelected) followedFacilities.facilities.forEach { facility ->
                    CreditFacilityCard(
                        facility = facility,
                        cardNetworksByLast4 = cardNetworksByLast4,
                        zoneId = ZoneId.systemDefault(),
                        ownedCards = state.ownedCards,
                        modifier = Modifier.fillMaxWidth(),
                        onOpenCard = onOpenCard,
                        trends = state.spendingTrends?.byCard.orEmpty(),
                    )
                }
                if (!creditSelected) followedFacilities.debitCards.forEach { debit ->
                    DebitCardSummaryTile(
                        debit = debit,
                        network = cardNetworksByLast4[CardOwnershipKey.of(debit)] ?: debit.network,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenDebit(debit) },
                        trend = state.spendingTrends?.byCard?.get(CardOwnershipKey.of(debit)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardsSummaryHeader(
    cardCount: Int,
    onManageCards: () -> Unit,
) {
    MasroofSectionHeader(title = stringResource(R.string.reference_followed_cards))
}
