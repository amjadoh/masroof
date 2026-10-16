package com.baraa.masroof.application.baseline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Production app keeps only a developer-settings bridge into the lab Beta module.
 */
class BetaBridgeEntryPointTest {
    @Test
    fun onlyDeveloperSettings_referencesBetaEntry() {
        val main = File("src/main")
        val hits = main.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filter { file ->
                val text = file.readText()
                text.contains("BetaEntry") || text.contains("lab.beta")
            }
            .map { it.relativeTo(main).invariantSeparatorsPath }
            .toSet()
        assertEquals(
            setOf("kotlin/com/baraa/masroof/presentation/settings/SettingsScreen.kt"),
            hits,
        )
        val settings = File("src/main/kotlin/com/baraa/masroof/presentation/settings/SettingsScreen.kt").readText()
        assertEquals(1, settings.split("BetaEntry.open").size - 1)
        val developer = File(
            "src/main/kotlin/com/baraa/masroof/presentation/settings/SettingsDeveloperScreen.kt",
        ).readText()
        assertTrue(developer.contains("R.string.beta_entry_title"))
        assertFalse(developer.contains("BetaEntry"))
        assertFalse(developer.contains("BetaActivity"))
        assertFalse(
            File("src/main/kotlin/com/baraa/masroof/presentation/navigation/AppNavigation.kt")
                .readText()
                .contains("Beta"),
        )
        assertFalse(File("src/main/AndroidManifest.xml").readText().contains("BetaActivity"))
    }

    @Test
    fun labGradleModules_useLabPathNotParsingLab() {
        val settings = File("../settings.gradle.kts").readText()
        assertTrue(settings.contains("include(\":lab:engine\", \":lab:android\", \":lab:beta\")"))
        assertFalse(settings.contains("parsing-lab"))
        assertTrue(File("../lab/beta/build.gradle.kts").isFile)
        assertTrue(File("../lab/beta/src/main/kotlin/com/baraa/masroof/lab/beta/data/BetaDatabase.kt").isFile)
        assertFalse(File("../parsing-lab").exists())
    }
}
