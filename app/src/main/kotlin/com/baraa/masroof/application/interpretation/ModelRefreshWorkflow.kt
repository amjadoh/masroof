package com.baraa.masroof.application.interpretation

import com.baraa.masroof.application.ingestion.ProcessStoredSmsUseCase
import com.baraa.masroof.application.ingestion.SmsIngestionResult
import com.baraa.masroof.domain.model.ProcessingRetryMode
import com.baraa.masroof.domain.repository.ProcessingRetryRepository
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import com.baraa.masroof.sms.time.InstantClock

/** Advisory reparse followed by one durable, restartable derived pass. */
class ModelRefreshWorkflow(
    private val reviews: ReviewRepository,
    private val parsed: ParsedEventRepository,
    private val raw: RawSmsRepository,
    private val retries: ProcessingRetryRepository,
    private val processor: ProcessStoredSmsUseCase,
    private val mayRefresh: suspend (String) -> Boolean,
    private val needsRefresh: (ParsedEventRecord) -> Boolean,
    private val inTransaction: suspend (suspend () -> Unit) -> Unit,
    private val recoverDerived: suspend () -> Unit,
    private val clock: InstantClock,
) {
    /** Returns false when a parse failed; thrown failures retain committed retry markers. */
    suspend fun refresh(): Boolean {
        val pending = reviews.listRequired().filter {
            mayRefresh(it.rawSmsId) && parsed.findByRawSmsId(it.rawSmsId)?.let(needsRefresh) == true
        }
        var complete = true
        for (review in pending) {
            // The marker and parse commit together. A current ParsedEvent is not proof
            // that discovery/reconciliation/review finished before a process interruption.
            inTransaction {
                if (mayRefresh(review.rawSmsId)) {
                    val evidence = raw.getById(review.rawSmsId)
                    if (evidence != null) {
                        retries.markRequired(evidence.id, clock.now(), ProcessingRetryMode.HISTORICAL_BATCH)
                        if (processor.reparseAndStore(evidence) is SmsIngestionResult.Failed) complete = false
                    }
                }
            }
        }
        // Also run when every parse is already current: an earlier attempt may have
        // stopped after committing the parse. Recovery clears only completed derived work.
        recoverDerived()
        return complete
    }
}
