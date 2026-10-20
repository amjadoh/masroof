package com.baraa.masroof.lab.bank

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Checks the actual source/build graph, including new modules added after this test. */
class ModuleBoundaryTest {
    private val root = File("../..").canonicalFile
    private val projectDependency = Regex("project\\(\"([^\"]+)\"\\)")

    @Test fun pureCoresImportOnlyJdkKotlinAndTheirOwnedContracts() {
        val common = listOf("java.", "kotlin.")
        mapOf(
            "finance-core" to common + "com.baraa.masroof.lab.finance.",
            "bank-engine" to common + listOf("com.baraa.masroof.lab.bank.", "com.baraa.masroof.lab.finance.value."),
        ).forEach { (module, allowed) ->
            val sources = File(root, "lab/$module/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
            assertTrue("Missing $module source tree", sources.isNotEmpty())
            sources.forEach { file ->
                Regex("(?m)^import ([^\\s]+)").findAll(file.readText()).forEach { match ->
                    assertTrue("${file.name} imports ${match.groupValues[1]}",
                        allowed.any { match.groupValues[1].startsWith(it) })
                }
                assertFalse("Fully qualified Android API in ${file.name}",
                    Regex("\\b(android|androidx)\\.").containsMatchIn(file.readText()))
                assertFalse("Legacy interpreter reference in ${file.name}",
                    Regex("com\\.baraa\\.masroof\\.lab\\.(engine|copy)\\.").containsMatchIn(file.readText()))
            }
        }
    }

    @Test fun labNeverDependsOnAppAndModuleGraphHasNoCycles() {
        val buildFiles = listOf(File(root, "app/build.gradle.kts")) +
            File(root, "lab").listFiles()!!.map { File(it, "build.gradle.kts") }.filter { it.isFile }
        val graph = buildFiles.associate { file ->
            val module = ":" + file.parentFile.relativeTo(root).invariantSeparatorsPath.replace('/', ':')
            module to projectDependency.findAll(file.readText()).map { it.groupValues[1] }.toSet()
        }
        graph.filterKeys { it.startsWith(":lab:") }.forEach { (module, deps) ->
            assertFalse("$module depends on production", ":app" in deps)
        }
        assertFalse("Removed legacy modules must not return", graph.keys.any { it in setOf(":lab:engine", ":lab:android") })
        assertEquals(emptySet<String>(), graph[":lab:finance-core"])
        assertEquals(setOf(":lab:finance-core"), graph[":lab:bank-engine"])
        assertEquals(setOf(":ui-kit", ":lab:finance-core", ":lab:bank-engine"), graph[":lab:beta"])
        fun visit(module: String, ancestors: Set<String>) {
            assertFalse("Dependency cycle: $ancestors -> $module", module in ancestors)
            graph[module].orEmpty().forEach { visit(it, ancestors + module) }
        }
        graph.keys.forEach { visit(it, emptySet()) }
    }

    @Test fun betaHasNoDirectProductionImports() {
        val imports = Regex("(?m)^import (com\\.baraa\\.masroof\\.[^\\s]+)")
        File(root, "lab/beta/src/replica/kotlin").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            imports.findAll(file.readText()).forEach { match ->
                assertTrue("${file.name} imports production ${match.groupValues[1]}",
                    match.groupValues[1].startsWith("com.baraa.masroof.lab.") ||
                        match.groupValues[1].startsWith("com.baraa.masroof.ui."))
            }
        }
    }
}
