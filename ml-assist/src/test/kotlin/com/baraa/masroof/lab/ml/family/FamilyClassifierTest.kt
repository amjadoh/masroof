package com.baraa.masroof.lab.ml.family

import org.junit.Assert.*
import org.junit.Test

class FamilyClassifierTest {
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
}
