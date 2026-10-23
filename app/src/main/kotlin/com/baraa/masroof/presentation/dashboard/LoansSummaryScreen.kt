package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.baraa.masroof.ui.theme.MasroofSpacing
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.common.MasroofTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.LoanOverview
import com.baraa.masroof.presentation.common.MasroofSectionTitle

@Composable
fun LoansSummaryRoute(
    viewModel: DashboardViewModel,
    initialSelectedLoanKey: String? = null,
    detailBackExitsSummary: Boolean = false,
    onInitialSelectionConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedLoanKey by rememberSaveable { mutableStateOf<String?>(null) }
    val followedLoans = state.followedLoansOverview()

    androidx.compose.runtime.LaunchedEffect(initialSelectedLoanKey) {
        if (initialSelectedLoanKey != null) {
            selectedLoanKey = initialSelectedLoanKey
            onInitialSelectionConsumed()
        }
    }

    val selectedLoan = selectedLoanKey?.let { key ->
        followedLoans?.loans?.find { LoanOwnershipKey.of(it) == key }
    }

    fun clearLoanSelection() {
        if (detailBackExitsSummary) {
            onBack()
        } else {
            selectedLoanKey = null
        }
    }

    BackHandler {
        when {
            selectedLoan != null -> clearLoanSelection()
            else -> onBack()
        }
    }

    when {
        selectedLoan != null -> {
            LoanDetailScreen(
                loan = selectedLoan,
                state = state,
                onBack = { clearLoanSelection() },
                onOpenTransaction = onOpenTransaction,
            )
        }

        else -> {
            LoansSummaryScreen(
                state = state,
                onBack = onBack,
                onOpenLoan = { loan -> selectedLoanKey = LoanOwnershipKey.of(loan) },
            )
        }
    }
}

@Composable
fun LoansSummaryScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onOpenLoan: (LoanOverview) -> Unit,
) {
    val followedLoans = state.followedLoansOverview()

    DashboardSummaryScaffold(
        title = stringResource(R.string.dashboard_loans_summary_screen_title),
        state = state,
        onBack = onBack,
        showPeriod = false,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
        ) {
            if (followedLoans == null || !followedLoans.hasContent) {
                Text(
                    stringResource(R.string.dashboard_loans_summary_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DashboardSummaryHeroCard(spec = loansSummaryHeroSpec(overview = followedLoans))
                followedLoans.loans.forEach { loan ->
                    LoanCompactListRow(
                        loan = loan,
                        onClick = { onOpenLoan(loan) },
                    )
                }
            }
        }
    }
}

@Composable
fun LoanCompactListRow(loan: LoanOverview, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LoanSummaryTile(loan, modifier, onClick)
}
