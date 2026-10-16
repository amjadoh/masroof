package com.baraa.masroof.lab.engine.acceptance

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class M6AcceptanceExportTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun root(): File = temporary.newFolder().apply {
        File(this, "src/main/kotlin").mkdirs()
        File(this, "src/main/kotlin/Source.kt").writeText("// synthetic source for exporter tests")
    }

    @Test fun missingInputFailsClosedWithCurrentFingerprints() {
        val output = temporary.newFolder()
        val failure = assertThrows(IllegalStateException::class.java) {
            M6AcceptanceExport.main(arrayOf(root().path, output.path, "", ""))
        }
        assertNull(failure.cause)
        val summary = File(output, "summary.txt").readText()
        assertTrue(summary.contains("MISSING_INDEPENDENT_REVIEWED_REAL_HOLDOUT"))
        assertTrue(summary.contains("Candidate source SHA256:"))
        assertFalse(summary.contains("Measurement gates met: true"))
    }

    @Test fun invalidPrivateInputReplacesStaleGreenArtifactWithoutLeakingParserException() {
        val output = temporary.newFolder()
        File(output, "summary.txt").writeText("Measurement gates met: true")
        val input = temporary.newFile().apply { writeText("{\"secret-private-body\":") }
        val failure = assertThrows(IllegalStateException::class.java) {
            M6AcceptanceExport.main(arrayOf(root().path, output.path, input.path, ""))
        }
        assertNull(failure.cause)
        assertFalse(failure.message!!.contains("secret-private-body"))
        val summary = File(output, "summary.txt").readText()
        assertTrue(summary.contains("INVALID_OR_UNREADABLE_INPUT"))
        assertFalse(summary.contains("secret-private-body"))
        assertFalse(summary.contains("Measurement gates met: true"))
    }

    @Test fun missingSourceCannotLeaveAnOldGreenReport() {
        val output = temporary.newFolder()
        File(output, "summary.txt").writeText("Measurement gates met: true")
        val failure = assertThrows(IllegalStateException::class.java) {
            M6AcceptanceExport.main(arrayOf(temporary.newFolder().path, output.path, "", ""))
        }
        assertNull(failure.cause)
        val summary = File(output, "summary.txt").readText()
        assertTrue(summary.contains("INVALID_OR_UNREADABLE_INPUT"))
        assertFalse(summary.contains("Measurement gates met: true"))
    }

    @Test fun reportCollisionCannotOverwriteManifestOrPolicyInput() {
        listOf("summary.txt", "other-private-input.json").forEach { filename ->
            listOf(true, false).forEach { isManifest ->
                val output = temporary.newFolder()
                val input = File(output, filename).apply { writeText("private-original-input") }
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    M6AcceptanceExport.main(arrayOf(root().path, output.path,
                        if (isManifest) input.path else "", if (isManifest) "" else input.path))
                }
                assertEquals("private-original-input", input.readText())
                assertFalse(failure.message!!.contains(input.path))
            }
        }
    }

    @Test fun reportHardLinkCannotOverwritePrivateInputOutsideOutputDirectory() {
        val output = temporary.newFolder()
        val input = temporary.newFile().apply { writeText("private-original-input") }
        java.nio.file.Files.createLink(File(output, "summary.txt").toPath(), input.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            M6AcceptanceExport.main(arrayOf(root().path, output.path, input.path, ""))
        }
        assertEquals("private-original-input", input.readText())
    }
}
