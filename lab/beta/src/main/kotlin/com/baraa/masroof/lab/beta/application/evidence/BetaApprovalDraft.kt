package com.baraa.masroof.lab.beta.application.evidence

import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.posting.*

/** Presentation submits stable choices; the pure classifier checks the exact preserved facts.
 * The write adapter independently rereads and validates everything inside its transaction. */
fun betaApprovalDraft(preview: BetaFinancialApprovalPreview, type: String?, source: String?, destination: String?, income: String?): BetaFinancialApproval? {
    if (preview.blockedReason != null || preview.pending != null || source == null || destination == null) return null
    fun endpoint(value: String): FinancialEndpoint? = if (value == "external") FinancialEndpoint.External
        else preview.assets.singleOrNull { "asset:${it.id.localId}" == value }?.id?.let(FinancialEndpoint::Owned)
    val selectedType = type?.let { runCatching { FinancialType.valueOf(it) }.getOrNull() } ?: return null
    val attribution = if (selectedType in setOf(FinancialType.INCOME, FinancialType.EXTERNAL_TRANSFER_IN))
        income?.let { runCatching { IncomeAttribution.valueOf(it) }.getOrNull() } ?: return null else null
    val input = FinancialClassificationInput(preview.item.key, preview.item.postingRevision ?: PostingRevision(0, 0), selectedType,
        preview.amount ?: return null, preview.time ?: return null, endpoint(source) ?: return null, endpoint(destination) ?: return null,
        preview.assets.associateBy { it.id }, setOf(preview.item.evidenceId), true, CorrectionAuthority.USER_FINANCIAL_TYPE, incomeAttribution = attribution)
    if (FinancialImpactClassifier.classify(input) !is FinancialClassification.Classified) return null
    return BetaFinancialApproval(preview.item.evidenceId, preview.item.archiveRevision, preview.item.postingRevision, selectedType,
        (input.source as? FinancialEndpoint.Owned)?.id, (input.destination as? FinancialEndpoint.Owned)?.id, attribution, preview.registryRevision)
}
