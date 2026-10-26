package com.baraa.masroof.application.ingestion

import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.domain.repository.UserCorrectionRepository
import com.baraa.masroof.parsing.repository.ParsedEventRepository

/** Check and parse/store share a Room transaction, so concurrent human decisions cannot be overwritten. */
class PatternEvidenceProtection(private val reviews: ReviewRepository, private val corrections: UserCorrectionRepository,
    private val transactions: FinancialTransactionRepository,
    private val parsed: ParsedEventRepository,
    private val inTransaction: suspend (suspend () -> SmsIngestionResult) -> SmsIngestionResult) {
    suspend fun run(rawSmsId: String, work: suspend () -> SmsIngestionResult, resume: suspend () -> SmsIngestionResult): SmsIngestionResult = inTransaction {
        val decision = reviews.findByRawSmsId(rawSmsId)?.resolutionKind
        if (decision == com.baraa.masroof.domain.model.ReviewResolutionKind.USER_NON_FINANCIAL) SmsIngestionResult.Duplicate
        else if (transactions.isRawSmsLinked(rawSmsId) ||
            ((decision != null || corrections.listForRawSmsId(rawSmsId).isNotEmpty()) && parsed.findByRawSmsId(rawSmsId) != null)) resume()
        // An early correction is an overlay, not an existing ParsedEvent to resume. Initial extraction preserves it.
        else work()
    }
}
