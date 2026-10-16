package com.baraa.masroof.lab.engine.acceptance

import com.baraa.masroof.lab.engine.*
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Private input stays outside artifacts. Failure output deliberately omits exception messages/paths. */
object M6AcceptanceExport {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 4)
        val root = File(args[0])
        val output = File(args[1]).apply { mkdirs() }
        val manifestFile = args[2].takeIf(String::isNotBlank)?.let(::File)
        val policyFile = args[3].takeIf(String::isNotBlank)?.let(::File)
        val report = File(output, "summary.txt")
        // Invalidate stale success before even reading source/configuration fingerprints.
        report.writeText("M6 measurement BLOCKED: INCOMPLETE_EVALUATION\n")
        var fingerprints = ""
        var completedReport = false
        try {
            val sourceRoot = File(root, "src/main/kotlin")
            val source = sourceRoot.walkTopDown().filter { it.extension == "kt" }.sortedBy { it.relativeTo(root).path }
                .joinToString("\n") { it.relativeTo(root).invariantSeparatorsPath + "\n" + it.readText().replace("\r\n", "\n") }
            require(source.isNotBlank())
            val sourceHash = M6Acceptance.sha(source)
            val library = SharedRuleDefaults.create()
            val libraryHash = M6Acceptance.sha(LabJson.format.encodeToString(library))
            fingerprints = "Candidate source SHA256: $sourceHash\nLibrary SHA256: $libraryHash\n"
            report.writeText(fingerprints + "M6 measurement BLOCKED: INCOMPLETE_EVALUATION\n")
            if (manifestFile == null) {
                report.writeText(fingerprints + "M6 measurement BLOCKED: MISSING_INDEPENDENT_REVIEWED_REAL_HOLDOUT\n" +
                    "No precision/coverage or promotion claim. Regression/synthetic data cannot replace this input.\n" +
                    "Device/M0 performance acceptance and explicit promotion approval remain pending.\n")
                completedReport = true
                println(report.readText())
                error("M6 measurement prerequisites missing; see safe summary")
            }
            fun boundedRead(file: File): String {
                require(file.length() in 1..10_000_000)
                return file.readText()
            }
            val manifestJson = boundedRead(manifestFile)
            val manifest = LabJson.format.decodeFromString<HoldoutManifest>(manifestJson)
            M6Acceptance.validate(manifest)
            val policyJson = policyFile?.let(::boundedRead)
            val policy = policyJson?.let { LabJson.format.decodeFromString<AcceptancePolicy>(it) }
            val corpusRoot = File(root, "src/test/resources/testdata/bank_aljazira")
            val regressionHashes = corpusRoot.walkTopDown().filter { it.extension == "json" && it.parentFile.name != "reference" }
                .map { Json.parseToJsonElement(it.readText()).jsonObject.getValue("body").jsonPrimitive.content }
                .map(M6Acceptance::messageHash).toSet()
            require(regressionHashes.size >= 40)
            val measurements = manifest.cases.map { case ->
                val received = Instant.parse(case.receivedAt)
                M6Acceptance.measure(case, SharedLabEngine(Clock.fixed(received, ZoneOffset.UTC))
                    .parse(case.banks, library, case.sender, case.body, "offline-assessment", received))
            }
            val assessment = M6Acceptance.assess(manifest, policy, measurements, regressionHashes, sourceHash, libraryHash)
            report.writeText("Input SHA256: ${M6Acceptance.sha(manifestJson)}\n" +
                "Candidate source SHA256: $sourceHash\nLibrary SHA256: $libraryHash\n" +
                "Policy SHA256: ${policyJson?.let(M6Acceptance::sha) ?: "missing"}\n" +
                M6Acceptance.safeReport(assessment))
            completedReport = true
            println(report.readText())
            check(assessment.measurementGatesMet) { "M6 measurement gates blocked; see safe summary" }
        } catch (failure: Exception) {
            if (!completedReport) report.writeText(fingerprints + "M6 measurement BLOCKED: INVALID_OR_UNREADABLE_INPUT\nNo input values are printed.\n")
            // Do not chain parsing exceptions, which may quote private input.
            throw IllegalStateException("M6 measurement blocked; inspect local input and safe summary")
        }
    }
}
