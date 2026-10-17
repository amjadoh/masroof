package com.baraa.masroof.lab.finance.dashboard.producer

import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.projection.LinkedInternationalFee
import com.baraa.masroof.lab.finance.value.Money
import java.time.Instant
import java.time.LocalDate

/** One atomic, bounded read of CURRENT rows and their lifecycle, not append-only ledger history.
 * Implementations must honor durable correction visibility (inactive/pending rows are not active).
 * This interface offers no per-row lookup, parsing, market lookup or write entry point.
 */
fun interface DashboardReadSource {
    suspend fun load(query: DashboardQuery): DashboardReadBatch
}

data class DashboardLedgerRow(val posting: StoredFinancialPosting, val active: Boolean)

/** Unknown coverage cannot be upgraded by an empty collection. Observations remain optional facts. */
data class DashboardCoverage(
    val completeLedger: Boolean,
    val completeRegistry: Boolean,
    val completeFeeFacts: Boolean,
    val reviewRequiredCount: Int?,
    /** True only for a runtime source reading all collections in one database snapshot. */
    val atomicRuntimeSource: Boolean = false,
    /** Includes proved absence of merchant labels; missing projections are not an empty ranking. */
    val completeMerchantFacts: Boolean = false,
    /** True only when user commitment occurrences are supplied completely for the salary window. */
    val completeUserCommitments: Boolean = false,
) {
    init { require(reviewRequiredCount == null || reviewRequiredCount >= 0) }
}

/** Caller-selected windows. A selected query is never silently relabelled a salary/statement period. */
data class DashboardPeriods(
    val salary: DisplayPeriod? = null,
    val calendar: DisplayPeriod? = null,
    val statements: Map<AssetId, DisplayPeriod> = emptyMap(),
)

data class ObservedSignedBalance(val amount: SignedAmount, val asOf: Instant)
data class ObservedMoney(val amount: Money, val asOf: Instant)
data class ObservedStatement(val due: Money, val issuedAt: Instant, val dueDate: LocalDate?)

/** Already persisted structured observations. No SMS body, bank wording or inferred last4 identity. */
data class DashboardAssetFacts(
    val id: AssetId,
    val label: String? = null,
    val accountCategory: AccountCategory? = null,
    val balance: ObservedSignedBalance? = null,
    val statement: ObservedStatement? = null,
    val loanType: String? = null,
    val loanRemaining: ObservedMoney? = null,
    val loanInstallment: ObservedMoney? = null,
    val network: String? = null,
)

data class DashboardPostingFacts(
    val key: PostingKey,
    val revision: PostingRevision,
    val evidenceId: String,
    val merchant: String? = null,
    val counterparty: String? = null,
    val categoryId: String? = null,
    /** Proved account-backed Mada involvement; never an inferred financial endpoint. */
    val debitCard: AssetId? = null,
    val messageFamily: DashboardEvidenceFamily? = null,
)

/** Persisted parse-time family only; chart projections never inspect bank wording. */
enum class DashboardEvidenceFamily {
    PURCHASE, TRANSFER_IN, TRANSFER_OUT, CARD_PAYMENT, BILL_PAYMENT, FINANCING_INSTALLMENT,
    WITHDRAWAL, REFUND, FEE, BALANCE_NOTICE, OTP, NON_FINANCIAL, UNKNOWN,
}

/** A source consistency token covers every collection and coverage flag in this batch together.
 * Runtime SQL/Room integration is deliberately separate from this executable pure producer.
 */
data class DashboardReadBatch(
    val query: DashboardQuery,
    val revision: ReadRevision,
    val rows: List<DashboardLedgerRow>,
    val assets: List<OwnedAsset>,
    val coverage: DashboardCoverage,
    val periods: DashboardPeriods = DashboardPeriods(),
    val assetFacts: List<DashboardAssetFacts> = emptyList(),
    val postingFacts: List<DashboardPostingFacts> = emptyList(),
    val fees: List<LinkedInternationalFee> = emptyList(),
    val commitmentOccurrences: List<CommitmentRow> = emptyList(),
)
