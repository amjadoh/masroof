package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.application.dashboard.LoanOverview

@Composable
fun LoanDetailScreen(
    loan: LoanOverview,
    state: DashboardUiState,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    val loanTransactions = DashboardSummaryTransactionFilter.forLoan(
        transactions = state.allTransactions,
        bank = loan.bank,
        loanType = loan.loanType,
        involvementByTransactionId = state.transactionLoanInvolvement,
    )

    DashboardSummaryScaffold(
        title = loan.displayLabel,
        state = state,
        onBack = onBack,
        showPeriod = false,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
        ) {
            LoanDetailSummaryCard(
                loan = loan,
                modifier = Modifier.fillMaxWidth(),
            )

            DashboardSummaryTransactionsSection(
                transactions = loanTransactions,
                onOpenTransaction = onOpenTransaction,
                ownedCards = state.ownedCards,
            )
        }
    }
}
