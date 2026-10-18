// Test-only visual oracle copied from main 73952d6 before M9 extraction.
// No financial implementation or runtime dependency is copied here.
package com.baraa.masroof.instrumentation.m9reference

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CommitmentDashboardSource
import com.baraa.masroof.application.dashboard.CommitmentPaymentStatus
import com.baraa.masroof.application.dashboard.CommitmentsOverview
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofText
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.presentation.common.MasroofTextButton as TextButton
import com.baraa.masroof.presentation.dashboard.*
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate
import com.baraa.masroof.ui.components.MasroofOutlineIcons
import com.baraa.masroof.ui.theme.*
import com.baraa.masroof.ui.theme.MasroofFontWeights
import com.baraa.masroof.ui.theme.MasroofIconSizes
import com.baraa.masroof.ui.theme.MasroofShapes
import com.baraa.masroof.ui.theme.MasroofSpacing
import com.baraa.masroof.ui.theme.MasroofThemeExtras
import java.time.format.DateTimeFormatter

@Composable
fun MasroofMetric(label: String, value: String, color: Color,
    modifier: Modifier = Modifier, guidance: String? = null) {
    Surface(modifier, shape = MasroofComponentShapes.radius16,
        color = MasroofThemeExtras.extendedColors.cardSurface,
        border = BorderStroke(MasroofSpacing.borderWidth, MasroofThemeExtras.extendedColors.cardBorder)) {
        Column(Modifier.fillMaxWidth().padding(MasroofSpacing.compactCardContentPadding + MasroofSpacing.borderWidth)) {
            MasroofGuidanceLabel(label, guidance)
            Spacer(Modifier.height(MasroofReference.shortcutGap))
            MasroofReferenceMoney(value, style = MasroofReference.metricValue, color = color)
            Spacer(Modifier.height(MasroofSpacing.hairlineGap))
        }
    }
}

@Composable
fun MasroofPeriodPill(label: String, onPrevious: () -> Unit, onNext: () -> Unit,
    modifier: Modifier = Modifier, previousContentDescription: String? = null,
    nextContentDescription: String? = null, onCustomize: (() -> Unit)? = null,
    customizeLabel: String? = null, guidance: String? = null) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val previous = previousContentDescription ?: stringResource(R.string.dashboard_period_previous)
    val next = nextContentDescription ?: stringResource(R.string.dashboard_period_next)
    Surface(modifier = modifier.padding(bottom = MasroofSpacing.inlineGap).fillMaxWidth(), shape = MasroofShapes.small,
        border = BorderStroke(MasroofSpacing.borderWidth, MasroofThemeExtras.extendedColors.cardBorder)) {
        Column(Modifier.padding(MasroofSpacing.sectionHeaderGap)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = MasroofReference.periodHeaderHorizontal),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
                    Icon(MasroofOutlineIcons.CalendarDays, null, Modifier.size(MasroofReference.icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    MasroofText(stringResource(R.string.dashboard_salary_period_short),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    guidance?.let { MasroofGuidanceButton(label, it) }
                }
                if (onCustomize != null && customizeLabel != null) TextButton(onClick = onCustomize,
                    modifier = Modifier.heightIn(min = MasroofSpacing.minimumTouchTarget).testTag("dashboard-customize")) {
                    Icon(MasroofIcons.customize, null, Modifier.size(MasroofReference.icon))
                    MasroofText(customizeLabel, Modifier.padding(start = MasroofSpacing.smallGap),
                        style = MaterialTheme.typography.titleSmall)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(MasroofSpacing.minimumTouchTarget).testTag("period-previous")) {
                    Icon(if (rtl) MasroofOutlineIcons.ChevronRight else MasroofOutlineIcons.ChevronLeft, previous,
                        Modifier.size(MasroofReference.icon))
                }
                MasroofText(label, Modifier.weight(1f).testTag("period-range"), style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center)
                IconButton(onClick = onNext, modifier = Modifier.size(MasroofSpacing.minimumTouchTarget).testTag("period-next")) {
                    Icon(if (rtl) MasroofOutlineIcons.ChevronLeft else MasroofOutlineIcons.ChevronRight, next,
                        Modifier.size(MasroofReference.icon))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
    viewAllLabel: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MasroofIconSizes.sectionHeader),
            )
            MasroofSectionTitle(title = title, modifier = Modifier.fillMaxWidth())
        }
        if (onViewAll != null && viewAllLabel != null) {
            TextButton(onClick = onViewAll) {
                Text(
                    viewAllLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun MasroofChartScopeTabs(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MasroofShapes.small).background(MaterialTheme.colorScheme.background)
        .padding(MasroofReference.scopeTabInset).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MasroofReference.scopeTabInset)) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Box(Modifier.weight(1f).clip(RoundedCornerShape(MasroofReference.scopeTabRadius))
                .background(if (active) MasroofPalette.Primary else MaterialTheme.colorScheme.background)
                .selectable(active, role = Role.Tab, onClick = { onSelected(index) })
                .heightIn(min = MasroofSpacing.minimumTouchTarget)
                .padding(horizontal = MasroofReference.scopeTabInset, vertical = MasroofSpacing.sectionHeaderGap)
                .testTag("home-chart-scope-$index"), contentAlignment = Alignment.Center) {
                MasroofText(label, Modifier.fillMaxWidth(),
                    color = if (active) MasroofLightColors.OnPrimary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    fontWeight = if (active) MasroofFontWeights.bold else MasroofFontWeights.normal)
            }
        }
    }
}

@Composable
fun MasroofChartAverageLegend(label: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
        Canvas(Modifier.width(MasroofReference.averageLegendWidth).height(MasroofSpacing.smallGap)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                MasroofReference.averageLineStroke.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(MasroofReference.averageDash.toPx(), MasroofReference.averageDash.toPx())))
        }
        MasroofText(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
fun MasroofHorizontalBar(
    progress: Float,
    modifier: Modifier = Modifier,
    style: MasroofHorizontalBarStyle = MasroofHorizontalBarStyle.Default,
) {
    val extended = MasroofThemeExtras.extendedColors
    val fillColor = when (style) {
        MasroofHorizontalBarStyle.Default -> MaterialTheme.colorScheme.primary
        MasroofHorizontalBarStyle.Inflow -> extended.inflow
        MasroofHorizontalBarStyle.Outflow -> extended.outflow
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val clampedProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MasroofSpacing.accentBarHeight)
            .background(trackColor, MasroofShapes.small),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .height(MasroofSpacing.accentBarHeight)
                .background(fillColor, MasroofShapes.small),
        )
    }
}

@Composable
fun MasroofRankedBarRow(
    title: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    style: MasroofHorizontalBarStyle = MasroofHorizontalBarStyle.Default,
    onClick: (() -> Unit)? = null,
) {
    val extended = MasroofThemeExtras.extendedColors
    val valueColor = when (style) {
        MasroofHorizontalBarStyle.Default -> MaterialTheme.colorScheme.primary
        MasroofHorizontalBarStyle.Inflow -> extended.inflow
        MasroofHorizontalBarStyle.Outflow -> extended.outflow
    }

    MasroofCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MasroofFontWeights.semiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MasroofFontWeights.bold),
                    color = valueColor,
                )
            }
            MasroofHorizontalBar(
                progress = progress,
                style = style,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CommitmentsSection(overview: CommitmentsOverview, modifier: Modifier = Modifier,
    onOpenCredit: (() -> Unit)? = null) {
    if (!overview.hasContent) return
    val colors = MasroofThemeExtras.extendedColors
    val pending = overview.rows.filter { it.status == CommitmentPaymentStatus.UNPAID }
    Column(modifier.padding(top = com.baraa.masroof.ui.theme.MasroofReference.sectionHeadingTop - MasroofSpacing.sectionGap),
        verticalArrangement = Arrangement.spacedBy(com.baraa.masroof.ui.theme.MasroofReference.sectionHeadingBottom)) {
        Text(stringResource(R.string.reference_period_commitments), style = MaterialTheme.typography.titleMedium)
        androidx.compose.material3.Surface(color = colors.liabilitySoft,
            shape = com.baraa.masroof.ui.theme.MasroofComponentShapes.radius14) {
            Row(Modifier.fillMaxWidth().padding(com.baraa.masroof.ui.theme.MasroofReference.contextualBandPadding),
                horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap),
                verticalAlignment = Alignment.Top) {
                androidx.compose.material3.Icon(MasroofIcons.clock, null,
                    Modifier.size(com.baraa.masroof.ui.theme.MasroofReference.icon), tint = colors.liability)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reference_waiting_payment, formatLocalizedMoney(overview.remaining)),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = MasroofFontWeights.semiBold))
                    pending.take(2).forEach { row ->
                        Text(row.displayName + (row.expectedDate?.let {
                            " · " + java.time.format.DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0]).format(it)
                        } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (onOpenCredit != null && pending.any { it.source == CommitmentDashboardSource.CREDIT_CARD }) {
                        com.baraa.masroof.presentation.common.MasroofTextButton(onClick = onOpenCredit) {
                            Text(stringResource(R.string.reference_card_details), style = MaterialTheme.typography.bodySmall, color = colors.liability)
                        }
                    }
                }
            }
        }
    }
}

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

@Composable
fun MasroofAppBar(
    title: String,
    modifier: Modifier = Modifier,
    showLogo: Boolean = true,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val reference = com.baraa.masroof.ui.theme.MasroofReference
    Surface(modifier.fillMaxWidth(), color = if (showLogo) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(
            start = if (showLogo) reference.headerHorizontal else MasroofSpacing.screenHorizontal,
            end = if (showLogo) reference.headerHorizontal else MasroofSpacing.screenHorizontal,
            top = if (showLogo) reference.headerTop else MasroofSpacing.screenVertical,
            bottom = reference.headerBottom),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
            if (onBack != null) BackNavigationIcon(onBack, backContentDescription)
            if (showLogo) MasroofLogo(size = MasroofIconSizes.logo, contentDescription = null)
            Text(title, Modifier.weight(1f),
                style = if (showLogo) reference.brand else MaterialTheme.typography.headlineSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}
