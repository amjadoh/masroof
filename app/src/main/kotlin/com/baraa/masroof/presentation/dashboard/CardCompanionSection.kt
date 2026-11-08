package com.baraa.masroof.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.baraa.masroof.R
import com.baraa.masroof.application.credit.CardCompanionWorkflow
import com.baraa.masroof.application.dashboard.CompanionBill
import com.baraa.masroof.application.dashboard.CompanionPlanProgress
import com.baraa.masroof.application.dashboard.CompanionPosition
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.MasroofBadge
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofGuidanceLabel
import com.baraa.masroof.presentation.common.MasroofText
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.ui.theme.MasroofCardFace
import com.baraa.masroof.ui.theme.MasroofFontWeights
import com.baraa.masroof.ui.theme.MasroofReference
import com.baraa.masroof.ui.theme.MasroofSpacing
import com.baraa.masroof.ui.theme.MasroofThemeExtras
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Provided by MainActivity so the card detail screen can host the companion without threading it through navigation. */
val LocalCardCompanionViewModel = staticCompositionLocalOf<CardCompanionViewModel?> { null }

/**
 * The SAB Visa tracker, inside Masroof: card face, what to pay and by when, installment months
 * and the last verified statement. Every number comes from [CompanionPosition]; nothing is summed here.
 */
@Composable
fun CardCompanionSection(row: CreditCardDashboardRow, modifier: Modifier = Modifier) {
    val viewModel = LocalCardCompanionViewModel.current ?: return
    LaunchedEffect(row.bank, row.last4, row.snapshot) { viewModel.load(row) }
    val state by viewModel.uiState.collectAsState()
    val snapshot = state.snapshot?.takeIf { it.cardKey == CardCompanionWorkflow.cardKey(row.bank, row.last4) }
    if (snapshot == null) {
        if (state.loading) {
            Box(modifier.fillMaxWidth().padding(MasroofSpacing.sectionGap), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }
    val position = snapshot.position
    val today = LocalDate.now(ZoneId.systemDefault())
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        CompanionCardFace(row, position)
        if (position.limit == null || !snapshot.profile.closeDayConfirmed) {
            CardSetupCard(
                hasLimit = position.limit != null,
                initialDay = snapshot.profile.closeDay,
                onSave = viewModel::setup,
            )
        }
        if (position.limit != null) {
            PayHeadline(position, today)
        }
        val active = position.plans.filterNot { it.finished }
        if (active.isNotEmpty()) {
            MasroofText(
                stringResource(R.string.cc_plans_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = MasroofReference.sectionHeadingTop),
            )
            active.forEach { PlanMonths(it, onDismiss = { viewModel.dismissPlan(it.plan.id) }) }
        }
        StatementCard(position.latestBill)
        CompanionSettings(
            closeDay = snapshot.profile.closeDay,
            hasLimit = position.limit != null,
            onCloseDay = viewModel::setCloseDay,
            onLimit = viewModel::setLimit,
        )
    }
}

/* ---------- Card face ---------- */

@Composable
private fun CompanionCardFace(row: CreditCardDashboardRow, position: CompanionPosition) {
    var showUsed by rememberSaveable(row.bank.id, row.last4) { mutableStateOf(false) }
    val shade by animateFloatAsState(position.usedShare, label = "used-share")
    val firstPlan = position.plans.firstOrNull { !it.finished }
    val availableLabel = position.available?.let { money(it) } ?: "—"
    val description = stringResource(R.string.cc_card_description, availableLabel, position.limit?.let { money(it) } ?: "—")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(MasroofCardFace.aspectRatio)
            .clip(RoundedCornerShape(MasroofCardFace.corner))
            .background(MasroofCardFace.ink)
            .clickable { showUsed = !showUsed }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("companion-card-face"),
    ) {
        // Used share is drawn as a lighter band growing from the trailing edge: the card is the chart.
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shade)
                .align(Alignment.CenterEnd)
                .background(MasroofCardFace.usedShade),
        )
        Column(
            Modifier.fillMaxSize().padding(MasroofCardFace.padding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                MasroofText(
                    if (CardCompanionViewModel.isSab(row.bank)) "SAB Visa" else stringResource(R.string.cc_credit_card),
                    color = MasroofCardFace.onInk,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = MasroofFontWeights.bold,
                )
                Box(
                    Modifier
                        .size(MasroofCardFace.chipWidth, MasroofCardFace.chipHeight)
                        .clip(RoundedCornerShape(MasroofSpacing.sectionHeaderGap))
                        .background(MasroofCardFace.gold),
                )
            }
            Crossfade(targetState = showUsed, label = "card-face") { used ->
                Column {
                    val value = if (used) position.used?.let { money(it) } else position.available?.let { money(it) }
                    MasroofText(
                        value ?: "—",
                        color = MasroofCardFace.onInk,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = MasroofFontWeights.bold,
                    )
                    val limit = position.limit?.let { money(it) }
                    val caption = when {
                        position.available == null -> stringResource(R.string.cc_waiting_balance)
                        limit == null -> stringResource(R.string.cc_available_no_limit)
                        used -> stringResource(R.string.cc_used_of, limit)
                        else -> stringResource(R.string.cc_available_of, limit)
                    }
                    MasroofText(
                        caption + if (position.availableEstimated) " • " + stringResource(R.string.cc_estimated) else "",
                        color = MasroofCardFace.onInkMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (firstPlan != null) {
                    PlanDots(firstPlan, Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                MasroofText(
                    "•••• ${row.last4}",
                    color = MasroofCardFace.onInkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PlanDots(progress: CompanionPlanProgress, modifier: Modifier) {
    val plan = progress.plan
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MasroofCardFace.dotGap)) {
        if (plan.months <= MAX_DOTS) {
            repeat(plan.months) { i ->
                Box(
                    Modifier.size(MasroofCardFace.dot).clip(CircleShape)
                        .background(if (i < progress.billed) MasroofCardFace.gold else MasroofCardFace.onInkFaint),
                )
            }
        } else {
            LinearProgressIndicator(
                progress = { progress.billed.toFloat() / plan.months },
                modifier = Modifier.weight(1f),
                color = MasroofCardFace.gold,
                trackColor = MasroofCardFace.onInkFaint,
            )
        }
        MasroofText(
            "${progress.billed} / ${plan.months}",
            color = MasroofCardFace.onInkMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = MasroofSpacing.sectionHeaderGap),
        )
    }
}

/* ---------- What to pay ---------- */

@Composable
private fun PayHeadline(position: CompanionPosition, today: LocalDate) {
    var explain by rememberSaveable { mutableStateOf(false) }
    val extended = MasroofThemeExtras.extendedColors
    val toPay = position.toPay
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { explain = !explain }
            .padding(vertical = MasroofReference.metadataGap).testTag("companion-pay"),
        verticalArrangement = Arrangement.spacedBy(MasroofReference.metadataGap),
    ) {
        val headline = when {
            toPay == null -> stringResource(R.string.cc_waiting_balance)
            (position.amountDue ?: 0L) < 0L -> stringResource(R.string.cc_nothing_to_pay_credit, money(-(position.amountDue ?: 0L)))
            toPay == 0L -> stringResource(R.string.cc_nothing_to_pay)
            else -> stringResource(R.string.cc_pay_before, money(toPay), shortDate(position.payBy))
        }
        MasroofText(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = MasroofFontWeights.semiBold)
        if (toPay != null && toPay > 0) {
            MasroofText(
                daysLeft(today, position.payBy) + " • " + stringResource(R.string.cc_tap_for_details),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompositionBar(installment = position.installmentDue ?: 0, purchases = position.purchasesDue ?: 0)
        }
    }
    AnimatedVisibility(explain) {
        MasroofCard {
            val used = position.used
            if (used != null) {
                Explain(stringResource(R.string.cc_explain_used), money(used))
            }
            val unbilled = position.latestBill?.unbilledPrincipal ?: position.plans.sumOf { it.remainingPrincipal }
            Explain(stringResource(R.string.cc_explain_unbilled), money(unbilled))
            Explain(stringResource(R.string.cc_installment_due), money(position.installmentDue ?: 0))
            Explain(stringResource(R.string.cc_purchases), money(position.purchasesDue ?: 0))
            if ((position.credit ?: 0) > 0) Explain(stringResource(R.string.cc_credit), money(position.credit ?: 0), extended.inflow)
            MasroofText(
                stringResource(if (position.verified) R.string.cc_explain_verified else R.string.cc_explain_estimate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MasroofSpacing.sectionHeaderGap),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
        val installmentBy = position.installmentBy
        StatChip(
            label = stringResource(R.string.cc_installment_due),
            dot = MasroofCardFace.gold,
            value = position.installmentDue,
            pill = when {
                (position.installmentDue ?: 0) == 0L -> stringResource(R.string.cc_none)
                installmentBy == null -> stringResource(R.string.cc_before_close)
                installmentBy.isBefore(today) -> stringResource(R.string.cc_overdue)
                else -> stringResource(R.string.cc_before, shortDate(installmentBy))
            },
            warn = (position.installmentDue ?: 0) > 0,
        )
        StatChip(
            label = stringResource(R.string.cc_purchases),
            dot = MaterialTheme.colorScheme.primary,
            value = position.purchasesDue,
            pill = stringResource(R.string.cc_goal, shortDate(position.purchaseGoal)),
        )
        StatChip(
            label = stringResource(R.string.cc_credit),
            dot = extended.inflow,
            value = position.credit,
            pill = stringResource(if ((position.credit ?: 0) > 0) R.string.cc_in_your_favor else R.string.cc_none),
        )
    }
}

@Composable
private fun CompositionBar(installment: Long, purchases: Long) {
    val total = installment + purchases
    if (total <= 0) return
    Row(
        Modifier.fillMaxWidth().padding(top = MasroofSpacing.sectionHeaderGap).height(MasroofCardFace.compositionBar)
            .clip(RoundedCornerShape(MasroofCardFace.compositionBar)),
    ) {
        if (installment > 0) Box(Modifier.weight(installment.toFloat()).fillMaxHeight().background(MasroofCardFace.gold))
        if (purchases > 0) Box(Modifier.weight(purchases.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun Explain(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = MasroofReference.metadataGap), horizontalArrangement = Arrangement.SpaceBetween) {
        MasroofText(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MasroofText(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = MasroofFontWeights.medium)
    }
}

@Composable
private fun RowScope.StatChip(label: String, dot: Color, value: Long?, pill: String, warn: Boolean = false) {
    val extended = MasroofThemeExtras.extendedColors
    Column(
        Modifier.weight(1f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)
            .border(MasroofSpacing.borderWidth, extended.cardBorder, MaterialTheme.shapes.large)
            .padding(horizontal = MasroofSpacing.sectionHeaderGap + MasroofReference.metadataGap, vertical = MasroofReference.rowVertical),
        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MasroofReference.metadataGap + MasroofReference.metadataGap)) {
            Box(Modifier.size(MasroofCardFace.legendDot).clip(CircleShape).background(dot))
            MasroofText(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        MasroofText(value?.let { money(it) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = MasroofFontWeights.bold, maxLines = 1)
        MasroofBadge(pill, if (warn) MasroofCardAccent.Liability else MasroofCardAccent.Credit)
    }
}

/* ---------- Installment months ---------- */

@Composable
private fun PlanMonths(progress: CompanionPlanProgress, onDismiss: () -> Unit) {
    val plan = progress.plan
    var selected by remember(plan.id) { mutableIntStateOf(-1) }
    MasroofCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MasroofText(plan.name, style = MaterialTheme.typography.titleSmall, fontWeight = MasroofFontWeights.bold, maxLines = 1, modifier = Modifier.weight(1f))
            MasroofText(
                stringResource(R.string.cc_last_installment, monthLabel(plan.billingDate(plan.months - 1))),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.padding(vertical = MasroofSpacing.sectionGap), verticalArrangement = Arrangement.spacedBy(MasroofCardFace.dotGap + MasroofCardFace.dotGap / 2)) {
            (0 until plan.months).chunked(MONTHS_PER_ROW).forEach { rowIndices ->
                Row(horizontalArrangement = Arrangement.spacedBy(MasroofCardFace.dotGap + MasroofCardFace.dotGap / 2)) {
                    rowIndices.forEach { i -> MonthCell(progress, i, selected == i) { selected = if (selected == i) -1 else i } }
                    repeat(MONTHS_PER_ROW - rowIndices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        AnimatedVisibility(selected >= 0) {
            val i = selected.coerceAtLeast(0)
            val text = when {
                i < progress.billed -> stringResource(R.string.cc_month_billed, i + 1, money(plan.installment(i)), monthLabel(plan.billingDate(i)))
                i == progress.billed -> stringResource(R.string.cc_month_current, i + 1, money(plan.installment(i)), monthLabel(plan.billingDate(i)))
                else -> stringResource(R.string.cc_month_future, i + 1, money(plan.installment(i)), monthLabel(plan.billingDate(i)))
            }
            MasroofText(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = MasroofSpacing.sectionHeaderGap))
        }
        HorizontalDivider(color = MasroofThemeExtras.extendedColors.cardBorder)
        Row(Modifier.fillMaxWidth().padding(top = MasroofSpacing.sectionGap)) {
            PlanStat("${progress.billed}", stringResource(if (progress.verified) R.string.cc_billed_verified else R.string.cc_billed))
            PlanStat("${progress.left}", stringResource(R.string.cc_left))
            PlanStat(money(progress.remainingPrincipal), stringResource(R.string.cc_left_principal))
        }
        if (!progress.verified) {
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                MasroofText(stringResource(R.string.cc_not_my_plan), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RowScope.MonthCell(progress: CompanionPlanProgress, index: Int, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val state = when {
        index < progress.billed -> 0
        index == progress.billed -> 1
        else -> 2
    }
    val bg = when (state) {
        0 -> scheme.primary
        1 -> scheme.secondaryContainer
        else -> scheme.surfaceVariant
    }
    val fg = when (state) {
        0 -> scheme.onPrimary
        1 -> scheme.onSecondaryContainer
        else -> scheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(MasroofCardFace.monthCell)
    Box(
        Modifier.weight(1f).aspectRatio(1f).clip(shape).background(bg)
            .then(if (state == 1 || selected) Modifier.border(MasroofCardFace.monthBorder, if (selected) scheme.primary else MasroofCardFace.gold, shape) else Modifier)
            .clickable(onClick = onClick)
            .testTag("companion-month-${progress.plan.id}-${index + 1}"),
        contentAlignment = Alignment.Center,
    ) {
        MasroofText(
            monthLabel(progress.plan.billingDate(index)),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = if (state == 1) MasroofFontWeights.bold else MasroofFontWeights.normal,
            maxLines = 1,
        )
        if (state == 0) {
            MasroofText("✓", color = fg, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = MasroofReference.metadataGap))
        }
    }
}

@Composable
private fun RowScope.PlanStat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MasroofReference.metadataGap)) {
        MasroofText(value, style = MaterialTheme.typography.titleMedium, fontWeight = MasroofFontWeights.bold, maxLines = 1)
        MasroofText(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/* ---------- Statement ---------- */

@Composable
private fun StatementCard(bill: CompanionBill?) {
    MasroofCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MasroofText(stringResource(R.string.cc_statement_title), style = MaterialTheme.typography.titleMedium)
            MasroofBadge(
                stringResource(if (bill != null) R.string.cc_verified else R.string.cc_soon),
                if (bill != null) MasroofCardAccent.Inflow else MasroofCardAccent.Account,
            )
        }
        if (bill == null) {
            MasroofText(
                stringResource(R.string.cc_statement_invite),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MasroofSpacing.sectionHeaderGap),
            )
        } else {
            Explain(stringResource(R.string.cc_statement_total), money(bill.totalDue))
            Explain(stringResource(R.string.cc_statement_minimum), money(bill.minimumDue))
            Explain(stringResource(R.string.cc_statement_due), shortDate(bill.due))
            Explain(stringResource(R.string.cc_statement_issued), shortDate(bill.issued))
        }
    }
}

/* ---------- Setup ---------- */

@Composable
private fun LimitSetupCard(onSave: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    MasroofCard {
        MasroofGuidanceLabel(
            label = stringResource(R.string.cc_limit_title),
            message = stringResource(R.string.cc_limit_hint),
            modifier = Modifier.padding(bottom = MasroofSpacing.sectionHeaderGap),
            style = MaterialTheme.typography.titleMedium,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; invalid = false },
                modifier = Modifier.weight(1f).testTag("companion-limit-input"),
                singleLine = true,
                isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { if (invalid) MasroofText(stringResource(R.string.cc_limit_invalid)) },
            )
            Button(onClick = { invalid = !onSave(text) }) { MasroofText(stringResource(R.string.cc_limit_save)) }
        }
    }
}

/** Asks for the card's limit and statement day together, the first time the card is opened. */
@Composable
private fun CardSetupCard(hasLimit: Boolean, initialDay: Int, onSave: (String, Int) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(initialDay) }
    var invalid by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // The statement-day reminder needs the notification permission on Android 13+.
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    MasroofCard(modifier = Modifier.testTag("companion-setup")) {
        MasroofGuidanceLabel(
            label = stringResource(R.string.cc_setup_title),
            message = stringResource(R.string.cc_setup_hint),
            modifier = Modifier.padding(bottom = MasroofSpacing.sectionHeaderGap),
            style = MaterialTheme.typography.titleMedium,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        if (!hasLimit) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; invalid = false },
                modifier = Modifier.fillMaxWidth().testTag("companion-limit-input"),
                singleLine = true,
                isError = invalid,
                label = { MasroofText(stringResource(R.string.cc_limit_title)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { if (invalid) MasroofText(stringResource(R.string.cc_limit_invalid)) },
            )
        }
        CloseDayStepper(day = day, onDay = { day = it })
        Button(
            onClick = {
                invalid = !onSave(text, day)
                if (!invalid && android.os.Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
            modifier = Modifier.fillMaxWidth().testTag("companion-setup-save"),
        ) { MasroofText(stringResource(R.string.cc_limit_save)) }
    }
}

@Composable
private fun CloseDayStepper(day: Int, onDay: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MasroofText(stringResource(R.string.cc_close_day), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onDay(if (day <= 1) 31 else day - 1) }) { MasroofText("−") }
        MasroofText("$day", style = MaterialTheme.typography.titleMedium, fontWeight = MasroofFontWeights.bold,
            modifier = Modifier.width(MasroofReference.iconBox).testTag("companion-close-day"))
        TextButton(onClick = { onDay(if (day >= 31) 1 else day + 1) }) { MasroofText("+") }
    }
}

@Composable
private fun CompanionSettings(closeDay: Int, hasLimit: Boolean, onCloseDay: (Int) -> Unit, onLimit: (String) -> Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { MasroofText(stringResource(R.string.cc_settings)) }
    AnimatedVisibility(open) {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofCard { CloseDayStepper(day = closeDay, onDay = onCloseDay) }
            if (hasLimit) LimitSetupCard(onSave = onLimit)
        }
    }
}

/* ---------- Formatting ---------- */

private const val MAX_DOTS = 24
private const val MONTHS_PER_ROW = 6

@Composable
private fun money(halalas: Long): String {
    val text = formatLocalizedMoney(Money(BigDecimal.valueOf(kotlin.math.abs(halalas), 2), Currency.SAR))
    return if (halalas < 0) "−$text" else text
}

private fun shortDate(date: LocalDate): String = "${date.dayOfMonth}/${date.monthValue}"

private fun monthLabel(date: LocalDate): String = "${date.monthValue}/${date.year % 100}"

@Composable
private fun daysLeft(today: LocalDate, date: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> stringResource(R.string.cc_overdue)
        days == 0L -> stringResource(R.string.cc_today)
        else -> stringResource(R.string.cc_days_left, days.toInt())
    }
}
