package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.common.MasroofTextStyles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CurrentAccountSummary
import com.baraa.masroof.application.dashboard.cashPosition
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofMoneyRow
import com.baraa.masroof.presentation.common.MasroofMoneyRowStyle
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun AccountPeriodSummaryCard(
    summary: CurrentAccountSummary,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    val movement = summary.cashPosition()
    val net = movement.remaining

    Column(modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        com.baraa.masroof.presentation.common.MasroofHeroCard(
            title = stringResource(R.string.dashboard_remaining_title), value = formatLocalizedMoney(net),
            hint = stringResource(R.string.dashboard_remaining_formula, formatLocalizedMoney(movement.inflow), formatLocalizedMoney(movement.outflow)),
            role = com.baraa.masroof.presentation.theme.MasroofHeroRole.Account,
        )
        MasroofCard {
        Text(
            stringResource(R.string.dashboard_account_remaining_calculated_hint),
            style = MasroofTextStyles.hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MasroofSpacing.inlineGap),
        )

        DashboardSummaryCardDivider()

        DashboardSummaryBreakdownHeader(
            title = stringResource(R.string.dashboard_current_account_inflows),
            modifier = Modifier.padding(top = MasroofSpacing.inlineGap),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap),
            modifier = Modifier.padding(top = MasroofSpacing.inlineGap),
        ) {
            AccountFlowMoneyRow(
                label = stringResource(R.string.dashboard_salary),
                amount = summary.inflow.salary,
                direction = TransactionDirectionUi.INCOME,
            )
            AccountFlowMoneyRow(
                label = stringResource(R.string.dashboard_external_in_short),
                amount = summary.inflow.externalTransfersIn,
                direction = TransactionDirectionUi.TRANSFER_IN,
            )
            AccountFlowMoneyRow(
                label = stringResource(R.string.dashboard_account_refunds),
                amount = summary.inflow.accountRefunds,
                direction = TransactionDirectionUi.INCOME,
            )
            if (summary.inflow.otherIncome.amount.signum() > 0) {
                AccountFlowMoneyRow(
                    label = stringResource(R.string.dashboard_other_income),
                    amount = summary.inflow.otherIncome,
                    direction = TransactionDirectionUi.INCOME,
                )
            }
        }
        DashboardSummaryTotalRow(
            label = stringResource(R.string.dashboard_total_inflow),
            amount = movement.inflow,
            amountColor = extended.inflow,
        )

        DashboardSummaryBreakdownHeader(
            title = stringResource(R.string.dashboard_money_movement),
            modifier = Modifier.padding(top = MasroofSpacing.cardInnerGap),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap),
            modifier = Modifier.padding(top = MasroofSpacing.inlineGap),
        ) {
            AccountDirectionMoneyRow(
                label = stringResource(R.string.dashboard_external_out_short),
                amount = summary.outflow.externalTransfersOut,
                direction = TransactionDirectionUi.OUTWARD,
            )
            AccountDirectionMoneyRow(
                label = stringResource(R.string.dashboard_credit_card_payment),
                amount = summary.outflow.creditCardPayments,
                direction = TransactionDirectionUi.OUTWARD,
            )
            AccountDirectionMoneyRow(
                label = stringResource(R.string.dashboard_cash_withdrawals),
                amount = summary.outflow.cashWithdrawals,
                direction = TransactionDirectionUi.OUTWARD,
            )
            AccountDirectionMoneyRow(
                label = stringResource(R.string.dashboard_bill_payments),
                amount = summary.outflow.billPayments,
                direction = TransactionDirectionUi.OUTWARD,
            )
            if (summary.outflow.loanRepayments.amount.signum() > 0) {
                AccountDirectionMoneyRow(
                    label = stringResource(R.string.dashboard_loan_repayments),
                    amount = summary.outflow.loanRepayments,
                    direction = TransactionDirectionUi.OUTWARD,
                )
            }
            AccountDirectionMoneyRow(
                label = stringResource(R.string.dashboard_pos_purchases_short),
                amount = summary.outflow.posPurchases,
                direction = TransactionDirectionUi.OUTWARD,
            )
            AccountDirectionMoneyRow(
                label = stringResource(R.string.dashboard_fees_short),
                amount = summary.outflow.fees,
                direction = TransactionDirectionUi.OUTWARD,
            )
        }
        DashboardSummaryTotalRow(
            label = stringResource(R.string.dashboard_total_spent),
            amount = movement.outflow,
            amountColor = extended.outflow,
        )

        if (
            summary.inflow.selfTransfersIn.amount.signum() > 0 ||
            summary.outflow.selfTransfersOut.amount.signum() > 0
        ) {
            DashboardSummaryCardDivider()
            DashboardSummaryBreakdownHeader(title = stringResource(R.string.dashboard_self_transfers))
            Column(
                verticalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap),
                modifier = Modifier.padding(top = MasroofSpacing.inlineGap),
            ) {
                AccountFlowMoneyRow(
                    label = stringResource(R.string.dashboard_self_transfer_in),
                    amount = summary.inflow.selfTransfersIn,
                    direction = TransactionDirectionUi.NEUTRAL,
                )
                AccountFlowMoneyRow(
                    label = stringResource(R.string.dashboard_self_transfer_out),
                    amount = summary.outflow.selfTransfersOut,
                    direction = TransactionDirectionUi.NEUTRAL,
                )
            }
            SelfTransfersHint(
                style = SelfTransfersHintStyle.IncludedInTotals,
                modifier = Modifier.padding(top = MasroofSpacing.inlineGap),
            )
        }
    }
    }
}

@Composable
private fun AccountDirectionMoneyRow(
    label: String,
    amount: Money,
    direction: TransactionDirectionUi,
) {
    if (amount.amount.signum() == 0) return
    MasroofMoneyRow(
        label = label,
        value = formatLocalizedMoney(amount),
        style = accountDirectionMoneyRowStyle(direction),
        leadingIcon = TransactionDirectionPresentation.icon(direction),
    )
}

@Composable
private fun AccountFlowMoneyRow(
    label: String,
    amount: Money,
    direction: TransactionDirectionUi,
) {
    AccountDirectionMoneyRow(label = label, amount = amount, direction = direction)
}

private fun accountDirectionMoneyRowStyle(direction: TransactionDirectionUi): MasroofMoneyRowStyle =
    when (direction) {
        TransactionDirectionUi.INCOME,
        TransactionDirectionUi.INWARD,
        TransactionDirectionUi.TRANSFER_IN,
        -> MasroofMoneyRowStyle.Inflow
        TransactionDirectionUi.OUTWARD -> MasroofMoneyRowStyle.Outflow
        TransactionDirectionUi.NEUTRAL -> MasroofMoneyRowStyle.Neutral
    }
