package com.baraa.masroof.lab.beta.application.history

import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.value.BankId
import org.junit.Assert.*
import org.junit.Test

class BetaHistoryImportTest {
    private val bank = BankId("BANK_ALJAZIRA")
    private val initial = HistorySession(0, 1000, setOf(bank))
    private class Sessions(var value: HistorySession) : HistorySessionStore {
        var fail = false
        override fun load() = value
        override fun save(session: HistorySession) { if (fail) error("checkpoint failure"); value = session }
    }
    private fun row(id: Long, date: Long = 5) = HistoryRow(id, date, "AlJazira", "synthetic")
    @Test fun batchIsBoundedAndSameTimestampUsesProviderId() {
        val rows = (0L..27).map { row(it) }
        val store = Sessions(initial)
        var reads = 0
        val captured = mutableListOf<String>()
        val workflow = BetaHistoryImport(store, { session, limit ->
            reads++
            rows.filter { it.date > session.afterDate || it.date == session.afterDate && it.id > session.afterId }.take(limit)
        }, { request, messages ->
            assertEquals(setOf(bank), request.historyBanks)
            captured += messages.single().providerId
            listOf(EvidenceResult.NotCaptured(ScopeRejection.UNRECOGNIZED))
        })
        assertEquals(25, workflow.step().scanned)
        assertFalse(store.value.finished)
        assertEquals(28, workflow.step().scanned)
        assertTrue(store.value.finished)
        assertEquals(28, captured.distinct().size)
        workflow.step()
        assertEquals(2, reads)
    }
    @Test fun failureAfterCaptureBeforeCheckpointReplaysSameIdentity() {
        val store = Sessions(initial)
        val identities = mutableListOf<String>()
        val workflow = BetaHistoryImport(store, { _, _ -> listOf(row(5)) }, { request, messages ->
            identities += ProcessBetaEvidence.identity(request.namespace, messages.single().providerId)
            listOf(EvidenceResult.NotCaptured(ScopeRejection.UNRECOGNIZED))
        })
        store.fail = true
        assertThrows(IllegalStateException::class.java) { workflow.step() }
        assertEquals(initial, store.value)
        store.fail = false
        workflow.step()
        assertEquals(2, identities.size)
        assertEquals(1, identities.distinct().size)
    }
    @Test fun sourceFailureDoesNotAdvanceCheckpoint() {
        val store = Sessions(initial)
        val workflow = BetaHistoryImport(store, { _, _ -> throw SecurityException() }, { _, _ -> error("must not import") })
        assertThrows(SecurityException::class.java) { workflow.step() }
        assertEquals(initial, store.value)
    }
    @Test fun orderingAndRangeViolationsFailClosed() {
        listOf(listOf(row(2), row(1)), listOf(row(1, 1001)), listOf(row(-1))).forEach { rows ->
            val store = Sessions(initial)
            val workflow = BetaHistoryImport(store, { _, _ -> rows }, { _, _ -> listOf(EvidenceResult.NotCaptured(ScopeRejection.UNRECOGNIZED)) })
            assertThrows(IllegalArgumentException::class.java) { workflow.step() }
        }
    }
    @Test fun limitDoesNotClaimCompleteAndNeverReadsAgain() {
        val store = Sessions(initial.copy(scanned = HistorySession.MAX_ROWS, excluded = HistorySession.MAX_ROWS))
        val workflow = BetaHistoryImport(store, { _, _ -> error("limit reached") }, { _, _ -> error("limit reached") })
        assertTrue(workflow.step().limited)
        assertFalse(store.value.finished)
    }
    @Test fun invalidMetadataAndRawLoggingRejected() {
        assertThrows(IllegalArgumentException::class.java) { initial.copy(stored = -1, excluded = 1) }
        assertThrows(IllegalArgumentException::class.java) { initial.copy(until = -1) }
        assertThrows(IllegalArgumentException::class.java) { initial.copy(banks = emptySet()) }
        assertFalse(row(1).toString().contains("synthetic"))
    }
    @Test fun retryAndConflictCountsAreSeparateFromFinancialSuccess() {
        val store = Sessions(initial)
        val results = listOf(EvidenceResult.Stored(EvidenceSummary("id", null, EvidenceStage.CAPTURED, 0, 0, null, emptySet())),
            EvidenceResult.IdentityConflict, EvidenceResult.NotCaptured(ScopeRejection.NOT_ENABLED))
        var i = 0
        val workflow = BetaHistoryImport(store, { _, _ -> listOf(row(1), row(2), row(3)) }, { _, _ -> listOf(results[i++]) })
        val session = workflow.step()
        assertEquals(1, session.retry)
        assertEquals(1, session.stored)
        assertEquals(1, session.conflicts)
        assertEquals(1, session.excluded)
        assertTrue(session.finished)
    }
}
