package com.baraa.masroof.lab.finance.corrections

import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CorrectionSnapshotsTest {
    private val asset = OwnedAsset(AssetId(Environment.BETA, BankId("BANK_ALJAZIRA"), "account"), AssetKind.ACCOUNT)
    private fun input() = FinancialClassificationInput(PostingKey(Environment.BETA, "transaction"), PostingRevision(0, 0),
        FinancialType.EXPENSE, Money.of("42", Currency.SAR), TransactionTime(Instant.parse("2026-10-17T12:00:00Z"), ZoneId.of("Asia/Riyadh")),
        FinancialEndpoint.Owned(asset.id), FinancialEndpoint.External, mapOf(asset.id to asset), setOf("original"), true)

    @Test fun initialSeedFreezesCallerCollectionsAndPinsExistingManualAuthorityWithoutFakeDecision() = runBlocking {
        val posted = (InMemoryFinancialPostingLedger().post(input().copy(revision = PostingRevision(7, 0),
            authority = CorrectionAuthority.USER_FINANCIAL_TYPE)) as FinancialPostingResult.Applied).posting
        val assets = posted.classification.input.assets.toMutableMap()
        val evidence = posted.command.evidenceIds.toMutableSet()
        val metrics = posted.classification.metrics.toMutableMap()
        val movements = posted.classification.accountMovements.toMutableList()
        val observations = posted.observations.toMutableList()
        val caller = posted.copy(classification = posted.classification.copy(input = posted.classification.input.copy(assets = assets, evidenceIds = evidence),
            metrics = metrics, accountMovements = movements), command = posted.command.copy(evidenceIds = evidence), observations = observations)
        val observation = CorrectionObservation(input(), ObservationSafety.ORDINARY)
        val seeded = CorrectionSnapshots.seedStoredSelection(observation, caller)
        assets.clear(); evidence.clear(); metrics.clear(); movements.clear(); observations.clear()
        assertEquals(posted, CorrectionSnapshots.export(seeded).seedPosting)
        assertTrue(seeded.history.isEmpty())
        val request = CorrectionRequest("metadata", input().key, 8, 0, setOf("original"), FinancialDecision.Keep, mapOf("merchant" to "edited"))
        val corrected = CorrectionOverlays.submit(observation, request, seeded) as CorrectionResult.Applied
        assertEquals(CorrectionAuthority.USER_FINANCIAL_TYPE, corrected.effectiveInput!!.authority)
        val snapshot = CorrectionSnapshots.export(corrected.overlay)
        assertEquals(snapshot, CorrectionSnapshots.export(CorrectionSnapshots.restore(snapshot)))
        assertEquals(1, corrected.overlay.history.size)
    }

    @Test fun malformedStoredSeedCannotGrantAuthorityOrRewriteExistingFinancialSnapshot() = runBlocking {
        val posted = (InMemoryFinancialPostingLedger().post(input()) as FinancialPostingResult.Applied).posting
        try {
            CorrectionSnapshots.seedStoredSelection(CorrectionObservation(input(), ObservationSafety.ORDINARY),
                posted.copy(command = posted.command.copy(amount = Money.of("99", Currency.SAR))))
            fail("Inconsistent seed accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun restoreRejectsChangedDecisionHistoryAndMetadataInsteadOfReplayingOrGuessing() {
        val observation = CorrectionObservation(input(), ObservationSafety.ORDINARY)
        val request = CorrectionRequest("decision", input().key, 1, 0, setOf("original"), FinancialDecision.ResolveFinancialType(FinancialType.EXPENSE), mapOf("note" to "kept"))
        val result = CorrectionOverlays.submit(observation, request) as CorrectionResult.Applied
        val state = CorrectionSnapshots.export(result.overlay)
        for (invalid in listOf(state.copy(metadata = mapOf("note" to "changed")), state.copy(history = emptyList()),
            state.copy(history = listOf(request.copy(expectedCorrectionRevision = 8))), state.copy(suppressed = true))) {
            try { CorrectionSnapshots.restore(invalid); fail("Changed history accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun restoredHistoryRequestsAreDeepImmutable() {
        val observation = CorrectionObservation(input(), ObservationSafety.ORDINARY)
        val request = CorrectionRequest("decision", input().key, 1, 0, setOf("original"), FinancialDecision.ResolveFinancialType(FinancialType.EXPENSE))
        val original = CorrectionSnapshots.export((CorrectionOverlays.submit(observation, request) as CorrectionResult.Applied).overlay)
        val evidence = mutableSetOf("original"); val metadata = mutableMapOf("note" to "stable")
        val restored = CorrectionSnapshots.restore(original.copy(metadata = metadata, history = listOf(request.copy(expectedEvidenceIds = evidence, metadata = metadata))))
        evidence.clear(); metadata.clear()
        assertEquals(setOf("original"), restored.history.single().expectedEvidenceIds)
        assertEquals(mapOf("note" to "stable"), restored.metadata)
        assertEquals(mapOf("note" to "stable"), restored.history.single().metadata)
    }
}
