package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.FinancialTransactionType

/**
 * Maps a classified [FinancialTransactionType] to expense / income / net-worth
 * effects using DOMAIN rules (D-001, D-004…D-008, §11–12).
 */
object FinancialImpactCalculator {
    fun forType(type: FinancialTransactionType): FinancialImpact =
        when (type) {
            FinancialTransactionType.SELF_TRANSFER,
            FinancialTransactionType.CREDIT_CARD_PAYMENT,
            FinancialTransactionType.LOAN_REPAYMENT,
            FinancialTransactionType.CASH_WITHDRAWAL,
            ->
                // Moving cash between owned containers or settling principal
                // (cash down, liability down) leaves net worth unchanged.
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.ZERO,
                )

            FinancialTransactionType.EXPENSE,
            FinancialTransactionType.BILL_PAYMENT,
            FinancialTransactionType.FEE,
            ->
                // Fees and bills reduce net worth like ordinary spending.
                FinancialImpact(
                    countsAsExpense = true,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.DECREASE,
                )

            FinancialTransactionType.INCOME ->
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = true,
                    netWorthEffect = NetWorthEffect.INCREASE,
                )

            FinancialTransactionType.REFUND ->
                // Offsets a prior purchase; not ordinary income (D-008, §12).
                FinancialImpact(
                    countsAsExpense = false,
                    countsAsIncome = false,
                    netWorthEffect = NetWorthEffect.INCREASE,
                )

            FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            FinancialTransactionType.EXTERNAL_TRANSFER_OUT,
            FinancialTransactionType.ADJUSTMENT,
            FinancialTransactionType.UNKNOWN,
            ->
                // Cash direction alone does not establish income, expense or
                // net-worth effect (loan, gift, reimbursement, investment, etc.).
                unresolved()
        }

    fun unresolved(): FinancialImpact =
        FinancialImpact(
            countsAsExpense = false,
            countsAsIncome = false,
            netWorthEffect = NetWorthEffect.UNRESOLVED,
        )
}
