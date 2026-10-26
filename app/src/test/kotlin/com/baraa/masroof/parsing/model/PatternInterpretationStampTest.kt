package com.baraa.masroof.parsing.model

import org.junit.Assert.*
import org.junit.Test

class PatternInterpretationStampTest {
    private val fingerprint = "a".repeat(64)
    private fun reasons(stamp: PatternInterpretationStamp) =
        listOf("engine:${stamp.engine}", "rules:${stamp.rules}") + stamp.definitionReasons()

    @Test fun bothFormatsKeepTheirPersistedSpelling() {
        val facts = PatternInterpretationStamp(fingerprint, "purchase", 2, false)
        assertEquals(listOf("engine:message-pattern-facts-v1", "rules:$fingerprint",
            "pattern_config:$fingerprint", "pattern_revision:purchase:2"), reasons(facts))
        val roles = facts.copy(roleProfile = true)
        assertEquals(listOf("engine:message-pattern-roles-v1", "rules:$fingerprint:purchase:2"), reasons(roles))
        for (stamp in listOf(facts, roles)) {
            assertEquals(stamp, PatternInterpretationStamp.decode(reasons(stamp)))
            assertTrue(PatternInterpretationStamp.isPatternDerived(reasons(stamp)))
        }
    }

    @Test fun corruptAndDuplicateMetadataRemainIneligible() {
        for (roles in listOf(false, true)) {
            val original = reasons(PatternInterpretationStamp(fingerprint, "purchase", 2, roles))
            for (index in original.indices) {
                assertNull(PatternInterpretationStamp.decode(original.filterIndexed { i, _ -> i != index }))
                assertNull(PatternInterpretationStamp.decode(original + original[index]))
            }
            assertNull(PatternInterpretationStamp.decode(original.map { it.replace(fingerprint, "invalid") }))
            assertNull(PatternInterpretationStamp.decode(original.map { it.replace(":2", ":02") }))
            assertNull(PatternInterpretationStamp.decode(original + "engine:other"))
        }
        assertTrue(PatternInterpretationStamp.isPatternDerived(listOf("pattern_config:damaged")))
        assertFalse(PatternInterpretationStamp.isPatternDerived(listOf("engine:other", "rules:old")))
    }
}
