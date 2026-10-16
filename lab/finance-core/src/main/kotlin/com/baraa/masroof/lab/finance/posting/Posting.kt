package com.baraa.masroof.lab.finance.posting

import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.value.Environment
import com.baraa.masroof.lab.finance.value.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/** Deliberately preserves production FinancialTransactionType members. */
enum class FinancialType {
    EXPENSE, INCOME, SELF_TRANSFER, EXTERNAL_TRANSFER_IN, EXTERNAL_TRANSFER_OUT,
    CREDIT_CARD_PAYMENT, REFUND, CASH_WITHDRAWAL, BILL_PAYMENT, LOAN_REPAYMENT,
    FEE, ADJUSTMENT, UNKNOWN,
}

enum class CorrectionAuthority {
    NONE, METADATA_ONLY, CONFIRMED_FAMILY_OR_AMOUNT, USER_FINANCIAL_TYPE,
}

enum class ReviewReason { OWNERSHIP, AMBIGUOUS_MATCH, VALIDATION, UNKNOWN_TYPE, INCOMPLETE_WRITE }

sealed interface PostingEligibility {
    data object Eligible : PostingEligibility
    data class Review(val reasons: Set<ReviewReason>) : PostingEligibility
}

/** Parsing success alone is not a posted row; unresolved financial blockers always stop posting. */
object PostingGate {
    fun evaluate(
        validatedParse: Boolean,
        authority: CorrectionAuthority,
        type: FinancialType,
        unresolved: Set<ReviewReason>,
    ): PostingEligibility {
        val reasons = unresolved.toMutableSet()
        if (!validatedParse && authority !in setOf(
                CorrectionAuthority.CONFIRMED_FAMILY_OR_AMOUNT,
                CorrectionAuthority.USER_FINANCIAL_TYPE,
            )
        ) reasons += ReviewReason.VALIDATION
        if (type == FinancialType.UNKNOWN) reasons += ReviewReason.UNKNOWN_TYPE
        return if (reasons.isEmpty()) PostingEligibility.Eligible else PostingEligibility.Review(reasons)
    }
}

/** Stored instant and zone travel together; dashboard never reinterprets bank wall clocks. */
data class TransactionTime(val instant: Instant, val zone: ZoneId)

/** FX rate keeps its full precision; it is not a monetary magnitude rounded to two decimals. */
data class AppliedFx(val rateToSar: BigDecimal, val source: String) {
    init { require(rateToSar.signum() > 0 && source.isNotBlank()) }
}

/** Stable transaction identity across retries/reparses; not a parser-version hash. */
data class PostingKey(val environment: Environment, val transactionId: String) {
    init { require(transactionId.isNotBlank()) }
}

data class PostingRevision(val correction: Long, val interpretation: Long) : Comparable<PostingRevision> {
    init { require(correction >= 0 && interpretation >= 0) }
    override fun compareTo(other: PostingRevision): Int =
        compareValuesBy(this, other, { it.correction }, { it.interpretation })
}

enum class RevisionAction { APPLY, ALREADY_APPLIED, STALE }

object PostingRevisionPolicy {
    fun action(existing: PostingRevision?, incoming: PostingRevision): RevisionAction = when {
        existing == null || incoming > existing -> RevisionAction.APPLY
        incoming == existing -> RevisionAction.ALREADY_APPLIED
        else -> RevisionAction.STALE
    }
}

/** Fully resolved financial result. No raw SMS or interpretation wording crosses this boundary. */
data class PostingCommand(
    val key: PostingKey,
    val revision: PostingRevision,
    val type: FinancialType,
    val amount: Money,
    val time: TransactionTime,
    val source: AssetId?,
    val destination: AssetId?,
    val evidenceIds: Set<String>,
    val appliedFx: AppliedFx? = null,
) {
    init {
        require(type != FinancialType.UNKNOWN)
        require(evidenceIds.isNotEmpty() && evidenceIds.none(String::isBlank))
        require(listOfNotNull(source, destination).all { it.environment == key.environment })
    }
}

/** Data adapter must atomically compare revision, replace result/links and preserve manual decisions.
 * Equal revisions must have identical payloads (otherwise conflict); failed writes stay retryable.
 * No implementation is supplied until Beta owns durable financial storage (M3/M8).
 */
interface PostingStore {
    suspend fun commit(command: PostingCommand): CommitResult
}

enum class CommitResult { APPLIED, ALREADY_APPLIED, STALE, CONFLICT }
