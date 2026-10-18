package com.baraa.masroof.lab.beta.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.value.Money
import com.baraa.masroof.ui.components.*
import com.baraa.masroof.ui.dashboard.*
import com.baraa.masroof.ui.theme.*
import java.time.format.DateTimeFormatter

/** Formatted facts only, using the same hero, detail rows and geometry as production. */
@Composable
internal fun BetaEntityDetail(snapshot: DashboardSnapshot, id: AssetId, enabled: Boolean, onSelect: (AssetId) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val unavailable = stringResource(R.string.beta_dash_unavailable)
    fun money(value: SignedAmount?) = value?.let { MasroofMoneyDisplay.format(it.amount, it.currency.name, locale.toLanguageTag()) } ?: unavailable
    fun money(value: Money?) = value?.let { MasroofMoneyDisplay.format(it.amount, it.currency.name, locale.toLanguageTag()) } ?: unavailable
    fun date(value: java.time.Instant?) = value?.let { DateTimeFormatter.ofPattern("d MMM", locale).format(it.atZone(snapshot.query.zone)) } ?: unavailable
    val account = snapshot.accounts.singleOrNull { it.id == id }
    val debit = snapshot.debitCards.singleOrNull { it.cardId == id }
    val facility = snapshot.creditFacilities.singleOrNull { it.facilityId == id }
    val cardFacility = snapshot.creditFacilities.singleOrNull { it.cards.any { card -> card.cardId == id } }
    val card = cardFacility?.cards?.singleOrNull { it.cardId == id }
    val loan = snapshot.loans.singleOrNull { it.id == id }
    val subtitle = if (id.bank.value == "BANK_ALJAZIRA") stringResource(R.string.beta_bank_aljazira) else unavailable
    val colors = MasroofThemeExtras.extendedColors
    when {
        account != null -> {
            MasroofHeroCard((account.label ?: stringResource(R.string.beta_asset_account)) + " · " + stringResource(R.string.beta_home_remaining),
                money(account.flow?.remaining), subtitle, MasroofHeroRole.Account, icon = MasroofOutlineIcons.Wallet)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofReference.metricGap)) {
                DashboardMetric(money(account.flow?.totalIn), colors.inflow, Modifier.weight(1f)) { Text(stringResource(R.string.beta_dash_inflow), style = MaterialTheme.typography.bodySmall) }
                DashboardMetric(money(account.flow?.totalOut), colors.outflow, Modifier.weight(1f)) { Text(stringResource(R.string.beta_dash_outflow), style = MaterialTheme.typography.bodySmall) }
            }
            BetaEntityTrend(snapshot, id)
            MasroofCard {
                DetailValue(R.string.beta_dash_balance, money(account.balance))
                DetailValue(R.string.beta_dash_as_of_label, date(account.balanceAsOf))
                account.flow?.buckets?.forEach { (bucket, value) ->
                    DashboardDetailRow(label = { Text(stringResource(entityFlowLabel(bucket)), it, style = MasroofReference.pair) }, value = money(value))
                }
            }
        }
        debit != null -> DashboardEntityDetail(hero = {
            MasroofHeroCard(debit.label ?: stringResource(R.string.beta_asset_debit), money(debit.spending), subtitle,
                MasroofHeroRole.Account, icon = MasroofOutlineIcons.Wallet)
        }) {
            Text(stringResource(R.string.beta_detail_data), style = MaterialTheme.typography.titleMedium)
            DetailValue(R.string.beta_asset_last4, debit.last4 ?: unavailable)
            DetailValue(R.string.beta_detail_account, snapshot.accounts.singleOrNull { it.id == debit.accountId }?.label ?: unavailable)
            val trend = BetaDashboardExplorer.trend(snapshot, BetaChartChoice(TrendScope.ASSET, asset = debit.cardId))
            DetailValue(R.string.beta_detail_before_refunds, money(trend?.totalBeforeRefunds))
            DetailValue(R.string.beta_dash_refunds, money(trend?.totalRefunds), colors.inflow)
            DetailValue(R.string.beta_dash_salary_spending, money(debit.spending))
            EntityPeriod(debit.salaryPeriod)
        }
        card != null -> DashboardEntityDetail(hero = {
            MasroofHeroCard((card.label ?: stringResource(R.string.beta_asset_credit)) + " · " + stringResource(R.string.beta_dash_available),
                money(card.availableBalance), subtitle, MasroofHeroRole.Card, icon = MasroofOutlineIcons.CreditCard)
        }) {
            Text(stringResource(R.string.beta_detail_data), style = MaterialTheme.typography.titleMedium)
            DetailValue(R.string.beta_asset_last4, card.last4 ?: unavailable)
            DetailValue(R.string.beta_dash_due, money(cardFacility.statementDue), colors.liability)
            Text(stringResource(R.string.beta_detail_shared_due), style = MaterialTheme.typography.bodySmall)
            DetailValue(R.string.beta_dash_as_of_label, date(card.balanceAsOf))
            DetailValue(R.string.beta_dash_due_date, cardFacility.dueDate?.format(DateTimeFormatter.ofPattern("d MMM", locale)) ?: unavailable)
            DetailValue(R.string.beta_dash_calendar_spending, money(card.calendarSpending?.net))
            DetailValue(R.string.beta_dash_salary_spending, money(card.salarySpending?.net)); EntityPeriod(card.salarySpending?.period)
            DetailValue(R.string.beta_dash_statement_spending, money(card.statementSpending?.net)); EntityPeriod(card.statementSpending?.period)
        }
        facility != null -> DashboardEntityDetail(hero = {
            MasroofHeroCard(stringResource(R.string.beta_asset_facility), money(facility.statementDue), subtitle,
                MasroofHeroRole.Card, icon = MasroofOutlineIcons.CreditCard)
        }) {
            DetailValue(R.string.beta_dash_salary_spending, money(facility.salarySpending))
            DetailValue(R.string.beta_dash_statement_spending, money(facility.statementSpending))
            facility.cards.forEach { member ->
                OutlinedButton(enabled = enabled, onClick = { onSelect(member.cardId) }, modifier = Modifier.testTag("dash-asset-${member.cardId.bank.value}-${member.cardId.localId}")) {
                    Text(member.label ?: member.last4 ?: stringResource(R.string.beta_asset_credit))
                }
            }
        }
        loan != null -> DashboardEntityDetail(hero = {
            MasroofHeroCard((loan.label.takeIf { it.isNotBlank() } ?: stringResource(R.string.beta_asset_loan)) + " · " + stringResource(R.string.beta_dash_loan_remaining),
                money(loan.remaining), subtitle, MasroofHeroRole.Liability, icon = MasroofOutlineIcons.Wallet)
        }) {
            Text(stringResource(R.string.beta_detail_data), style = MaterialTheme.typography.titleMedium)
            DetailValue(R.string.beta_detail_loan_type, stringResource(when (loan.type) {
                "PERSONAL" -> R.string.beta_detail_personal
                "AUTO" -> R.string.beta_detail_auto
                "MORTGAGE" -> R.string.beta_detail_mortgage
                else -> R.string.beta_dash_unavailable
            }))
            DetailValue(R.string.beta_dash_installment, money(loan.latestInstallment), colors.liability)
            DetailValue(R.string.beta_dash_as_of_label, date(loan.installmentAsOf))
            DetailValue(R.string.beta_dash_balance_date, date(loan.remainingAsOf))
            DetailValue(R.string.beta_dash_salary_payment, money(loan.salaryPayment)); EntityPeriod(loan.salaryPeriod)
        }
        else -> Text(stringResource(R.string.beta_dash_missing), Modifier.testTag("dash-missing"))
    }
}

@Composable
private fun DetailValue(label: Int, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    DashboardDetailRow(label = { Text(stringResource(label), it, style = MasroofReference.pair) }, value = value, valueColor = color)
}

@Composable
private fun EntityPeriod(period: DisplayPeriod?) {
    Text(if (period == null) stringResource(R.string.beta_dash_period_unavailable) else
        stringResource(R.string.beta_dash_period, period.from.atZone(period.zone).toLocalDate().toString(), period.until.atZone(period.zone).toLocalDate().toString()),
        style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BetaEntityTrend(snapshot: DashboardSnapshot, id: AssetId) {
    val trend = BetaDashboardExplorer.trend(snapshot, BetaChartChoice(TrendScope.ASSET, asset = id))
    val visual = trend?.let(BetaDashboardVisualMapper::trend)
    var selected by rememberSaveable(id.toString(), snapshot.query.toString()) { mutableIntStateOf(-1) }
    MasroofCard {
        Text(stringResource(R.string.beta_home_all_expenses), style = MaterialTheme.typography.titleMedium)
        if (visual == null || visual.values.isEmpty()) Text(stringResource(R.string.beta_dash_unavailable_help)) else
            MasroofInteractiveLineChart(visual.values, visual.average ?: java.math.BigDecimal.ZERO,
                selectedPointIndex = selected.takeIf { it in visual.values.indices } ?: visual.values.lastIndex, labels = LineChartLabels(stringResource(R.string.beta_chart_day_axis),
                    stringResource(R.string.beta_chart_previous_point), stringResource(R.string.beta_chart_next_point), stringResource(R.string.beta_chart_day_axis)),
                pointLabel = { visual.dates[it] + " · " + visual.values[it].toPlainString() + " " + visual.currency },
                axisLabel = { visual.dates[it].takeLast(2) }, onPointSelected = { selected = it }, showAverage = visual.average != null)
    }
}

private fun entityFlowLabel(bucket: FlowBucket): Int = when (bucket) {
    FlowBucket.SALARY, FlowBucket.OTHER_INCOME -> R.string.beta_review_income
    FlowBucket.EXTERNAL_IN -> R.string.beta_review_transfer_in
    FlowBucket.ACCOUNT_REFUNDS -> R.string.beta_review_refund
    FlowBucket.INTERNAL_IN, FlowBucket.INTERNAL_OUT -> R.string.beta_review_self_transfer
    FlowBucket.EXTERNAL_OUT -> R.string.beta_review_transfer_out
    FlowBucket.CREDIT_SETTLEMENT -> R.string.beta_review_card_payment
    FlowBucket.CASH_WITHDRAWAL -> R.string.beta_review_cash
    FlowBucket.BILLS -> R.string.beta_review_bill
    FlowBucket.POS_PURCHASES -> R.string.beta_review_expense
    FlowBucket.FEES -> R.string.beta_review_fee
    FlowBucket.LOAN -> R.string.beta_review_loan
}
