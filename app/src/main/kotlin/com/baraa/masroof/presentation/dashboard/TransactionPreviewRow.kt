package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.baraa.masroof.presentation.common.MasroofText
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate
import com.baraa.masroof.ui.theme.*
@Composable
fun TransactionPreviewRow(
    row: TransactionPreviewUi,
    modifier: Modifier = Modifier,
    ownedCards: List<OwnedCardUi> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    val colors = MasroofThemeExtras.extendedColors
    val inward = row.direction in listOf(TransactionDirectionUi.INCOME, TransactionDirectionUi.INWARD, TransactionDirectionUi.TRANSFER_IN)
    val transfer = row.direction == TransactionDirectionUi.NEUTRAL
    val (color, background) = when (row.type) {
        com.baraa.masroof.domain.model.FinancialTransactionType.LOAN_REPAYMENT -> colors.liability to colors.liabilitySoft
        com.baraa.masroof.domain.model.FinancialTransactionType.CREDIT_CARD_PAYMENT -> colors.card to colors.cardSoft
        else -> if (inward) colors.inflow to colors.inflowSoft else if (transfer) colors.account to colors.accountSoft else colors.outflow to colors.outflowSoft
    }
    val title = row.title?.takeIf { it.isNotBlank() } ?: transactionTypeLabel(row.typeLabelResHint)
    val money = formatLocalizedMoney(row.amount)
    val formatted = money.substringBeforeLast(' ')
    val currency = money.substringAfterLast(' ')
    val sign = if (inward) "+" else if (transfer) "" else "−"
    Column(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(vertical = MasroofReference.rowVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofReference.rowGap)) {
            Surface(shape = MasroofShapes.small, color = background) {
                Box(Modifier.size(MasroofReference.iconBox), contentAlignment = Alignment.Center) {
                    Icon(MasroofIcons.transactionType(row.typeLabelResHint), null, Modifier.size(MasroofReference.icon), tint = color)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.hairlineGap)) {
                MasroofText(title, style = MasroofReference.pair.copy(fontWeight = MasroofFontWeights.semiBold))
                MasroofText(transactionTypeLabel(row.typeLabelResHint) +
                    (cardDisplayLabelFromTransaction(row, ownedCards)?.let { " · " + it } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                MasroofText(sign + formatted, style = MasroofReference.rowAmount, color = color)
                MasroofText(formatLocalizedTransactionDate(row.localDate) + " · " + currency,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = colors.cardBorder)
    }
}
