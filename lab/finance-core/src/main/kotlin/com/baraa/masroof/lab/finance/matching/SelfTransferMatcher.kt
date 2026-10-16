package com.baraa.masroof.lab.finance.matching

import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.finance.value.Environment
import java.util.Collections
import java.time.Duration

enum class TransferDirection { OUTBOUND, INBOUND }

/** A bank-issued unique transfer identifier, established from validated structured evidence.
 * A memo, amount, suffix, merchant, sender or SMS-body similarity is never such an identifier.
 */
data class ConfirmedBankTransferReference(val issuer: BankId, val value: String) {
    init { require(value.isNotBlank()) }
}

data class TransferObservation(
    val input: FinancialClassificationInput,
    val direction: TransferDirection,
    val reference: ConfirmedBankTransferReference? = null,
)

sealed interface TransferMatchProof {
    data class BankReference(val reference: ConfirmedBankTransferReference) : TransferMatchProof

    /** Explicit pairing decision, bound to both original keys, revisions and exact evidence sets.
     * It selects a pair only: it does not confirm ownership or grant financial posting authority.
     */
    data class UserPair(
        val decisionId: String,
        val outbound: PostingKey,
        val outboundRevision: PostingRevision,
        val outboundEvidence: Set<String>,
        val inbound: PostingKey,
        val inboundRevision: PostingRevision,
        val inboundEvidence: Set<String>,
    ) : TransferMatchProof {
        init { require(decisionId.isNotBlank()) }
    }
}

/** Identity is namespaced and retained by the posting adapter to prevent reusing a pairing proof. */
data class TransferMatchIdentity(
    val environment: Environment,
    val kind: String,
    val issuer: BankId?,
    val reference: String,
)

/** Constructor is internal: callers obtain a checked pair through the matcher, not by metadata. */
class MatchedSelfTransfer internal constructor(
    val input: FinancialClassificationInput,
    val observations: List<TransferObservation>,
    val identity: TransferMatchIdentity,
)

sealed interface TransferMatchResult {
    data class Matched(val transfer: MatchedSelfTransfer) : TransferMatchResult
    data class Review(val reasons: Set<ReviewReason>) : TransferMatchResult
}

/** Conservative pure matcher. Supply the complete candidate pool for the requested proof.
 * Multiple possible pairs, inconsistent facts and unresolved endpoints stay in review.
 * Both original observation times/provenance remain available; the outbound key/time are canonical.
 */
object SelfTransferMatcher {
    fun match(candidates: List<TransferObservation>, proof: TransferMatchProof): TransferMatchResult {
        val pool = candidates.map { it.copy(input = freezeFinancialInput(it.input)) }
        val selected = when (proof) {
            is TransferMatchProof.BankReference -> pool.filter { it.reference == proof.reference }
            is TransferMatchProof.UserPair -> pool.filter { it.input.key == proof.outbound || it.input.key == proof.inbound }
        }
        fun review(reason: ReviewReason = ReviewReason.AMBIGUOUS_MATCH) = TransferMatchResult.Review(setOf(reason))
        if (selected.size != 2 || selected.map { it.input.key }.distinct().size != 2) return review()
        val outbound = selected.singleOrNull { it.direction == TransferDirection.OUTBOUND } ?: return review()
        val inbound = selected.singleOrNull { it.direction == TransferDirection.INBOUND } ?: return review()
        val out = outbound.input
        val incoming = inbound.input
        if (out.key.environment != incoming.key.environment || out.storedType != FinancialType.SELF_TRANSFER ||
            incoming.storedType != FinancialType.SELF_TRANSFER || out.amount != incoming.amount ||
            out.source != incoming.source || out.destination != incoming.destination || out.appliedFx != incoming.appliedFx ||
            out.financingLoan != incoming.financingLoan || out.incomeAttribution != incoming.incomeAttribution
        ) return review(ReviewReason.VALIDATION)
        // Shared evidence is a repeated/conflicting observation, never two independent transfer legs.
        if (out.evidenceIds.intersect(incoming.evidenceIds).isNotEmpty()) return review()
        // If the complete pool contains another claimant to either observation, do not pick by order.
        if (pool.filter { it !in selected }.any { candidate ->
                candidate.input.evidenceIds.any { it in out.evidenceIds || it in incoming.evidenceIds }
            }) return review()
        val identity = when (proof) {
            is TransferMatchProof.BankReference -> {
                val source = (out.source as? FinancialEndpoint.Owned)?.id ?: return review(ReviewReason.OWNERSHIP)
                val destination = (out.destination as? FinancialEndpoint.Owned)?.id ?: return review(ReviewReason.OWNERSHIP)
                if (source.bank != proof.reference.issuer || destination.bank != proof.reference.issuer) return review()
                // Stored instants only; no timezone or wall-clock reinterpretation. A reused bank
                // reference days later is not proof of the same transfer (legacy bound: 10 minutes).
                if (Duration.between(out.time.instant, incoming.time.instant).abs() > Duration.ofMinutes(10)) return review()
                TransferMatchIdentity(out.key.environment, "BANK_REFERENCE", proof.reference.issuer, proof.reference.value)
            }
            is TransferMatchProof.UserPair -> {
                if (out.key != proof.outbound || incoming.key != proof.inbound ||
                    out.revision != proof.outboundRevision || incoming.revision != proof.inboundRevision ||
                    out.evidenceIds != proof.outboundEvidence.toSet() || incoming.evidenceIds != proof.inboundEvidence.toSet()
                ) return review()
                TransferMatchIdentity(out.key.environment, "USER_PAIR", null, proof.decisionId)
            }
        }
        // A pairing decision never bypasses either leg's existing eligibility/ownership blockers.
        val reasons = selected.flatMap {
            (FinancialImpactClassifier.classify(it.input) as? FinancialClassification.Review)?.reasons.orEmpty()
        }.toSet()
        if (reasons.isNotEmpty()) return TransferMatchResult.Review(reasons)
        if (incoming.assets.any { (id, asset) -> out.assets[id]?.let { it != asset } == true }) return review(ReviewReason.OWNERSHIP)
        val merged = freezeFinancialInput(out.copy(assets = out.assets + incoming.assets,
            evidenceIds = out.evidenceIds + incoming.evidenceIds))
        return TransferMatchResult.Matched(MatchedSelfTransfer(merged, Collections.unmodifiableList(listOf(outbound, inbound)), identity))
    }
}

/** Immutable snapshots are also used by the reference posting adapter; Kotlin read-only views alone
 * are insufficient when callers still retain a MutableMap/MutableSet or cast an exposed collection.
 */
internal fun freezeFinancialInput(input: FinancialClassificationInput): FinancialClassificationInput = input.copy(
    assets = Collections.unmodifiableMap(LinkedHashMap(input.assets)),
    evidenceIds = Collections.unmodifiableSet(LinkedHashSet(input.evidenceIds)),
    unresolved = Collections.unmodifiableSet(LinkedHashSet(input.unresolved)),
)
