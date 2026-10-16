package com.baraa.masroof.lab.engine

import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Explicit repeatable candidate timing; never a financial/holdout/M0 acceptance gate. */
object SharedNetworkBenchmarkExport {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val output = File(args[1]).apply { mkdirs() }
        val now = Instant.parse("2026-10-16T12:00:00Z")
        val library = SharedRuleDefaults.create()
        val cases = File(args[0]).walkTopDown().filter { it.extension == "json" && it.parentFile.name != "reference" }
            .sortedBy { it.path }.map { file ->
                val fixture = Json.parseToJsonElement(file.readText()).jsonObject
                ContextBenchmarkExport.Case(fixture.getValue("id").jsonPrimitive.content, "preserved-regression-not-holdout",
                    fixture.getValue("sender").jsonPrimitive.content, fixture.getValue("body").jsonPrimitive.content,
                    now.toString(), listOf(BankProfile("BANK_ALJAZIRA", "regression", listOf(fixture.getValue("sender").jsonPrimitive.content))), library)
            }.toList()
        require(cases.size == 40)
        val logicalInput = LabJson.format.encodeToString(cases)
        val temporary = java.nio.file.Files.createTempDirectory("masroof-m6-benchmark-")
        val input = temporary.resolve("input.json")
        val raw = temporary.resolve("measurements.json")
        val rows = try {
            java.nio.file.Files.writeString(input, logicalInput)
            ContextBenchmarkExport.main(arrayOf(input.toString(), raw.toString()))
            LabJson.format.decodeFromString<List<ContextBenchmarkExport.Measurement>>(java.nio.file.Files.readString(raw))
        } finally {
            val cleanupFailures = listOf(input, raw, temporary).mapNotNull { path ->
                runCatching { java.nio.file.Files.deleteIfExists(path) }.exceptionOrNull()
            }
            check(cleanupFailures.isEmpty()) { "Could not remove temporary benchmark files" }
        }
        val elapsed = rows.flatMap { it.elapsedMs }.sorted()
        val engine = SharedLabEngine(Clock.fixed(now, ZoneOffset.UTC))
        val repeated = cases.map { engine.parse(it.banks, library, it.sender, it.body, it.id, now) }
        check(repeated == rows.map { it.outcome }) { "Candidate outcome changed across timed/repeat passes" }
        fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        fun percentile(value: Double) = elapsed[(kotlin.math.ceil(elapsed.size * value).toInt() - 1).coerceAtLeast(0)]
        fun Double.ms() = String.format(Locale.ROOT, "%.3f", this)
        val engineRoot = generateSequence(File(args[0]).absoluteFile) { it.parentFile }.first { File(it, "src/main/kotlin").isDirectory }
        val sourceFiles = File(engineRoot, "src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.sortedBy { it.relativeTo(engineRoot).path }.toList()
        check(sourceFiles.isNotEmpty())
        val source = sourceFiles.joinToString("\n") { it.relativeTo(engineRoot).invariantSeparatorsPath + "\n" + it.readText().replace("\r\n", "\n") }
        fun git(vararg arguments: String) = runCatching {
            val process = ProcessBuilder(listOf("git") + arguments).directory(engineRoot).redirectErrorStream(true).start()
            val result = process.inputStream.bufferedReader().use { it.readText().trim() }
            check(process.waitFor() == 0)
            result
        }.getOrDefault("unavailable")
        val prefix = "Outgoing transfer\nAmount: 67 SAR\n"
        val worstBody = (prefix + "x\n".repeat(8_000)).take(16_000)
        val worstConfig = library.network.copy(
            bankAliases = (0 until 100).associate { index ->
                (if (index == 0) "BANK_ALJAZIRA" else "SYNTHETIC_$index") to List(20) { "Bank $index alias $it" }
            },
            sourceBankLabels = List(20) { "Sender label $it" }, destinationBankLabels = List(20) { "Recipient label $it" },
            networkLabels = List(20) { "Network label $it" })
        val worstLibrary = library.copy(network = worstConfig)
        fun worst() = engine.parse(listOf(BankProfile("BANK_ALJAZIRA", "regression", listOf("SYNTHETIC"))), worstLibrary,
            "SYNTHETIC", worstBody, "bounded-worst-case", now)
        repeat(5) { worst() }
        val worstTimes = List(25) { val start = System.nanoTime(); worst(); (System.nanoTime() - start) / 1_000_000.0 }.sorted()
        val report = File(output, "summary.txt")
        report.writeText("""
            Candidate only: ${SharedLabEngine.VERSION}; library revision ${library.version}
            Data: ${cases.size} preserved regression samples; not independently reviewed real holdout
            Input SHA256 (logical case JSON): ${digest(logicalInput)}
            Library SHA256: ${digest(LabJson.format.encodeToString(library))}
            Engine source SHA256 (normalized Kotlin sources): ${digest(source)}
            Git HEAD: ${git("rev-parse", "HEAD")}; tracked working-tree changes: ${git("status", "--porcelain", "--untracked-files=no").isNotEmpty()}
            JVM: ${System.getProperty("java.runtime.version")}; OS: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}; CPUs: ${Runtime.getRuntime().availableProcessors()}
            Method: one JVM; 5 warmups + 1 untimed parse + 25 measured parses per case, sequential; validation+parse timed, file I/O excluded
            Measurements: ${elapsed.size}; p50 ms: ${percentile(.50).ms()}; p95 ms: ${percentile(.95).ms()}; mean ms: ${elapsed.average().ms()}; max ms: ${elapsed.last().ms()}
            Repeat outcomes: ${repeated.size}/${cases.size} identical across independent post-timing pass
            Bounded worst case: ${worstBody.length} UTF-16 units, many short lines, 100 banks x 20 aliases, 20 labels per role/network; 5 warmups + 25 measured parses
            Bounded worst-case p50 ms: ${worstTimes[12].ms()}; p95 ms: ${worstTimes[23].ms()}; max ms: ${worstTimes.last().ms()}
            No M0 comparison, device budget, accepted precision, promotion or baseline speedup is claimed.
        """.trimIndent() + "\n")
        File(output, "case-timings.csv").writeText("id,p50_ms,p95_ms,mean_ms,input_sha256\n" + rows.mapIndexed { index, row ->
            val values = row.elapsedMs.sorted()
            "${row.id},${values[12].ms()},${values[23].ms()},${values.average().ms()},${digest(LabJson.format.encodeToString(cases[index]))}"
        }.joinToString("\n") + "\n")
        println(report.readText())
    }
}
