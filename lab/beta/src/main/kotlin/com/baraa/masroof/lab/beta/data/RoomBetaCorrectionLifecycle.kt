package com.baraa.masroof.lab.beta.data

import androidx.room.withTransaction
import com.baraa.masroof.lab.beta.application.corrections.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.matching.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.Environment
import java.util.Collections

internal suspend fun readBetaCorrectionState(database: BetaDatabase, key: PostingKey): PersistedBetaCorrection? =
    database.correctionDao().current(key.environment.name, key.transactionId)?.let { row ->
        BetaCorrectionCodec.decode(row.payload).also {
            check(it.overlay.key == key && it.overlay.correctionRevision == row.correction &&
                it.overlay.interpretationRevision == row.interpretation && it.transition == row.transition && it.activity.name == row.activity)
        }
    }

/** Explicit decision + active financial effect change, committed together or not acknowledged. */
class RoomBetaCorrectionLifecycle internal constructor(private val database: BetaDatabase,
    private val afterWritesBeforeCommit: suspend () -> Unit) {
    constructor(database: BetaDatabase) : this(database, {})
    init { require(database.openHelper.databaseName == BetaDatabase.NAME) }

    suspend fun submit(observation: CorrectionObservation, request: CorrectionRequest): BetaCorrectionResult =
        change(BetaCorrectionOperation(observation, request, request.expectedCorrectionRevision))

    suspend fun rebase(observation: CorrectionObservation, expectedCorrectionRevision: Long): BetaCorrectionResult {
        require(expectedCorrectionRevision >= 0)
        return change(BetaCorrectionOperation(observation, null, expectedCorrectionRevision))
    }

    private class Abort(val result: BetaCorrectionResult) : RuntimeException()

    private suspend fun change(unfrozen: BetaCorrectionOperation): BetaCorrectionResult {
        require(unfrozen.observation.input.key.environment == Environment.BETA)
        // Freeze every caller collection before the first suspension/transaction acquisition.
        val operation = BetaCorrectionCodec.decodeOperation(BetaCorrectionCodec.encodeOperation(unfrozen))
        val key = operation.observation.input.key
        if (operation.request != null && operation.request.key != key) return BetaCorrectionResult.Rejected(CorrectionRejection.IDENTITY)
        try {
            return database.withTransaction {
                val dao = database.correctionDao()
                val previous = readBetaCorrectionState(database, key)
                operation.request?.let { request ->
                    dao.decision(key.environment.name, request.decisionId)?.let { prior ->
                        if (prior.transactionId != key.transactionId || BetaCorrectionCodec.decodeOperation(prior.operation) != operation) {
                            return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.CONFLICT)
                        }
                        return@withTransaction BetaCorrectionResult.AlreadyApplied(view(requireNotNull(previous)))
                    }
                }
                if (operation.expectedCorrection != (previous?.overlay?.correctionRevision ?: 0L)) {
                    return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.STALE)
                }
                val ledger = RoomBetaFinancialLedger(database)
                val existing = ledger.readAudit(key)
                if (previous == null && existing != null && operation.request != null) {
                    if (operation.request.correctionRevision <= existing.command.revision.correction ||
                        operation.observation.input.revision.interpretation < existing.command.revision.interpretation) {
                        return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.STALE)
                    }
                    if (!operation.observation.input.evidenceIds.containsAll(existing.command.evidenceIds)) {
                        return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.EVIDENCE)
                    }
                }
                val priorOverlay = previous?.overlay ?: existing?.let { CorrectionSnapshots.seedStoredSelection(operation.observation, it) }
                val transitioned = if (operation.request != null) CorrectionOverlays.submit(operation.observation, operation.request, priorOverlay)
                else previous?.let { CorrectionOverlays.rebase(operation.observation, it.overlay) }
                    ?: return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.IDENTITY)
                if (transitioned is CorrectionResult.Rejected) return@withTransaction BetaCorrectionResult.Rejected(transitioned.reason)
                val applied = transitioned as CorrectionResult.Applied
                val snapshot = CorrectionSnapshots.export(applied.overlay)
                if (previous != null && snapshot == CorrectionSnapshots.export(previous.overlay)) {
                    return@withTransaction BetaCorrectionResult.AlreadyApplied(view(previous))
                }
                // A correction of a matched aggregate must bind the whole canonical observation set.
                if (existing?.matchIdentity != null && !operation.observation.input.evidenceIds.containsAll(existing.command.evidenceIds)) {
                    return@withTransaction BetaCorrectionResult.Rejected(CorrectionRejection.EVIDENCE)
                }
                val evidence = operation.observation.input.evidenceIds + existing?.command?.evidenceIds.orEmpty()
                for (id in evidence) {
                    val owners = listOf(dao.evidenceOwner(key.environment.name, id), database.ledgerDao().evidenceOwner(key.environment.name, id))
                    if (owners.any { it != null && it != key.transactionId }) return@withTransaction BetaCorrectionResult.PostingConflict(PostingConflict.EVIDENCE_ALREADY_CLAIMED)
                }
                val identity = existing?.matchIdentity
                identity?.let {
                    val owners = listOf(dao.proofOwner(key.environment.name, it.kind, it.issuer?.value.orEmpty(), it.reference),
                        database.ledgerDao().proofOwner(key.environment.name, it.kind, it.issuer?.value.orEmpty(), it.reference))
                    if (owners.any { owner -> owner != null && owner != key.transactionId }) return@withTransaction BetaCorrectionResult.PostingConflict(PostingConflict.MATCH_ALREADY_CLAIMED)
                }
                val sequence = Math.addExact(previous?.transition ?: 0L, 1L)
                // Anchor permits suppression before first posting. This provisional state never escapes COMMIT.
                var next = PersistedBetaCorrection(applied.overlay, BetaLedgerActivity.REVIEW, sequence, setOf(ReviewReason.INCOMPLETE_WRITE))
                dao.current(row(next))
                for (id in evidence) if (dao.evidenceOwner(key.environment.name, id) == null) {
                    dao.evidence(BetaCorrectionEvidenceClaimEntity(key.environment.name, id, key.transactionId))
                }
                if (identity != null && dao.proofOwner(key.environment.name, identity.kind, identity.issuer?.value.orEmpty(), identity.reference) == null) {
                    dao.proof(BetaCorrectionProofClaimEntity(key.environment.name, identity.kind, identity.issuer?.value.orEmpty(), identity.reference, key.transactionId))
                }
                var activity = when (applied.disposition) {
                    CorrectionDisposition.SUPPRESSED -> BetaLedgerActivity.SUPPRESSED
                    CorrectionDisposition.CLASSIFIED -> BetaLedgerActivity.ACTIVE
                    CorrectionDisposition.FINANCIAL_REVIEW -> BetaLedgerActivity.REVIEW
                }
                var reasons = (applied.classification as? FinancialClassification.Review)?.reasons.orEmpty()
                if (identity != null && !snapshot.suppressed && !snapshot.safetyHold) {
                    // Reusing a stored match is permitted only for an unchanged whole aggregate.
                    val selected = snapshot.financial ?: operation.observation.input
                    if (!samePairFacts(selected, existing.classification.input)) {
                        throw Abort(BetaCorrectionResult.Rejected(CorrectionRejection.UNSUPPORTED_CHANGE))
                    }
                    val blockers = selected.unresolved + operation.observation.input.unresolved +
                        ((applied.classification as? FinancialClassification.Review)?.reasons.orEmpty() - ReviewReason.AMBIGUOUS_MATCH)
                    if (blockers.isNotEmpty()) { activity = BetaLedgerActivity.REVIEW; reasons = blockers }
                    else {
                        val paired = restorePair(existing, operation.observation.input, applied.overlay.correctionRevision, selected.authority)
                        if (paired is TransferMatchResult.Review) { activity = BetaLedgerActivity.REVIEW; reasons = paired.reasons }
                        else {
                            activity = BetaLedgerActivity.ACTIVE
                            reasons = emptySet()
                            when (val result = ledger.postCorrection((paired as TransferMatchResult.Matched).transfer)) {
                                is FinancialPostingResult.Conflict -> throw Abort(BetaCorrectionResult.PostingConflict(result.reason))
                                is FinancialPostingResult.Review -> { activity = BetaLedgerActivity.REVIEW; reasons = result.reasons }
                                is FinancialPostingResult.Stale -> throw Abort(BetaCorrectionResult.Rejected(CorrectionRejection.STALE))
                                else -> Unit
                            }
                        }
                    }
                } else if (activity == BetaLedgerActivity.ACTIVE) {
                    when (val result = ledger.postCorrection(requireNotNull(applied.effectiveInput))) {
                        is FinancialPostingResult.Conflict -> throw Abort(BetaCorrectionResult.PostingConflict(result.reason))
                        is FinancialPostingResult.Review -> { activity = BetaLedgerActivity.REVIEW; reasons = result.reasons }
                        is FinancialPostingResult.Stale -> throw Abort(BetaCorrectionResult.Rejected(CorrectionRejection.STALE))
                        else -> Unit
                    }
                }
                if (activity == BetaLedgerActivity.REVIEW && reasons.isEmpty()) reasons = setOf(ReviewReason.VALIDATION)
                next = PersistedBetaCorrection(applied.overlay, activity, sequence, Collections.unmodifiableSet(LinkedHashSet(reasons)))
                dao.current(row(next))
                dao.append(BetaCorrectionAuditEntity(key.environment.name, key.transactionId, sequence, operation.request?.decisionId,
                    BetaCorrectionCodec.encodeOperation(operation), BetaCorrectionCodec.encode(next)))
                RoomBetaDashboardFacts(database).refreshTransaction(key)
                afterWritesBeforeCommit()
                BetaCorrectionResult.Applied(view(next))
            }
        } catch (abort: Abort) { return abort.result }
    }

    private fun samePairFacts(left: FinancialClassificationInput, right: FinancialClassificationInput) =
        left.storedType == FinancialType.SELF_TRANSFER && left.amount == right.amount && left.source == right.source &&
            left.destination == right.destination && left.time == right.time && left.appliedFx == right.appliedFx &&
            left.financingLoan == right.financingLoan && left.incomeAttribution == right.incomeAttribution

    private fun restorePair(stored: StoredFinancialPosting, base: FinancialClassificationInput, correction: Long,
        authority: CorrectionAuthority): TransferMatchResult {
        val identity = requireNotNull(stored.matchIdentity)
        val observations = stored.observations.map { observation -> observation.copy(input = observation.input.copy(assets = base.assets,
            revision = if ((stored.matchIdentity?.kind=="BANK_NOTICES" && observation.input.key==stored.command.key) || (stored.matchIdentity?.kind!="BANK_NOTICES" && observation.direction == TransferDirection.OUTBOUND)) PostingRevision(correction, base.revision.interpretation) else observation.input.revision,
            authority = if (((stored.matchIdentity?.kind=="BANK_NOTICES" && observation.input.key==stored.command.key) || (stored.matchIdentity?.kind!="BANK_NOTICES" && observation.direction == TransferDirection.OUTBOUND)) && authority != CorrectionAuthority.NONE) authority else observation.input.authority,
            unresolved = observation.input.unresolved + base.unresolved)) }
        val proof = if(identity.kind=="BANK_NOTICES") TransferMatchProof.BankNotices(stored.command.key)
        else if (identity.kind == "BANK_REFERENCE") TransferMatchProof.BankReference(ConfirmedBankTransferReference(requireNotNull(identity.issuer), identity.reference))
        else {
            val out = observations.single { it.direction == TransferDirection.OUTBOUND }.input
            val incoming = observations.single { it.direction == TransferDirection.INBOUND }.input
            TransferMatchProof.UserPair(identity.reference, out.key, out.revision, out.evidenceIds, incoming.key, incoming.revision, incoming.evidenceIds)
        }
        return SelfTransferMatcher.match(observations, proof)
    }
    private fun row(value: PersistedBetaCorrection) = BetaCorrectionCurrentEntity(value.overlay.key.environment.name,
        value.overlay.key.transactionId, value.overlay.correctionRevision, value.overlay.interpretationRevision, value.transition,
        value.activity.name, BetaCorrectionCodec.encode(value))

    private suspend fun view(value: PersistedBetaCorrection): BetaCorrectionView = BetaCorrectionView(value.overlay, value.activity,
        value.transition, value.reasons, if (value.activity == BetaLedgerActivity.ACTIVE) RoomBetaFinancialLedger(database).readAudit(value.overlay.key) else null)

    suspend fun read(key: PostingKey): BetaCorrectionView? {
        require(key.environment == Environment.BETA)
        return database.withTransaction { readBetaCorrectionState(database, key)?.let { view(it) } }
    }
    suspend fun history(key: PostingKey): List<BetaCorrectionView> {
        require(key.environment == Environment.BETA)
        return Collections.unmodifiableList(database.correctionDao().history(key.environment.name, key.transactionId).map { row ->
            val state = BetaCorrectionCodec.decode(row.payload)
            check(state.overlay.key == key && state.transition == row.transition)
            // Audit views describe activity at that revision; financial history is read separately.
            BetaCorrectionView(state.overlay, state.activity, state.transition, state.reasons, null)
        })
    }
}
