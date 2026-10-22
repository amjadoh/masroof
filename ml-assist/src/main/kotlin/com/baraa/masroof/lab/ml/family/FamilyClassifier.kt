package com.baraa.masroof.lab.ml.family

data class FamilyEvaluation(val family: SmsFamilyLabel, val terms: Map<String, Int>,
    val runtimeEligible: Boolean = false, val template: String? = null, val passesValidation: Boolean = false) {
    init {
        require(FamilyInputCleaner.validTerms(terms))
        require(template == null || template.matches(Regex("[0-9a-f]{64}")))
    }
}

/** Human decisions are the only runtime labels. Predictions never teach themselves. */
class FamilyClassifier(seed: FamilyModel, labels: List<HumanFamilyLabel> = emptyList(),
    blocks: List<HumanTemplateBlock> = emptyList()) {
    val seed = seed.copy(vocabulary = seed.vocabulary.toSet(), documents = seed.documents.toMap(),
        counts = seed.counts.mapValues { it.value.toMap() }, enabledFamilies = seed.enabledFamilies.toSet())
    val latestBlocks = blocks.filter { it.bankId == seed.bankId }.groupBy { it.rawId }.mapNotNull { (_, rows) ->
        rows.maxWith(compareBy<HumanTemplateBlock> { it.at }.thenBy { it.decisionId }).takeIf { block ->
            labels.none { it.rawId == block.rawId && it.bankId == block.bankId && it.at > block.at }
        }
    }.sortedBy { it.rawId }
    private val blockedTemplates = latestBlocks.map { it.template }.toSet()
    val latestLabels = labels.filter { it.bankId == seed.bankId && latestBlocks.none { block -> block.rawId == it.rawId && block.at >= it.at } }.groupBy { it.rawId }.map { (_, rows) ->
        rows.maxWith(compareBy<HumanFamilyLabel> { it.at }
            .thenBy { if (it.provenance == HumanLabelSource.USER_MESSAGE_FAMILY.name) 1 else 0 }.thenBy { it.decisionId })
            .let { it.copy(terms = it.terms.toMap()) }
    }.sortedBy { it.rawId }
    private val groups = latestLabels.groupBy { it.template }
    private val templates = groups.mapValues { (_, rows) -> rows.map { it.family }.toSet() }
    // A conflicted template has no statistical authority either. One template is one example.
    private val trusted = groups.values.filter { rows -> rows.first().template !in blockedTemplates && rows.map { it.family }.distinct().size == 1 }
        .map { rows -> rows.maxWith(compareBy<HumanFamilyLabel> { it.at }.thenBy { it.decisionId }) }
    val version = if (latestLabels.isEmpty() && latestBlocks.isEmpty()) seed.version else seed.version + "-human-" + FamilyInputCleaner.sha256(
        latestLabels.joinToString("|") { "${it.rawId}:${it.decisionId}:${it.family}:${it.template}" } +
            latestBlocks.joinToString("|") { "block:${it.rawId}:${it.decisionId}:${it.template}" }).take(16)
    val model: FamilyModel = if (trusted.isEmpty()) this.seed.copy(version = version) else {
        val vocabulary = (this.seed.vocabulary + trusted.flatMap { it.terms.keys }.distinct().sorted()
            .take((4096 - this.seed.vocabulary.size).coerceAtLeast(0))).toSet()
        val documents = this.seed.documents.toMutableMap()
        val counts = this.seed.counts.mapValues { it.value.toMutableMap() }.toMutableMap()
        trusted.forEach { row ->
            documents[row.family] = (documents[row.family] ?: 0L) + 1
            val target = counts.getOrPut(row.family) { mutableMapOf() }
            row.terms.filterKeys { it in vocabulary }.forEach { (term, count) -> target[term] = (target[term] ?: 0L) + count }
        }
        this.seed.copy(version = version, vocabulary = vocabulary, documents = documents, counts = counts)
    }

    fun suggest(bankId: String, features: CleanFamilyFeatures): FamilyPrediction? {
        if (bankId != model.bankId) return null
        if (features.template in blockedTemplates) return null
        val choices = templates[features.template]
        if (choices != null) {
            if (choices.size != 1 || choices.single() !in FamilyModel.FINANCIAL) return null
            return FamilyPrediction(choices.single(), 1.0, version, SuggestionOrigin.HUMAN_TEMPLATE)
        }
        val prediction = model.predict(features) ?: return null
        if (!model.accepts(prediction)) return null
        return FamilyPrediction(prediction.family, prediction.score, version, SuggestionOrigin.ML)
    }

    companion object {
        const val MIN_ACCEPTED_VALIDATION_TEMPLATES = 8
        fun checked(seed: FamilyModel, labels: List<HumanFamilyLabel>, validation: List<FamilyEvaluation>,
            blocks: List<HumanTemplateBlock> = emptyList()): FamilyClassifier {
            val eligible = validation.filter { it.runtimeEligible && it.template != null }.distinctBy { it.template }
            fun features(test: FamilyEvaluation) = CleanFamilyFeatures.create("",test.terms,requireNotNull(test.template))
            val initial = FamilyClassifier(seed, labels, blocks)
            val conflicts = eligible.filter { test -> initial.suggest(seed.bankId,features(test))?.let {
                it.origin == SuggestionOrigin.HUMAN_TEMPLATE && it.family != test.family
            } == true }.map { test -> HumanTemplateBlock(seed.bankId,"validation:${test.template}",
                "validation:${test.template}",requireNotNull(test.template),0) }
            val allBlocks = blocks + conflicts
            val candidate = FamilyClassifier(seed, labels, allBlocks)
            val safe = seed.enabledFamilies.filterTo(mutableSetOf()) { family ->
                val accepted = eligible.filter { test -> candidate.suggest(seed.bankId,features(test))?.let {
                    it.origin == SuggestionOrigin.ML && it.family == family
                } == true }
                accepted.count { it.family == family && it.passesValidation } >= MIN_ACCEPTED_VALIDATION_TEMPLATES && accepted.all { it.family == family }
            }
            return FamilyClassifier(seed.copy(enabledFamilies = safe), labels, allBlocks)
        }
    }
}
