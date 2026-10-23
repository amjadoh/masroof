package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.presentation.common.toUiLabelRes
import com.baraa.masroof.domain.model.FinancialTransactionType

@Composable
fun transactionTypeLabel(type: FinancialTransactionType): String =
    stringResource(type.toUiLabelRes())
