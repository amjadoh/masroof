package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.presentation.common.toUiLabelRes
import com.baraa.masroof.domain.model.FinancialTransactionType

@Composable
fun transactionTypeLabel(type: FinancialTransactionType): String =
    stringResource(type.toUiLabelRes())

/** Purchases and refunds name a merchant; when the SMS gave none, say so instead of repeating the type label. */
@Composable
fun transactionTitle(row: TransactionPreviewUi): String =
    row.title?.takeIf { it.isNotBlank() } ?: if (row.expectsMerchant()) stringResource(com.baraa.masroof.R.string.transaction_unknown_merchant)
    else transactionTypeLabel(row.typeLabelResHint)

internal fun TransactionPreviewUi.expectsMerchant() =
    type == FinancialTransactionType.EXPENSE || type == FinancialTransactionType.REFUND
