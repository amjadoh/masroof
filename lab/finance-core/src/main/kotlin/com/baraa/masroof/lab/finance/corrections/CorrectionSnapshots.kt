package com.baraa.masroof.lab.finance.corrections

import com.baraa.masroof.lab.finance.classification.FinancialClassificationInput
import com.baraa.masroof.lab.finance.posting.CorrectionAuthority
import com.baraa.masroof.lab.finance.posting.PostingKey
import com.baraa.masroof.lab.finance.posting.StoredFinancialPosting
import com.baraa.masroof.lab.finance.posting.PostingRevision
import com.baraa.masroof.lab.finance.posting.FinancialPostingRules
import com.baraa.masroof.lab.finance.posting.PostingPreparation
import com.baraa.masroof.lab.finance.matching.*
import com.baraa.masroof.lab.finance.classification.FinancialEndpoint
import com.baraa.masroof.lab.finance.dashboard.SummaryMetric
import java.util.Collections

/** Storage support, not a user command or an alternative financial authority boundary. */
data class CorrectionOverlaySnapshot(
    val key: PostingKey,
    val correctionRevision: Long,
    val interpretationRevision: Long,
    val original: FinancialClassificationInput,
    val latestObservation: CorrectionObservation,
    val safetyHold: Boolean,
    val metadata: Map<String, String>,
    val history: List<CorrectionRequest>,
    val financial: FinancialClassificationInput?,
    val suppressed: Boolean,
    val seedPosting: StoredFinancialPosting? = null,
)

/** Restores an exact persisted snapshot without rerunning historical classifications/transitions.
 * Durable adapters own versioning/checksums and must never accept a caller-built snapshot as a command.
 */
object CorrectionSnapshots {
    fun export(overlay: DecisionOverlay): CorrectionOverlaySnapshot = CorrectionOverlaySnapshot(overlay.key,
        overlay.correctionRevision, overlay.interpretationRevision, overlay.original, overlay.latestObservation,
        overlay.safetyHold, overlay.metadata, overlay.history, overlay.financial, overlay.suppressed, overlay.seedPosting)

    /** Trusted durable-adapter seed only. It is not a user command and invents no decision history. */
    fun seedStoredSelection(observation: CorrectionObservation, posting: StoredFinancialPosting): DecisionOverlay {
        val frozen = verifiedSeed(posting)
        val base = observation.input
        require(base.key == frozen.command.key && base.authority == CorrectionAuthority.NONE && base.revision.correction == 0L)
        require(base.revision.interpretation >= frozen.command.revision.interpretation && base.evidenceIds.containsAll(frozen.command.evidenceIds))
        val selected = frozen.classification.input.copy(revision = PostingRevision(0, frozen.command.revision.interpretation))
        return DecisionOverlay.create(base.key, 0, base.revision.interpretation, base, emptyMap(), emptyList(), selected,
            false, observation, observation.safety != ObservationSafety.ORDINARY, frozen)
    }

    fun restore(state: CorrectionOverlaySnapshot): DecisionOverlay {
        val frozenSeed = state.seedPosting?.let { structuralSeed(it) }
        require(state.correctionRevision > 0 && state.history.isNotEmpty())
        require(state.original.key == state.key && state.latestObservation.input.key == state.key)
        require(state.original.authority == CorrectionAuthority.NONE && state.original.revision.correction == 0L)
        val latest = state.latestObservation.input
        require(latest.authority == CorrectionAuthority.NONE && latest.revision.correction == 0L)
        require(state.interpretationRevision == latest.revision.interpretation &&
            latest.revision.interpretation >= state.original.revision.interpretation)
        require(latest.evidenceIds.containsAll(state.original.evidenceIds))
        require(state.latestObservation.safety == ObservationSafety.ORDINARY || state.safetyHold)
        require(state.history.last().correctionRevision == state.correctionRevision)
        require(state.history.map { it.decisionId }.distinct().size == state.history.size)
        var previous = 0L
        var interpretation = state.original.revision.interpretation
        val metadata = linkedMapOf<String, String>()
        var suppressed = false
        var hasFinancial = false
        for (command in state.history) {
            require(command.key == state.key && command.expectedCorrectionRevision == previous && command.correctionRevision > previous)
            require(command.expectedInterpretation in interpretation..state.interpretationRevision)
            require(latest.evidenceIds.containsAll(command.expectedEvidenceIds))
            previous = command.correctionRevision; interpretation = command.expectedInterpretation
            metadata.putAll(command.metadata)
            when (command.decision) {
                FinancialDecision.Keep -> Unit
                is FinancialDecision.EditFacts -> require(hasFinancial || frozenSeed != null)
                FinancialDecision.SuppressAsNonFinancial -> suppressed = true
                else -> { suppressed = false; hasFinancial = true }
            }
        }
        require(metadata == state.metadata && suppressed == state.suppressed)
        frozenSeed?.let { seed ->
            require(seed.command.key == state.key && seed.classification.input.key == state.key)
            require(latest.evidenceIds.containsAll(seed.command.evidenceIds) && latest.revision.interpretation >= seed.command.revision.interpretation)
            require(state.history.first().correctionRevision > seed.command.revision.correction)
        }
        require((hasFinancial || frozenSeed != null) == (state.financial != null))
        state.financial?.let { financial ->
            val edits = state.history.mapNotNull { it.decision as? FinancialDecision.EditFacts }
            edits.lastOrNull { it.time != null }?.time?.let { require(financial.time == it) }
            edits.lastOrNull { it.source != null }?.source?.let { require(financial.source == it) }
            edits.lastOrNull { it.destination != null }?.destination?.let { require(financial.destination == it) }
            require(financial.key == state.key && financial.revision.correction == 0L &&
                financial.revision.interpretation <= state.interpretationRevision)
            require(latest.evidenceIds.containsAll(financial.evidenceIds))
            val decision = state.history.lastOrNull { it.decision is FinancialDecision.ConfirmFamilyOrAmount || it.decision is FinancialDecision.ResolveFinancialType }?.decision
            when (decision) {
                is FinancialDecision.ConfirmFamilyOrAmount -> {
                    require(financial.authority == CorrectionAuthority.CONFIRMED_FAMILY_OR_AMOUNT)
                    require(decision.type == null || financial.storedType == decision.type)
                    require(decision.amount == null || financial.amount == decision.amount)
                }
                is FinancialDecision.ResolveFinancialType -> require(financial.authority == CorrectionAuthority.USER_FINANCIAL_TYPE && financial.storedType == decision.type)
                null -> {
                    val seed = requireNotNull(frozenSeed).classification.input
                    require(financial == seed.copy(revision = PostingRevision(0, seed.revision.interpretation),
                        time = edits.lastOrNull { it.time != null }?.time ?: seed.time,
                        source = edits.lastOrNull { it.source != null }?.source ?: seed.source,
                        destination = edits.lastOrNull { it.destination != null }?.destination ?: seed.destination))
                }
                else -> error("Missing financial decision")
            }
        }
        val frozenHistory = state.history.map { it.copy(expectedEvidenceIds = Collections.unmodifiableSet(LinkedHashSet(it.expectedEvidenceIds)),
            metadata = Collections.unmodifiableMap(LinkedHashMap(it.metadata))) }
        return DecisionOverlay.create(state.key, state.correctionRevision, state.interpretationRevision,
            state.original, state.metadata, frozenHistory, state.financial, state.suppressed, state.latestObservation, state.safetyHold, frozenSeed)
    }

    private fun verifiedSeed(posting: StoredFinancialPosting): StoredFinancialPosting {
        val identity = posting.matchIdentity
        val prepared = if (identity == null) {
            require(posting.observations.isEmpty())
            FinancialPostingRules.prepare(posting.classification.input)
        } else {
            val proof = when (identity.kind) {
                "BANK_REFERENCE" -> TransferMatchProof.BankReference(ConfirmedBankTransferReference(requireNotNull(identity.issuer), identity.reference))
                "USER_PAIR" -> {
                    require(identity.issuer == null)
                    val out = posting.observations.single { it.direction == TransferDirection.OUTBOUND }.input
                    val incoming = posting.observations.single { it.direction == TransferDirection.INBOUND }.input
                    TransferMatchProof.UserPair(identity.reference, out.key, out.revision, out.evidenceIds, incoming.key, incoming.revision, incoming.evidenceIds)
                }
                else -> error("Unknown stored pairing proof")
            }
            val matched = SelfTransferMatcher.match(posting.observations, proof)
            require(matched is TransferMatchResult.Matched)
            FinancialPostingRules.prepare(matched.transfer)
        }
        require(prepared is PostingPreparation.Ready && prepared.posting == posting) { "Inconsistent stored financial seed" }
        return prepared.posting
    }

    /** Structural validation only: persisted historical financial metrics are never recalculated. */
    private fun structuralSeed(posting: StoredFinancialPosting): StoredFinancialPosting {
        val input = posting.classification.input
        val command = posting.command
        require(input.key == command.key && input.revision == command.revision && input.amount == command.amount &&
            input.time == command.time && input.appliedFx == command.appliedFx && input.evidenceIds == command.evidenceIds &&
            posting.classification.effectiveType == command.type && command.source == (input.source as? FinancialEndpoint.Owned)?.id &&
            command.destination == (input.destination as? FinancialEndpoint.Owned)?.id)
        require(posting.classification.metrics.keys == SummaryMetric.entries.toSet() &&
            posting.classification.metrics.values.all { it.currency == input.amount.currency } &&
            posting.classification.accountMovements.all { it.amount.currency == input.amount.currency && it.account.environment == input.key.environment })
        val identity = posting.matchIdentity
        require((identity == null) == posting.observations.isEmpty())
        if (identity != null) {
            require(identity.environment == input.key.environment && identity.reference.isNotBlank())
            require((identity.kind == "USER_PAIR" && identity.issuer == null) || (identity.kind == "BANK_REFERENCE" && identity.issuer != null))
            val out = posting.observations.single { it.direction == TransferDirection.OUTBOUND }.input
            val incoming = posting.observations.single { it.direction == TransferDirection.INBOUND }.input
            require(posting.observations.size == 2 && out.key != incoming.key && out.key.environment == incoming.key.environment &&
                out.evidenceIds.intersect(incoming.evidenceIds).isEmpty())
            require(input == out.copy(assets = out.assets + incoming.assets, evidenceIds = out.evidenceIds + incoming.evidenceIds))
            require(out.amount == incoming.amount && out.source == incoming.source && out.destination == incoming.destination &&
                out.appliedFx == incoming.appliedFx && out.financingLoan == incoming.financingLoan && out.incomeAttribution == incoming.incomeAttribution)
            if (identity.kind == "BANK_REFERENCE") require(posting.observations.all {
                it.reference == ConfirmedBankTransferReference(requireNotNull(identity.issuer), identity.reference)
            })
        }
        fun freeze(value: FinancialClassificationInput) = value.copy(assets = Collections.unmodifiableMap(LinkedHashMap(value.assets)),
            evidenceIds = Collections.unmodifiableSet(LinkedHashSet(value.evidenceIds)), unresolved = Collections.unmodifiableSet(LinkedHashSet(value.unresolved)))
        return posting.copy(classification = posting.classification.copy(input = freeze(input),
            metrics = Collections.unmodifiableMap(LinkedHashMap(posting.classification.metrics)),
            accountMovements = Collections.unmodifiableList(ArrayList(posting.classification.accountMovements))),
            command = command.copy(evidenceIds = Collections.unmodifiableSet(LinkedHashSet(command.evidenceIds))),
            observations = Collections.unmodifiableList(posting.observations.map { it.copy(input = freeze(it.input)) }))
    }
}
