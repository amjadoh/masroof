package com.baraa.masroof.lab.beta.application.history

import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.value.BankId

/** Metadata only; no sender/body is stored in session preferences or UI state. */
data class HistorySession(
    val from: Long, val until: Long, val banks: Set<BankId>,
    val afterDate: Long = from, val afterId: Long = -1,
    val scanned: Int = 0, val stored: Int = 0, val excluded: Int = 0,
    val conflicts: Int = 0, val retry: Int = 0, val finished: Boolean = false,
) {
    init {
        require(from >= 0 && until >= from && true)
        require(banks.isNotEmpty() && afterDate in from..until && afterId >= -1)
        require(listOf(scanned, stored, excluded, conflicts).all { it in 0..MAX_ROWS })
        require(stored + excluded + conflicts == scanned)
        require(retry in 0..stored)
    }
    val limited get() = scanned == MAX_ROWS && !finished
    companion object {
        const val DAY = 86_400_000L
        const val MAX_DAYS = 90
        const val MAX_ROWS = Int.MAX_VALUE
        const val BATCH = 25
        const val NAMESPACE = "beta-device-inbox-v1"
    }
}

data class HistoryRow(val id: Long, val date: Long, val sender: String, val body: String) {
    override fun toString() = "HistoryRow(redacted)"
    fun message() = EvidenceMessage(id.toString(), sender, body, java.time.Instant.ofEpochMilli(date), ReceiptTimeSource.PROVIDER_TIMESTAMP)
}
fun interface HistorySource { fun read(session: HistorySession, limit: Int): List<HistoryRow> }
interface HistorySessionStore {
    fun load(): HistorySession?
    fun save(session: HistorySession)
}

/** Keyset scan; durable evidence precedes each checkpoint. Replaying a row is safe after death. */
class BetaHistoryImport(
    private val sessions: HistorySessionStore,
    private val source: HistorySource,
    private val import: (EvidenceRequest, List<EvidenceMessage>) -> List<EvidenceResult>,
) {
    fun step(): HistorySession {
        var session = requireNotNull(sessions.load())
        if (session.finished || session.limited) return session
        val limit = minOf(HistorySession.BATCH, HistorySession.MAX_ROWS - session.scanned)
        val rows = source.read(session, limit)
        require(rows.size <= limit)
        rows.forEach { row ->
            require(row.id >= 0 && row.date in session.from..session.until)
            require(row.date > session.afterDate || row.date == session.afterDate && row.id > session.afterId)
            // Blank provider addresses are explicitly excluded, never fabricated as bank evidence.
            val result = if (row.sender.isBlank()) EvidenceResult.NotCaptured(ScopeRejection.UNRECOGNIZED)
                else import(EvidenceRequest(HistorySession.NAMESPACE, EvidenceOrigin.EXPLICIT_HISTORY, session.banks), listOf(row.message())).single()
            session = session.copy(afterDate = row.date, afterId = row.id, scanned = session.scanned + 1,
                stored = session.stored + if (result is EvidenceResult.Stored) 1 else 0,
                excluded = session.excluded + if (result is EvidenceResult.NotCaptured) 1 else 0,
                conflicts = session.conflicts + if (result == EvidenceResult.IdentityConflict) 1 else 0,
                retry = session.retry + if (result is EvidenceResult.Stored && result.summary.retryStage != null) 1 else 0)
            sessions.save(session) // A failed checkpoint never advances beyond evidence that was committed.
        }
        if (rows.size < limit) {
            session = session.copy(finished = true)
            sessions.save(session)
        }
        return session
    }
}
