package com.baraa.masroof.lab.beta.application.evidence

/** Human labels describe original evidence; they never alter interpreter/financial authority. */
enum class BetaLabelField { FAMILY, AMOUNT, CURRENCY, SOURCE_ACCOUNT, DESTINATION_ACCOUNT, CARD }
data class BetaInterpretationToken(val evidenceId: String, val archiveHash: String, val attempt: Int, val labelRevision: Long)
data class BetaOriginalSpan(val field: String, val ruleId: String, val start: Int, val end: Int)
data class BetaTrustedLabel(val revision: Long, val field: BetaLabelField, val value: String,
    val start: Int, val end: Int, val originalText: String, val archiveHash: String, val attempt: Int) {
    override fun toString() = "BetaTrustedLabel(redacted)"
}
data class BetaInterpretationReview(val token: BetaInterpretationToken, val body: String,
    val engineVersion: String, val rulesVersion: String, val status: String,
    val observed: Map<BetaLabelField, String>, val spans: List<BetaOriginalSpan>, val labels: List<BetaTrustedLabel>) {
    override fun toString() = "BetaInterpretationReview(redacted)"
}
data class BetaLabelDraft(val token: BetaInterpretationToken, val decisionId: String, val field: BetaLabelField,
    val value: String, val start: Int, val end: Int, val confirmed: Boolean) {
    override fun toString() = "BetaLabelDraft(redacted)"
}
