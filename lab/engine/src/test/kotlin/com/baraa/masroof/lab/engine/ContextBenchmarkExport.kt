package com.baraa.masroof.lab.engine

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Measures the actual shared engine. Input/output are private, explicitly chosen local files. */
object ContextBenchmarkExport {
    @Serializable
    data class Case(
        val id: String, val dataset: String, val sender: String, val body: String,
        val receivedAt: String, val banks: List<BankProfile>, val library: SharedRuleLibrary = SharedRuleDefaults.create(),
        val expected: ExpectedResult? = null,
    )

    @Serializable
    data class Measurement(
        val id: String, val outcome: LabOutcome, val elapsedMs: List<Double>,
        val differences: List<String>? = null,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Provide input and output file paths" }
        val cases = LabJson.format.decodeFromString<List<Case>>(File(args[0]).readText())
        require(cases.isNotEmpty() && cases.size <= 1_000)
        require(cases.map { it.id }.distinct().size == cases.size)
        // Warm each case before measurement; parsing and library validation are both timed.
        val measured = cases.map { case ->
            val receivedAt = Instant.parse(case.receivedAt)
            val engine = SharedLabEngine(Clock.fixed(receivedAt, ZoneOffset.UTC))
            fun parse() = engine.parse(case.banks, case.library, case.sender, case.body, case.id, receivedAt)
            repeat(5) { parse() }
            val elapsed = mutableListOf<Double>()
            var outcome = parse()
            repeat(25) {
                val start = System.nanoTime()
                outcome = parse()
                elapsed += (System.nanoTime() - start) / 1_000_000.0
            }
            Measurement(case.id, outcome, elapsed, case.expected?.let { LabComparison.differences(it, outcome) })
        }
        File(args[1]).writeText(LabJson.format.encodeToString(measured))
        println("Exported ${measured.size} lab-only rule measurements (${SharedLabEngine.VERSION})")
    }
}
