package com.baraa.masroof.lab.engine

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LabIsolationTest {
    @Test fun labHasNoProductionImportsOrAppProjectDependency() {
        val root = File("..").takeIf { File(it, "copied-sources.json").exists() } ?: File("lab")
        assertTrue(root.isDirectory)
        val imports = Regex("(?m)^import (com\\.baraa\\.masroof\\.[^\\s]+)")
        root.walkTopDown().filter { it.isFile && it.extension == "kt" && "build" !in it.toPath().map { part -> part.toString() } }.forEach { file ->
            imports.findAll(file.readText()).forEach { match ->
                assertTrue("Production dependency in $file: ${match.value}", match.groupValues[1].startsWith("com.baraa.masroof.lab."))
            }
        }
        listOf("engine", "android", "beta").forEach { module ->
            val gradle = File(root, "$module/build.gradle.kts")
            if (!gradle.isFile) return@forEach
            assertFalse(gradle.readText().contains("project(\":app\")"))
        }
        File(root, "android/src/main/kotlin/com/baraa/masroof/lab/presentation").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.forEach { file ->
                assertFalse("UI must use application workflows: $file", file.readText().contains("com.baraa.masroof.lab.data."))
            }
    }
}
