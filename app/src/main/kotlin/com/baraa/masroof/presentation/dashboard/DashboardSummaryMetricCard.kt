package com.baraa.masroof.presentation.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.ui.theme.MasroofThemeExtras
import java.math.BigDecimal

enum class DashboardMetricTone {
    Inflow,
    Outflow,
    Liability,
    Signed,
    Neutral,
}

data class DashboardSummaryMetricItem(
    val title: String,
    val amount: String,
    val tone: DashboardMetricTone,
    val hint: String? = null,
    val signedAmount: BigDecimal? = null,
)

@Composable
fun resolveMetricToneColor(tone: DashboardMetricTone, signedAmount: BigDecimal? = null): Color {
    val extended = MasroofThemeExtras.extendedColors
    return when (tone) {
        DashboardMetricTone.Inflow -> extended.inflow
        DashboardMetricTone.Outflow -> extended.outflow
        DashboardMetricTone.Liability -> extended.liability
        DashboardMetricTone.Signed -> signedAmount?.let { signedAmountMetricTone(it) }
            ?.let { resolveMetricToneColor(it) }
            ?: MaterialTheme.colorScheme.onSurface
        DashboardMetricTone.Neutral -> MaterialTheme.colorScheme.onSurface
    }
}

fun spendingMetricTone(amount: SignedMoneyAmount): DashboardMetricTone =
    when (amount.amount.signum()) {
        1 -> DashboardMetricTone.Outflow
        -1 -> DashboardMetricTone.Inflow
        else -> DashboardMetricTone.Neutral
    }

fun signedAmountMetricTone(amount: BigDecimal): DashboardMetricTone =
    when (amount.signum()) {
        1 -> DashboardMetricTone.Inflow
        -1 -> DashboardMetricTone.Outflow
        else -> DashboardMetricTone.Neutral
    }
