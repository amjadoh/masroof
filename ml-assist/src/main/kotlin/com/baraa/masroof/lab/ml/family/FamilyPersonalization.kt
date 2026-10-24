package com.baraa.masroof.lab.ml.family

data class FamilyPersonalizationResult(val classifier: FamilyClassifier, val candidate: FamilyClassifier,
    val trainingTemplates: Int, val validationTemplates: Int, val personalUsed: Boolean, val reason: String)

/** Human-template memory and held-out statistical generalization are evaluated separately. */
object FamilyPersonalization {
    fun isValidation(bankId: String, template: String): Boolean =
        FamilyInputCleaner.sha256("family-personal-v1|$bankId|$template").take(8).toLong(16) % 5 == 0L

    fun rebuild(seed: FamilyModel, labels: List<HumanFamilyLabel>, validation: List<FamilyEvaluation>,
        blocks: List<HumanTemplateBlock> = emptyList(), runtimeEligibleTemplates: Set<String>? = null): FamilyPersonalizationResult {
        val validatedBlocks = FamilyClassifier.checked(seed, labels, validation, blocks).latestBlocks
        val baseline = FamilyClassifier(seed, labels, validatedBlocks, emptySet())
        val trusted = baseline.trustedLabels
        val training = trusted.filterNot { isValidation(it.bankId, it.template) }
        val heldOut = trusted.filter { isValidation(it.bankId, it.template) &&
            (runtimeEligibleTemplates == null || it.template in runtimeEligibleTemplates) }
        val candidate = FamilyClassifier(seed, labels, validatedBlocks, training.map { it.template }.toSet())
        fun suggestion(model: FamilyModel, terms: Map<String, Int>) = model.predictTerms(terms)?.takeIf(model::accepts)?.family
        // Do not call suggest(): exact human-template memory would leak held-out answers.
        fun correct(model: FamilyModel) = heldOut.count { suggestion(model, it.terms) == it.family }
        fun wrong(model: FamilyModel) = heldOut.count { row -> suggestion(model, row.terms)?.let { it != row.family } == true }
        val safety = validation.filter { it.runtimeEligible && it.template != null }.distinctBy { it.template }
        val safe = safety.none { row -> suggestion(candidate.model, row.terms)?.let { it != row.family || !row.passesValidation } == true } &&
            seed.enabledFamilies.all { family -> safety.count { it.family == family && it.passesValidation &&
                suggestion(candidate.model, it.terms) == family } >= FamilyClassifier.MIN_ACCEPTED_VALIDATION_TEMPLATES }
        val reason = when {
            seed.enabledFamilies.isEmpty() -> "SEED_NOT_ELIGIBLE"
            heldOut.size < FamilyClassifier.MIN_ACCEPTED_VALIDATION_TEMPLATES -> "NEEDS_EVALUATIONS"
            training.isEmpty() -> "NEEDS_TRAINING_LABELS"
            !safe -> "SAFETY_VALIDATION_FAILED"
            wrong(candidate.model) > wrong(baseline.model) -> "BASELINE_BETTER"
            correct(candidate.model) <= correct(baseline.model) -> "BASELINE_BETTER"
            else -> "PERSONAL_MODEL"
        }
        val accepted = reason == "PERSONAL_MODEL"
        return FamilyPersonalizationResult(if (accepted) candidate else baseline, candidate,
            training.size, heldOut.size, accepted, reason)
    }
}
