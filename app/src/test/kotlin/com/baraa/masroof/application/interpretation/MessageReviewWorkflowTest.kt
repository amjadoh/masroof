package com.baraa.masroof.application.interpretation

import org.junit.Assert.*
import org.junit.Test

class MessageReviewWorkflowTest {
    private fun candidate(id: String, bank: String = "BANK_A", template: String = "one", unsupported: Boolean = false) =
        MessageReviewCandidate(MessageReviewExample(id, 100, unsupported), bank, template)

    @Test fun selectsAtMostThreeDistinctBankQualifiedTemplatesAndPrioritizesUnsupportedWording() {
        val input = listOf(candidate("a"), candidate("repeat"), candidate("other-bank", bank = "BANK_B"),
            candidate("unsupported", template = "two", unsupported = true), candidate("fourth", template = "three").let {
                it.copy(example = it.example.copy(receivedAt = 0))
            })
        val selected = MessageReviewWorkflow.select(input)
        assertEquals(3, selected.size)
        assertEquals("unsupported", selected.first().reviewId)
        assertEquals(selected, MessageReviewWorkflow.select(input.reversed()))
        assertTrue(selected.any { it.reviewId == "other-bank" })
        assertFalse(selected.any { it.reviewId == "repeat" })
    }

    @Test fun revokedOrMissingExamplesDisappearRatherThanKeepingAPersistedReviewSession() {
        val input = listOf(candidate("a"), candidate("b", template = "two"))
        assertEquals(listOf("b"), MessageReviewWorkflow.select(input.drop(1)).map { it.reviewId })
        assertTrue(MessageReviewWorkflow.select(emptyList()).isEmpty())
    }
}
