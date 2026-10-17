package com.baraa.masroof.lab.beta.application.evidence

import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.classification.IncomeAttribution
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.Environment

/** Explicit financial approval, separate from comparison acknowledgement. No parser success is
 * asserted by this request. Amount and time always come from the exact durable baseline archive. */
data class BetaFinancialApproval(
    val evidenceId: String,
    val archiveRevision: String,
    val expectedPostingRevision: PostingRevision?,
    val type: FinancialType,
    val source: AssetId?,
    val destination: AssetId?,
    val incomeAttribution: IncomeAttribution? = null,
) {
    init {
        require(evidenceId.isNotBlank() && archiveRevision.isNotBlank())
        require(listOfNotNull(source, destination).all { it.environment == Environment.BETA })
    }
}

data class BetaFinancialReviewItem(val evidenceId: String, val key: PostingKey,
    val archiveRevision: String, val status: String, val reason: String,
    val postingRevision: PostingRevision?)

sealed interface BetaFinancialAdoptionResult {
    data class Posted(val result: FinancialPostingResult) : BetaFinancialAdoptionResult
    data class Review(val reason: String) : BetaFinancialAdoptionResult
}
