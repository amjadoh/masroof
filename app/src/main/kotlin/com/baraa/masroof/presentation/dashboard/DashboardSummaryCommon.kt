package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.ui.theme.MasroofSpacing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofPeriodDisplay
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@Composable
fun DashboardSummaryScaffold(
    title: String,
    state: DashboardUiState,
    onBack: () -> Unit,
    showPeriod: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val periodRangeLabel = state.period?.let {
        FinancialPeriodUiFormatter.formatRange(context, it)
    } ?: state.periodLabel

    MasroofSecondaryScaffold(
        title = title,
        onBack = onBack,
        backContentDescription = stringResource(R.string.dashboard_flow_detail_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
        ) {
            if (showPeriod) MasroofPeriodDisplay(label = periodRangeLabel, guidance = state.periodAdjustmentHint)

            if (state.error != null) {
                com.baraa.masroof.presentation.common.MasroofText(
                    stringResource(R.string.dashboard_load_error),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }

            if (state.loading && state.currentAccount == null) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MasroofSpacing.contentPaddingLarge),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (state.error == null || state.currentAccount != null) {
                content()
            }
        }
    }
}
