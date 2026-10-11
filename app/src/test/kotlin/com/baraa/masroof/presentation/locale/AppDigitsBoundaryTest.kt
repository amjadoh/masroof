package com.baraa.masroof.presentation.locale

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDigitsBoundaryTest {
    @Test
    fun presentationUsesSharedText_andResourcesContainNoEasternDigits() {
        val presentation = File("src/main/kotlin/com/baraa/masroof/presentation")
        assertTrue(presentation.isDirectory)
        val directText = Regex("import androidx\\.compose\\.(?:material3|material|foundation\\.text)\\.(?:Text|BasicText|ClickableText)\\b|androidx\\.compose\\.material3\\.Text\\(")
        val bypasses = presentation.walkTopDown().filter { it.extension == "kt" && it.name != "MasroofText.kt" }
            .filter { directText.containsMatchIn(it.readText()) }.map { it.path }.toList()
        assertTrue("Use MasroofText so imported digits are normalized: $bypasses", bypasses.isEmpty())
        val easternDigits = Regex("[\\u0660-\\u0669\\u06F0-\\u06F9]")
        val resources = File("src/main/res")
        assertTrue(resources.isDirectory)
        val violations = resources.walkTopDown().filter { it.extension == "xml" }
            .filter { easternDigits.containsMatchIn(it.readText()) }.map { it.path }.toList()
        assertTrue("Display resources must use 0123456789: $violations", violations.isEmpty())
    }
}
