package com.baraa.masroof.lab.finance.classification

import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.asset.AssetKind
import com.baraa.masroof.lab.finance.asset.OwnedAsset
import com.baraa.masroof.lab.finance.dashboard.FlowBucket
import com.baraa.masroof.lab.finance.dashboard.SignedAmount
import com.baraa.masroof.lab.finance.dashboard.SummaryMetric
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.Money
import java.math.BigDecimal

/** External is an explicit resolved decision; an absent/ambiguous owner is Unresolved. */
sealed interface FinancialEndpoint {
    data class Owned(val id: AssetId) : FinancialEndpoint
    data object External : FinancialEndpoint
    data object Unresolved : FinancialEndpoint
}

/** Established from persisted facts by the caller; absence never guesses salary/ordinary income. */
enum class IncomeAttribution { SALARY, OTHER }

/** Resolved financial input, never a bank message family or unvalidated parser result.
 * Registry entries must come from confirmed ownership. No suffix matching occurs here.
 * financingLoan is already attributed from linked FINANCING_INSTALLMENT + loan-type evidence.
 */
data class FinancialClassificationInput(
    val key: PostingKey,
    val revision: PostingRevision,
    val storedType: FinancialType,
    val amount: Money,
    val time: TransactionTime,
    val source: FinancialEndpoint,
    val destination: FinancialEndpoint,
    val assets: Map<AssetId, OwnedAsset>,
    val evidenceIds: Set<String>,
    val validatedParse: Boolean,
    val authority: CorrectionAuthority = CorrectionAuthority.NONE,
    val unresolved: Set<ReviewReason> = emptySet(),
    val financingLoan: AssetId? = null,
    val appliedFx: AppliedFx? = null,
    val incomeAttribution: IncomeAttribution? = null,
)

enum class Settlement { NONE, CREDIT, LOAN }

/** Native-currency amounts only; missing FX never silently becomes SAR or zero. */
data class AccountMovement(val account: AssetId, val bucket: FlowBucket, val amount: Money)

sealed interface FinancialClassification {
    /** No impact/PostingCommand is exposed for a blocked candidate. */
    data class Review(val reasons: Set<ReviewReason>) : FinancialClassification
    data class Classified(
        val input: FinancialClassificationInput,
        val effectiveType: FinancialType,
        val settlement: Settlement,
        val metrics: Map<SummaryMetric, SignedAmount>,
        val accountMovements: List<AccountMovement>,
    ) : FinancialClassification
}

/** M8.1 pure classification only: no posting, deduplication, matching, parsing or persistence.
 * Monthly NET_CASH_FLOW and account cash movements intentionally preserve different legacy metrics.
 */
object FinancialImpactClassifier {
    fun classify(candidate: FinancialClassificationInput): FinancialClassification {
        // Freeze caller-owned collections before validation and keep the same validated snapshot.
        val input = candidate.copy(assets = candidate.assets.toMap(), evidenceIds = candidate.evidenceIds.toSet(),
            unresolved = candidate.unresolved.toSet())
        val blockers = input.unresolved.toMutableSet()
        if (input.evidenceIds.isEmpty() || input.evidenceIds.any(String::isBlank)) blockers += ReviewReason.VALIDATION
        val source = resolve(input.source, input, blockers)
        val destination = resolve(input.destination, input, blockers)
        val sourceAccount = account(source, input, blockers)
        val destinationAccount = account(destination, input, blockers)
        val usesFinancingEvidence = input.storedType in setOf(FinancialType.FEE, FinancialType.LOAN_REPAYMENT)
        val loan = input.financingLoan?.takeIf { usesFinancingEvidence }?.let { input.assets[it] }
        if (usesFinancingEvidence && input.financingLoan != null && (loan?.kind != AssetKind.LOAN ||
                loan.id != input.financingLoan || loan.id.environment != input.key.environment ||
                (source != null && loan.id.bank != source.id.bank))
        ) blockers += ReviewReason.VALIDATION
        // Explicit reclassification to another stored type wins over old financing evidence.
        val type = if (input.storedType == FinancialType.FEE && input.financingLoan != null) {
            FinancialType.LOAN_REPAYMENT
        } else input.storedType
        when (type) {
            FinancialType.EXPENSE, FinancialType.FEE, FinancialType.BILL_PAYMENT,
            FinancialType.CASH_WITHDRAWAL -> {
                if (source == null) blockers += ReviewReason.OWNERSHIP
                if (sourceAccount == null && source?.kind !in setOf(AssetKind.CREDIT_CARD, AssetKind.CREDIT_FACILITY)) {
                    blockers += ReviewReason.VALIDATION
                }
                if (input.destination != FinancialEndpoint.External) blockers += ReviewReason.AMBIGUOUS_MATCH
            }
            FinancialType.INCOME, FinancialType.EXTERNAL_TRANSFER_IN -> {
                if (destinationAccount == null) blockers += ReviewReason.OWNERSHIP
                if (input.source != FinancialEndpoint.External) blockers += ReviewReason.AMBIGUOUS_MATCH
                if (input.incomeAttribution == null) blockers += ReviewReason.VALIDATION
            }
            FinancialType.EXTERNAL_TRANSFER_OUT -> {
                if (sourceAccount == null) blockers += ReviewReason.OWNERSHIP
                if (input.destination != FinancialEndpoint.External) blockers += ReviewReason.AMBIGUOUS_MATCH
            }
            FinancialType.CREDIT_CARD_PAYMENT, FinancialType.LOAN_REPAYMENT -> {
                if (sourceAccount == null) blockers += ReviewReason.OWNERSHIP
                val validTarget = if (type == FinancialType.CREDIT_CARD_PAYMENT) {
                    destination?.kind in setOf(AssetKind.CREDIT_CARD, AssetKind.CREDIT_FACILITY)
                } else destination?.kind == AssetKind.LOAN ||
                    (input.destination == FinancialEndpoint.External && loan != null)
                if (!validTarget) blockers += ReviewReason.OWNERSHIP
                if (loan != null && destination != null && destination.id != loan.id) blockers += ReviewReason.VALIDATION
            }
            FinancialType.SELF_TRANSFER -> {
                if (source?.kind != AssetKind.ACCOUNT || destination?.kind != AssetKind.ACCOUNT) {
                    blockers += ReviewReason.OWNERSHIP
                }
                if (source?.id == destination?.id) blockers += ReviewReason.AMBIGUOUS_MATCH
            }
            FinancialType.REFUND -> {
                if (destination == null) blockers += ReviewReason.OWNERSHIP
                if (destinationAccount == null && destination?.kind !in setOf(AssetKind.CREDIT_CARD, AssetKind.CREDIT_FACILITY)) {
                    blockers += ReviewReason.VALIDATION
                }
                if (input.source != FinancialEndpoint.External) blockers += ReviewReason.AMBIGUOUS_MATCH
            }
            FinancialType.ADJUSTMENT -> blockers += ReviewReason.UNKNOWN_TYPE // No invented adjustment direction.
            FinancialType.UNKNOWN -> Unit
        }
        val gate = PostingGate.evaluate(input.validatedParse, input.authority, type, blockers)
        if (gate is PostingEligibility.Review) return FinancialClassification.Review(gate.reasons)

        val amounts = SummaryMetric.entries.associateWith { BigDecimal("0.00") }.toMutableMap()
        fun metric(metric: SummaryMetric, sign: Int = 1) { amounts[metric] = input.amount.amount.multiply(BigDecimal(sign)) }
        val movements = mutableListOf<AccountMovement>()
        fun out(bucket: FlowBucket) { sourceAccount?.let { movements += AccountMovement(it.id, bucket, input.amount) } }
        fun incoming(bucket: FlowBucket) { destinationAccount?.let { movements += AccountMovement(it.id, bucket, input.amount) } }
        val creditSource = source?.kind in setOf(AssetKind.CREDIT_CARD, AssetKind.CREDIT_FACILITY)
        val creditDestination = destination?.kind in setOf(AssetKind.CREDIT_CARD, AssetKind.CREDIT_FACILITY)
        val settlement = when (type) {
            FinancialType.EXPENSE, FinancialType.FEE, FinancialType.BILL_PAYMENT -> {
                metric(SummaryMetric.SPENDING_GROSS)
                if (creditSource) metric(SummaryMetric.CREDIT_PURCHASES)
                else out(when (type) {
                    FinancialType.FEE -> FlowBucket.FEES
                    FinancialType.BILL_PAYMENT -> FlowBucket.BILLS
                    else -> FlowBucket.POS_PURCHASES
                })
                Settlement.NONE
            }
            FinancialType.REFUND -> {
                metric(SummaryMetric.REFUNDS)
                if (creditDestination) metric(SummaryMetric.CREDIT_PURCHASES, -1)
                else incoming(FlowBucket.ACCOUNT_REFUNDS)
                Settlement.NONE
            }
            FinancialType.INCOME -> {
                metric(SummaryMetric.INCOME)
                // Preserved SalaryIncomeHeuristics: an explicit stored INCOME is always salary.
                // Attribution only selects the salary/ordinary bucket for inbound transfers.
                incoming(FlowBucket.SALARY)
                Settlement.NONE
            }
            FinancialType.EXTERNAL_TRANSFER_IN -> {
                metric(SummaryMetric.EXTERNAL_IN)
                incoming(if (input.incomeAttribution == IncomeAttribution.SALARY) FlowBucket.SALARY else FlowBucket.EXTERNAL_IN)
                Settlement.NONE
            }
            FinancialType.EXTERNAL_TRANSFER_OUT -> { metric(SummaryMetric.EXTERNAL_OUT); out(FlowBucket.EXTERNAL_OUT); Settlement.NONE }
            FinancialType.CREDIT_CARD_PAYMENT -> { metric(SummaryMetric.CREDIT_SETTLEMENTS); out(FlowBucket.CREDIT_SETTLEMENT); Settlement.CREDIT }
            FinancialType.LOAN_REPAYMENT -> { out(FlowBucket.LOAN); Settlement.LOAN }
            FinancialType.CASH_WITHDRAWAL -> { metric(SummaryMetric.CASH_WITHDRAWALS); out(FlowBucket.CASH_WITHDRAWAL); Settlement.NONE }
            FinancialType.SELF_TRANSFER -> {
                metric(SummaryMetric.SELF_TRANSFERS); out(FlowBucket.INTERNAL_OUT); incoming(FlowBucket.INTERNAL_IN); Settlement.NONE
            }
            FinancialType.ADJUSTMENT, FinancialType.UNKNOWN -> error("PostingGate must reject unresolved types")
        }
        amounts[SummaryMetric.SPENDING_NET] = amounts.getValue(SummaryMetric.SPENDING_GROSS) - amounts.getValue(SummaryMetric.REFUNDS)
        amounts[SummaryMetric.COMBINED_INCOMING] = amounts.getValue(SummaryMetric.INCOME) + amounts.getValue(SummaryMetric.EXTERNAL_IN)
        amounts[SummaryMetric.NET_CASH_FLOW] = amounts.getValue(SummaryMetric.COMBINED_INCOMING) -
            amounts.getValue(SummaryMetric.EXTERNAL_OUT) - amounts.getValue(SummaryMetric.SPENDING_NET)
        amounts[SummaryMetric.ACTUAL_ACCOUNT_SPENDING] = movements.filter { it.bucket in accountOutflow }.fold(BigDecimal("0.00")) { sum, row -> sum + row.amount.amount }
        return FinancialClassification.Classified(input, type, settlement,
            amounts.mapValues { SignedAmount(it.value, input.amount.currency) }, movements.toList())
    }

    private val accountOutflow = setOf(FlowBucket.EXTERNAL_OUT, FlowBucket.CREDIT_SETTLEMENT,
        FlowBucket.CASH_WITHDRAWAL, FlowBucket.BILLS, FlowBucket.POS_PURCHASES, FlowBucket.FEES, FlowBucket.LOAN)

    private fun resolve(endpoint: FinancialEndpoint, input: FinancialClassificationInput,
        blockers: MutableSet<ReviewReason>): OwnedAsset? = when (endpoint) {
        FinancialEndpoint.External -> null
        FinancialEndpoint.Unresolved -> { blockers += ReviewReason.OWNERSHIP; null }
        is FinancialEndpoint.Owned -> input.assets[endpoint.id]?.takeIf {
            it.id == endpoint.id && it.id.environment == input.key.environment
        } ?: run { blockers += ReviewReason.OWNERSHIP; null }
    }

    private fun account(asset: OwnedAsset?, input: FinancialClassificationInput,
        blockers: MutableSet<ReviewReason>): OwnedAsset? {
        if (asset == null) return null
        if (asset.kind == AssetKind.ACCOUNT) return asset
        if (asset.kind !in setOf(AssetKind.DEBIT_CARD, AssetKind.CREDIT_CARD)) return null
        val parent = asset.parentId?.let { input.assets[it] }
        val expected = if (asset.kind == AssetKind.DEBIT_CARD) AssetKind.ACCOUNT else AssetKind.CREDIT_FACILITY
        if (parent == null || parent.id != asset.parentId || parent.kind != expected ||
            parent.id.environment != input.key.environment || parent.id.bank != asset.id.bank
        ) {
            blockers += ReviewReason.OWNERSHIP
            return null
        }
        return parent.takeIf { asset.kind == AssetKind.DEBIT_CARD }
    }
}
