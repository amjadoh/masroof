package com.baraa.masroof.application.patterns

import com.baraa.masroof.application.ingestion.SmsIngestionResult
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.domain.repository.UserCorrectionRepository

/** Check and parse/store share a Room transaction, so concurrent human decisions cannot be overwritten. */
class PatternEvidenceProtection(private val reviews: ReviewRepository, private val corrections: UserCorrectionRepository,
    private val transactions: FinancialTransactionRepository,
    private val inTransaction: suspend (suspend () -> SmsIngestionResult) -> SmsIngestionResult) {
    suspend fun run(rawSmsId: String, work: suspend () -> SmsIngestionResult, resume: suspend () -> SmsIngestionResult): SmsIngestionResult = inTransaction {
        val decision = reviews.findByRawSmsId(rawSmsId)?.resolutionKind
        if (decision == com.baraa.masroof.domain.model.ReviewResolutionKind.USER_NON_FINANCIAL) SmsIngestionResult.Duplicate
        else if (decision != null || corrections.listForRawSmsId(rawSmsId).isNotEmpty() || transactions.isRawSmsLinked(rawSmsId)) resume()
        else work()
    }
}
