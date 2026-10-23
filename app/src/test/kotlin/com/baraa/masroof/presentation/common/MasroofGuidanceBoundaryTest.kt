package com.baraa.masroof.presentation.common

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MasroofGuidanceBoundaryTest {
    @Test
    fun explanatoryHintsUseSharedGuidance_insteadOfPermanentBodyText() {
        val root = File("src/main/kotlin/com/baraa/masroof/presentation")
        assertTrue(root.isDirectory)
        val inlineHint = Regex("\\b(?:Text|MasroofText)\\(\\s*(?:text\\s*=\\s*)?stringResource\\(R\\.string\\.([a-z0-9_]+(?:_hint|_basis)|lab_analysis_need_data)\\b")
        val violations = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            inlineHint.findAll(file.readText()).filter { it.groupValues[1] != "transaction_list_search_hint" }
                .map { "${file.path}:${it.groupValues[1]}" }.asSequence()
        }.toList()
        assertTrue("Contextual explanations must use MasroofGuidance: $violations", violations.isEmpty())
    }
}
