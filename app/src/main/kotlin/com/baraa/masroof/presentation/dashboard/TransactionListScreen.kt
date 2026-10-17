package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.baraa.masroof.ui.theme.MasroofSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.common.ShareActionIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    periodLabel: String,
    transactions: List<TransactionPreviewUi>,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    seedFilter: TransactionListFilterState? = null,
    onSeedFilterApplied: () -> Unit = {},
    openGeneration: Int = 0,
    ownedCardKeys: Set<String> = emptySet(),
    ownedAccountContainerIds: Set<String> = emptySet(),
    ownedCards: List<OwnedCardUi> = emptyList(),
    ownedAccounts: List<OwnedAccountUi> = emptyList(),
    transactionAccountInvolvement: Map<String, Set<String>> = emptyMap(),
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTypeNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    var selectedCardLast4s by rememberSaveable { mutableStateOf(listOf<String>()) }
    var selectedAccountContainerIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var seedTransactionIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var filtersSheetOpen by rememberSaveable { mutableStateOf(false) }
    var appliedOpenGeneration by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(-1) }
    val listState = rememberLazyListState()

    LaunchedEffect(openGeneration) {
        // A fold/configuration recreation restores the current draft; only a new
        // navigation request should consume a seed and reset the filter sheet.
        if (appliedOpenGeneration == openGeneration) return@LaunchedEffect
        appliedOpenGeneration = openGeneration
        val filter = seedFilter ?: TransactionListFilterState()
        searchQuery = filter.searchQuery
        selectedTypeNames = filter.types.map { it.name }
        selectedCardLast4s = filter.cardLast4s.toList()
        selectedAccountContainerIds = filter.accountContainerIds.toList()
        seedTransactionIds = filter.transactionIds
        filtersSheetOpen = false
        onSeedFilterApplied()
    }

    val filterState = remember(
        searchQuery,
        selectedTypeNames,
        selectedCardLast4s,
        selectedAccountContainerIds,
        seedTransactionIds,
    ) {
        TransactionListFilterState(
            searchQuery = searchQuery,
            types = selectedTypeNames.mapNotNull { name ->
                runCatching { FinancialTransactionType.valueOf(name) }.getOrNull()
            }.toSet(),
            cardLast4s = selectedCardLast4s.toSet(),
            accountContainerIds = selectedAccountContainerIds.toSet(),
            transactionIds = seedTransactionIds,
        )
    }

    val filterResult = remember(transactions, filterState, transactionAccountInvolvement) {
        TransactionListFilterEngine.apply(
            transactions = transactions,
            filter = filterState,
            accountInvolvementByTransactionId = transactionAccountInvolvement,
        )
    }
    val availableTypes = remember(transactions) {
        TransactionListFilterEngine.availableTypes(transactions)
    }
    val availableCards = remember(transactions, ownedCardKeys) {
        TransactionListFilterEngine.availableCardLast4s(
            transactions = transactions,
            ownedCardKeys = ownedCardKeys,
        )
    }
    val availableAccounts = remember(transactions, ownedAccountContainerIds, transactionAccountInvolvement) {
        TransactionListFilterEngine.availableAccountContainerIds(
            transactions = transactions,
            ownedAccountContainerIds = ownedAccountContainerIds,
            involvementByTransactionId = transactionAccountInvolvement,
        )
    }
    val activeFilterCount = filterState.activeFilterCount()
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.transaction_share)
    val shareSubject = stringResource(R.string.transaction_list_title)
    val shareText = transactionListShareText(
        periodLabel = periodLabel,
        filter = filterState,
        transactions = filterResult.transactions,
        totalAmount = filterResult.totalAmount,
        ownedCards = ownedCards,
        ownedAccounts = ownedAccounts,
    )
    val canShare = filterResult.transactions.isNotEmpty()

    val clearFilters = {
        searchQuery = ""
        selectedTypeNames = emptyList()
        selectedCardLast4s = emptyList()
        selectedAccountContainerIds = emptyList()
        filtersSheetOpen = false
    }

    MasroofSecondaryScaffold(
        title = stringResource(R.string.transaction_list_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.review_back),
        actions = {
            ShareActionIcon(
                enabled = canShare,
                onClick = {
                    SharePlainText.share(
                        context = context,
                        text = shareText,
                        chooserTitle = shareChooserTitle,
                        subject = shareSubject,
                    )
                },
            )
        },
    ) { contentModifier ->
        if (transactions.isEmpty()) {
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(MasroofSpacing.screenPaddingLarge),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.dashboard_empty_period),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            return@MasroofSecondaryScaffold
        }

        Column(
            modifier = contentModifier.fillMaxSize(),
        ) {
            TransactionListToolbar(
                periodLabel = periodLabel,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onOpenFilters = { filtersSheetOpen = true },
                activeFilterCount = activeFilterCount,
                onClearFilters = clearFilters,
                filteredCount = filterResult.transactions.size,
                totalCount = transactions.size,
                totalAmount = filterResult.totalAmount,
                filterActive = filterState.isActive,
                typeChoices = availableTypes.map { it.name to transactionTypeLabel(it) },
                selectedType = selectedTypeNames.singleOrNull(),
                onSelectType = { name -> selectedTypeNames = if (name.isEmpty()) emptyList() else listOf(name) },
                sourceChoices = availableAccounts.map { "account:$it" to (accountDisplayLabel(ownedAccounts, it) ?: it) } +
                    availableCards.map { "card:$it" to cardDisplayLabel(ownedCards, it) },
                selectedSource = selectedAccountContainerIds.singleOrNull()?.let { "account:$it" }
                    ?: selectedCardLast4s.singleOrNull()?.let { "card:$it" },
                onSelectSource = { key ->
                    selectedAccountContainerIds = if (key.startsWith("account:")) listOf(key.removePrefix("account:")) else emptyList()
                    selectedCardLast4s = if (key.startsWith("card:")) listOf(key.removePrefix("card:")) else emptyList()
                },
            )
            Spacer(Modifier.height(MasroofSpacing.cardInnerGap))

            androidx.compose.material3.Surface(modifier = Modifier.weight(1f),
                shape = com.baraa.masroof.ui.theme.MasroofCardShape,
                border = androidx.compose.foundation.BorderStroke(MasroofSpacing.borderWidth, com.baraa.masroof.ui.theme.MasroofThemeExtras.extendedColors.cardBorder)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(horizontal = MasroofSpacing.cardInnerPadding),
                verticalArrangement = Arrangement.spacedBy(MasroofSpacing.none),
            ) {
                if (filterResult.transactions.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.transaction_list_no_filter_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = MasroofSpacing.contentPaddingLarge),
                        )
                    }
                } else {
                    items(filterResult.transactions, key = { it.id }) { row ->
                        TransactionPreviewRow(
                            row = row,
                            ownedCards = ownedCards,
                            onClick = { onOpenTransaction(row.id) },
                        )
                    }
                }

                item { Spacer(Modifier.height(MasroofSpacing.screenHorizontal)) }
            }
            }
        }

        TransactionListFilterBottomSheet(
            visible = filtersSheetOpen,
            onDismiss = { filtersSheetOpen = false },
            filterState = filterState,
            availableTypes = availableTypes,
            availableCards = availableCards,
            availableAccounts = availableAccounts,
            ownedCards = ownedCards,
            ownedAccounts = ownedAccounts,
            onApply = { draft ->
                selectedTypeNames = draft.types.map { it.name }
                selectedCardLast4s = draft.cardLast4s.toList()
                selectedAccountContainerIds = draft.accountContainerIds.toList()
                searchQuery = draft.searchQuery
                seedTransactionIds = draft.transactionIds
            },
        )
    }
}
