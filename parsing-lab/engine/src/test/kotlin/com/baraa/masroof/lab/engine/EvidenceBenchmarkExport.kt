package com.baraa.masroof.lab.engine

import com.baraa.masroof.lab.engine.evidence.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Offline, paired measurement. Raw analysis outputs must remain in a private local directory. */
object EvidenceBenchmarkExport {
    @Serializable data class Case(val id: String, val dataset: String, val sender: String, val body: String,
        val receivedAt: String, val banks: List<BankProfile>, val expected: ExpectedResult? = null,
        val library: SharedRuleLibrary = SharedRuleDefaults.create(), val knowledge: EvidenceKnowledge? = null)
    @Serializable data class Measurement(val id: String, val dataset: String, val expected: ExpectedResult?,
        val shared: LabOutcome, val evidence: EvidenceAnalysis, val sharedMs: List<Double>, val evidenceMs: List<Double>,
        val sharedFirstMs: Double, val evidenceFirstMs: Double)
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val cases = LabJson.format.decodeFromString<List<Case>>(File(args[0]).readText())
        require(cases.size in 1..1000 && cases.map { it.id }.distinct().size == cases.size)
        val knowledge = EvidenceDefaults.create()
        File(args[1] + ".default-knowledge.json").writeText(LabJson.format.encodeToString(knowledge))
        val measurements = cases.mapIndexed { index, case ->
            val now = Instant.parse(case.receivedAt); val clock = Clock.fixed(now, ZoneOffset.UTC)
            val old = SharedLabEngine(clock); val new = EvidenceLabEngine(clock)
            fun a() = old.parse(case.banks, case.library, case.sender, case.body, case.id, now)
            fun b() = new.parse(case.banks, case.knowledge ?: knowledge, case.sender, case.body, case.id, now)
            var shared: LabOutcome; var evidence: EvidenceAnalysis
            val firstA = System.nanoTime(); shared = a(); val firstAMs = (System.nanoTime() - firstA) / 1_000_000.0
            val firstB = System.nanoTime(); evidence = b(); val firstBMs = (System.nanoTime() - firstB) / 1_000_000.0
            repeat(5) { a(); b() }
            val at = mutableListOf<Double>(); val bt = mutableListOf<Double>()
            repeat(25) { iteration ->
                fun ma() { val start = System.nanoTime();shared = a();at += (System.nanoTime() - start) / 1_000_000.0 }
                fun mb() { val start = System.nanoTime();evidence = b();bt += (System.nanoTime() - start) / 1_000_000.0 }
                if ((index + iteration) % 2 == 0) { ma();mb() } else { mb();ma() }
            }
            Measurement(case.id, case.dataset, case.expected, shared, evidence, at, bt, firstAMs, firstBMs)
        }
        File(args[1]).writeText(LabJson.format.encodeToString(measurements))
        println("Measured ${cases.size} cases with ${SharedLabEngine.VERSION} and ${EvidenceLabEngine.VERSION}")
    }
}
