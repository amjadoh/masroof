package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofOpacity
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofSizes
import com.baraa.masroof.presentation.theme.MasroofFontWeights
import com.baraa.masroof.presentation.theme.MasroofComponentShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.locale.formatLocalizedMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListToolbar(
    periodLabel: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    activeFilterCount: Int,
    onClearFilters: () -> Unit,
    filteredCount: Int,
    totalCount: Int,
    totalAmount: Money?,
    filterActive: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.rowHorizontalPadding),
    ) {
        Text(
            text = stringResource(R.string.transaction_list_period, periodLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextField(
            visualTransformation = com.baraa.masroof.presentation.locale.AppDigitsVisualTransformation,
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    stringResource(R.string.transaction_list_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(MasroofSpacing.largeGap),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.transaction_list_clear_search),
                        )
                    }
                }
            },
            shape = MasroofComponentShapes.radius14,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = {
                    if (activeFilterCount > 0) {
                        Badge { Text(activeFilterCount.toString()) }
                    }
                },
            ) {
                AssistChip(
                    onClick = onOpenFilters,
                    label = { Text(stringResource(R.string.transaction_list_filters_toggle)) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(MasroofSizes.size18),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (activeFilterCount > 0) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                )
            }

            if (filterActive) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.transaction_list_clear_filters))
                }
            }
        }

        TransactionListResultsBanner(
            filteredCount = filteredCount,
            totalCount = totalCount,
            totalAmount = totalAmount,
            filterActive = filterActive,
        )
    }
}

@Composable
private fun TransactionListResultsBanner(
    filteredCount: Int,
    totalCount: Int,
    totalAmount: Money?,
    filterActive: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MasroofComponentShapes.radius12,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = MasroofOpacity.subtle),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MasroofSpacing.compactCardContentPadding, vertical = MasroofSpacing.rowHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.hairlineGap),
        ) {
            Text(
                text = if (filterActive) {
                    stringResource(
                        R.string.transaction_list_filtered_count,
                        filteredCount,
                        totalCount,
                    )
                } else {
                    stringResource(R.string.transaction_list_count, totalCount)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = MasroofFontWeights.semiBold,
            )
            if (totalAmount != null && filteredCount > 0) {
                Text(
                    text = stringResource(
                        R.string.transaction_list_filtered_total,
                        formatLocalizedMoney(totalAmount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun TransactionListFilterState.activeFilterCount(): Int {
    var count = types.size + cardLast4s.size + accountContainerIds.size + transactionIds.size
    if (searchQuery.isNotBlank()) count++
    return count
}
