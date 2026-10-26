package com.baraa.masroof.engine.bank

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Checks the actual source/build graph, including new modules added after this test. */
class ModuleBoundaryTest {
    private val root = File("..").canonicalFile
    private val projectDependency = Regex("project\\(\"([^\"]+)\"\\)")

    @Test fun removedMlImplementationCannotReturnToTheBankEngine() {
        val old = File(root, "bank-engine/src/main/kotlin/com/baraa/masroof/engine/bank/learning")
        assertFalse("Duplicate ML implementation in bank-engine", old.walkTopDown().any { it.extension == "kt" })
        assertFalse("Duplicate training command", File(root, "bank-engine/tools/train_family_model.py").exists())
    }

    @Test fun pureCoresImportOnlyJdkKotlinAndTheirOwnedContracts() {
        val common = listOf("java.", "kotlin.")
        mapOf(
            "ml-assist" to common + listOf("com.baraa.masroof.ml.", "com.baraa.masroof.analytics."),
            "finance-core" to common + listOf(
                "com.baraa.masroof.finance.",
                "com.baraa.masroof.core.money.",
                "com.baraa.masroof.domain.model.",
                "com.baraa.masroof.domain.ids.",
                "com.baraa.masroof.domain.period.",
                "com.baraa.masroof.domain.rules.",
                "com.baraa.masroof.domain.assembly.",
                "com.baraa.masroof.domain.matching.",
                "com.baraa.masroof.domain.statement.",
            ),
            "bank-engine" to common + listOf("com.baraa.masroof.engine.bank.", "com.baraa.masroof.finance.value."),
        ).forEach { (module, allowed) ->
            val sources = File(root, "$module/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
            assertTrue("Missing $module source tree", sources.isNotEmpty())
            sources.forEach { file ->
                Regex("(?m)^import ([^\\s]+)").findAll(file.readText()).forEach { match ->
                    assertTrue("${file.name} imports ${match.groupValues[1]}",
                        allowed.any { match.groupValues[1].startsWith(it) })
                }
                // Fully qualified calls must not bypass the import boundary. In particular,
                // bank-engine can use finance values, but never financial posting policies.
                Regex("\\bcom\\.baraa\\.masroof(?:\\.[A-Za-z_][A-Za-z_0-9]*|\\.\\*)+")
                    .findAll(file.readText()).forEach { match ->
                        assertTrue("${file.name} references ${match.value}",
                            allowed.any { match.value == it.removeSuffix(".") || match.value.startsWith(it) })
                    }
                assertFalse("Fully qualified Android API in ${file.name}",
                    Regex("\\b(android|androidx)\\.").containsMatchIn(file.readText()))
                if (module == "bank-engine") assertFalse("ML implementation leaked into engine: ${file.name}",
                    Regex("com\\.baraa\\.masroof\\.lab\\.(ml|bank\\.learning)\\.").containsMatchIn(file.readText()))
                assertFalse("Legacy interpreter reference in ${file.name}",
                    Regex("com\\.baraa\\.masroof\\.lab\\.(engine|copy)\\.").containsMatchIn(file.readText()))
            }
        }
    }

    @Test fun pureModulesNeverDependOnAppAndModuleGraphHasNoCycles() {
        val buildFiles = listOf(File(root, "app/build.gradle.kts")) +
            listOf("bank-engine", "finance-core", "ml-assist").map { File(root, "$it/build.gradle.kts") }
        val graph = buildFiles.associate { file ->
            val module = ":" + file.parentFile.relativeTo(root).invariantSeparatorsPath.replace('/', ':')
            module to projectDependency.findAll(file.readText()).map { it.groupValues[1] }.toSet()
        }
        graph.filterKeys { it != ":app" }.forEach { (module, deps) ->
            assertFalse("$module depends on production", ":app" in deps)
        }
        assertFalse("Removed legacy modules must not return", graph.keys.any { it in setOf(":lab:engine", ":lab:android") })
        assertEquals(emptySet<String>(), graph[":ml-assist"])
        assertEquals(emptySet<String>(), graph[":finance-core"])
        assertEquals(setOf(":finance-core"), graph[":bank-engine"])
        assertEquals(setOf(":ui-kit", ":finance-core", ":bank-engine", ":ml-assist"), graph[":app"])
        fun visit(module: String, ancestors: Set<String>) {
            assertFalse("Dependency cycle: $ancestors -> $module", module in ancestors)
            graph[module].orEmpty().forEach { visit(it, ancestors + module) }
        }
        graph.keys.forEach { visit(it, emptySet()) }
    }

    @Test fun retiredInterpreterCannotShip() {
        val app = File(root, "app/src")
        assertFalse(app.walkTopDown().any { it.name in setOf("AlJaziraMessageParser.kt", "AlJaziraParsingPipeline.kt", "AlJaziraSmsAdapter.kt") })
        assertFalse(File(root, "settings.gradle.kts").readText().contains(":lab:"))
        assertFalse(File(root, "app/build.gradle.kts").readText().contains("generateProductionReplica"))
    }

    @Test fun referenceProfilesAndTestAdapterStayOutsideProductionSources() {
        val production = listOf("bank-engine", "app").flatMap { module ->
            File(root, "$module/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
        }
        assertFalse("Reference vocabulary or adapter returned to production", production.any {
            it.name in setOf("RuleLibrary.kt", "AlJaziraRuleProfile.kt", "NewEngineSmsAdapter.kt", "InterpretationTestAdapter.kt")
        })
        for (name in listOf("RuleLibrary.kt", "AlJaziraRuleProfile.kt")) {
            assertTrue("Independent reference missing: $name", File(root,
                "bank-engine/src/testFixtures/kotlin/com/baraa/masroof/engine/bank/rules/$name").isFile)
        }
    }
}
