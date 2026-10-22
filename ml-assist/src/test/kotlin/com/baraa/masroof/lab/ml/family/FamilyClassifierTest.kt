package com.baraa.masroof.lab.ml.family

import org.junit.Assert.*
import org.junit.Test

class FamilyClassifierTest {
    @Test fun aBlockRevokesHumanAndStatisticalAuthorityAndOnlyANewerExplicitDecisionClearsIt() {
        val f = requireNotNull(FamilyInputCleaner.clean("سداد تمويل خاص", listOf("من", "القسط")).features)
        val seed = FamilyModel("BANK_TEST","seed",f.terms.keys,mapOf(SmsFamilyLabel.FEE to 1L),
            mapOf(SmsFamilyLabel.FEE to f.terms.mapValues { it.value.toLong() }),setOf(SmsFamilyLabel.FEE))
        val label = HumanFamilyLabel("BANK_TEST","raw","old",SmsFamilyLabel.FEE,f.template,f.terms,1,"USER_MESSAGE_FAMILY")
        val block = HumanTemplateBlock("BANK_TEST","raw","edit",f.template,2)
        val blocked = FamilyClassifier(seed,listOf(label),listOf(block))
        assertNull(blocked.suggest("BANK_TEST",f))
        assertTrue(blocked.latestLabels.isEmpty())
        assertEquals(seed.documents,blocked.model.documents)
        assertEquals(SuggestionOrigin.HUMAN_TEMPLATE,FamilyClassifier(seed,listOf(label.copy(at=3)),listOf(block)).suggest("BANK_TEST",f)!!.origin)
        assertNotNull(FamilyClassifier(seed, emptyList(),listOf(block.copy(bankId="OTHER_BANK"))).suggest("BANK_TEST",f))
    }

    @Test fun activationRequiresEightAcceptedDistinctNaturallyEligibleValidationTemplates() {
        val active = seed.copy(enabledFamilies=setOf(SmsFamilyLabel.FEE))
        fun row(index: Int) = FamilyEvaluation(SmsFamilyLabel.FEE,f.terms,true,FamilyInputCleaner.sha256("validation-$index"),true)
        assertTrue(FamilyClassifier.checked(active,emptyList(),listOf(row(1))).model.enabledFamilies.isEmpty())
        assertTrue(FamilyClassifier.checked(active,emptyList(),List(10) { row(1) }).model.enabledFamilies.isEmpty())
        assertTrue(FamilyClassifier.checked(active,emptyList(),(1..8).map { row(it).copy(runtimeEligible=false) }).model.enabledFamilies.isEmpty())
        assertEquals(active.enabledFamilies,FamilyClassifier.checked(active,emptyList(),(1..8).map(::row)).model.enabledFamilies)
        assertTrue(FamilyClassifier.checked(active,emptyList(),(1..8).map(::row) + row(9).copy(family=SmsFamilyLabel.OTP)).model.enabledFamilies.isEmpty())
    }
    private val f = requireNotNull(FamilyInputCleaner.clean("سداد تمويل خاص", listOf("من", "القسط")).features)
    private val seed = FamilyModel("BANK_TEST", "seed", f.terms.keys, mapOf(SmsFamilyLabel.FEE to 1L),
        mapOf(SmsFamilyLabel.FEE to f.terms.mapValues {it.value.toLong()}), emptySet())
    private fun label(id: String = "one", family: SmsFamilyLabel = SmsFamilyLabel.FINANCING_INSTALLMENT, at: Long = 1) =
        HumanFamilyLabel("BANK_TEST", id, "decision-$id-$at", family, f.template, f.terms, at, "USER_MESSAGE_FAMILY")

    @Test fun conflictingTemplatesNeitherTrainNorPropose() {
        val c = FamilyClassifier(seed, listOf(label(), label("two", SmsFamilyLabel.FEE)))
        assertNull(c.suggest("BANK_TEST", f))
        assertEquals(seed.documents, c.model.documents)
        assertEquals(seed.counts, c.model.counts)
    }

    @Test fun repeatedMessagesDoNotOverweightOneTemplate() {
        val c = FamilyClassifier(seed, listOf(label(), label("two"), label("three")))
        assertEquals(1L, c.model.documents[SmsFamilyLabel.FINANCING_INSTALLMENT])
        assertEquals(SuggestionOrigin.HUMAN_TEMPLATE, c.suggest("BANK_TEST", f)!!.origin)
        assertNull(c.suggest("OTHER_BANK", f))
    }

    @Test fun latestHumanIgnoreRevokesEarlierFamilyWithoutAutoIgnoringNewEvidence() {
        val ignored = label(family=SmsFamilyLabel.NON_FINANCIAL, at=2).copy(provenance="USER_NON_FINANCIAL")
        val c = FamilyClassifier(seed, listOf(label(), ignored))
        assertEquals(listOf(ignored), c.latestLabels)
        assertNull(c.suggest("BANK_TEST", f))
    }

    @Test fun modelPredictionsAndMalformedLabelsAreNeverTrainingTruth() {
        assertThrows(IllegalArgumentException::class.java) {label().copy(provenance="MODEL_SUGGESTED")}
        assertThrows(IllegalArgumentException::class.java) {label().copy(family=SmsFamilyLabel.UNKNOWN)}
        assertThrows(IllegalArgumentException::class.java) {label().copy(terms=mapOf("w:sarah" to 1))}
        assertThrows(IllegalArgumentException::class.java) {label().copy(template="old-incompatible-template")}
    }
    @Test fun runtimeValidationExercisesHumanTemplatePrecedenceAndExplainsConflictsWithHeldOutTruth() {
        val validation=listOf(FamilyEvaluation(SmsFamilyLabel.FEE,f.terms,true,f.template,true))
        val candidate=FamilyClassifier.checked(seed,listOf(label()),validation)
        assertNull(candidate.suggest("BANK_TEST",f))
        assertTrue(candidate.latestBlocks.any { it.template == f.template })
        assertEquals(seed.documents,candidate.model.documents)
    }
}
