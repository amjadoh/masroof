package com.baraa.masroof.lab.beta.application.corrections

import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.posting.*

enum class BetaLedgerActivity { ACTIVE, SUPPRESSED, REVIEW }

/** Financial output exists only for ACTIVE. The prior financial snapshot remains an audit record. */
data class BetaCorrectionView(
    val overlay: DecisionOverlay,
    val activity: BetaLedgerActivity,
    val transitionRevision: Long,
    val reasons: Set<ReviewReason>,
    val posting: StoredFinancialPosting?,
)

sealed interface BetaCorrectionResult {
    data class Applied(val current: BetaCorrectionView) : BetaCorrectionResult
    /** The exact decision was committed earlier; current reflects any later committed transitions. */
    data class AlreadyApplied(val current: BetaCorrectionView) : BetaCorrectionResult
    data class Rejected(val reason: CorrectionRejection) : BetaCorrectionResult
    data class PostingConflict(val reason: com.baraa.masroof.lab.finance.posting.PostingConflict) : BetaCorrectionResult
}
