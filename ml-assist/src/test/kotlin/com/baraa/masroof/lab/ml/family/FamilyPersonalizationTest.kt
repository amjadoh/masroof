package com.baraa.masroof.lab.ml.family

import org.junit.Assert.*
import org.junit.Test

class FamilyPersonalizationTest {
    private val features = requireNotNull(FamilyInputCleaner.clean("سداد تمويل خاص", listOf("من", "القسط")).features)
    private val seed = FamilyModel("BANK_TEST", "seed", features.terms.keys,
        mapOf(SmsFamilyLabel.FEE to 1L), mapOf(SmsFamilyLabel.FEE to features.terms.mapValues { it.value.toLong() }),
        setOf(SmsFamilyLabel.FEE))
    private fun label(index: Int) = HumanFamilyLabel("BANK_TEST", "raw-$index", "decision-$index", SmsFamilyLabel.FEE,
        FamilyInputCleaner.sha256("template-$index"), features.terms, index.toLong(), "USER_MESSAGE_FAMILY")

    @Test fun sparseFeedbackKeepsSeedStatisticsButStillHonorsTheHumanTemplate() {
        val label = label(1).copy(template = features.template)
        val result = FamilyPersonalization.rebuild(seed, listOf(label), emptyList())
        assertFalse(result.personalUsed)
        assertEquals(seed.counts, result.classifier.model.counts)
        assertEquals(SuggestionOrigin.HUMAN_TEMPLATE, result.classifier.suggest("BANK_TEST", features)!!.origin)
        assertEquals("NEEDS_EVALUATIONS", result.reason)
    }

    @Test fun heldOutTemplatesNeverEnterStatisticalTrainingAndDuplicateMessagesDoNotAddExamples() {
        val labels = (1..100).map(::label)
        val result = FamilyPersonalization.rebuild(seed, labels, emptyList())
        assertTrue(result.validationTemplates >= 8)
        assertEquals(100, result.trainingTemplates + result.validationTemplates)
        val duplicated = labels + labels.map { it.copy(rawId = "copy-${it.rawId}", decisionId = "copy-${it.decisionId}") }
        val repeated = FamilyPersonalization.rebuild(seed, duplicated, emptyList())
        assertEquals(result.trainingTemplates, repeated.trainingTemplates)
        assertEquals(result.validationTemplates, repeated.validationTemplates)
        assertEquals(result.candidate.model.counts, repeated.candidate.model.counts)
        val trainingCount = labels.count { !FamilyPersonalization.isValidation(it.bankId, it.template) }
        assertEquals(1L + trainingCount, result.candidate.model.documents[SmsFamilyLabel.FEE])
        assertFalse(result.personalUsed)
    }

    @Test fun seedWithNoEnabledFamiliesCannotBeActivatedByPersonalLabels() {
        val result = FamilyPersonalization.rebuild(seed.copy(enabledFamilies = emptySet()), (1..100).map(::label), emptyList())
        assertFalse(result.personalUsed)
        assertTrue(result.classifier.model.enabledFamilies.isEmpty())
        assertEquals("SEED_NOT_ELIGIBLE", result.reason)
    }

    @Test fun personalStatisticsGeneralizeBeyondHumanTemplatesOnlyAfterIndependentValidation() {
        val vocabulary = setOf("w:رسوم", "w:شراء", "w:خاص")
        val base = FamilyModel("BANK_TEST", "seed", vocabulary,
            mapOf(SmsFamilyLabel.FEE to 1L, SmsFamilyLabel.PURCHASE to 100L),
            mapOf(SmsFamilyLabel.FEE to mapOf("w:رسوم" to 100L),
                SmsFamilyLabel.PURCHASE to mapOf("w:شراء" to 100L)), setOf(SmsFamilyLabel.FEE))
        val labels = (1..100).map { label(it).copy(terms = mapOf("w:خاص" to 5)) }
        val safety = (1..8).map { FamilyEvaluation(SmsFamilyLabel.FEE, mapOf("w:رسوم" to 5),
            true, FamilyInputCleaner.sha256("safety-$it"), true) }
        val unseen = CleanFamilyFeatures.create("خاص", mapOf("w:خاص" to 5), FamilyInputCleaner.sha256("unseen"))
        assertNull(FamilyClassifier(base).suggest(base.bankId, unseen))
        val result = FamilyPersonalization.rebuild(base, labels, safety)
        assertTrue(result.personalUsed)
        assertNotEquals(base.counts, result.classifier.model.counts)
        assertEquals(SuggestionOrigin.ML, result.classifier.suggest(base.bankId, unseen)!!.origin)
        assertEquals(SmsFamilyLabel.FEE, result.classifier.suggest(base.bankId, unseen)!!.family)
        val revoked = FamilyPersonalization.rebuild(base, emptyList(), safety)
        assertFalse(revoked.personalUsed)
        assertNull(revoked.classifier.suggest(base.bankId, unseen))
    }
}
