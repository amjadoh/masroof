package com.baraa.masroof.lab.application

import androidx.room.withTransaction
import com.baraa.masroof.lab.data.*
import com.baraa.masroof.lab.engine.*
import com.baraa.masroof.lab.engine.evidence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.util.UUID

data class LabComparisonView(val id: String, val from: String, val to: String)
data class LabComparisonItem(val sampleId: String, val body: String, val expected: ExpectedResult?,
    val shared: LabOutcome, val evidence: EvidenceAnalysis, val sharedMs: Double, val evidenceMs: Double) {
    val sharedDifferences get() = expected?.let { ReferenceFields.differences(it, shared) }
    val evidenceDifferences get() = expected?.let { ReferenceFields.differences(it, evidence.outcome) }
}
data class LabKnowledgeImpact(val improved: List<String>, val regressed: List<String>, val changed: Int, val labeled: Int,
    val draft: EvidenceKnowledge, val bankId: String, val testedBanks: Int, val examples: List<String>)

/** All reads/writes target the lab database. The baseline is never used as a reference answer. */
class LabEvidenceWorkflow(private val db: LabDatabase) {
    private val dao = db.dao()
    private val json = LabJson.format
    suspend fun knowledge(): EvidenceKnowledge = db.withTransaction {
        dao.evidenceKnowledge()?.let { stored ->
            val previous = json.decodeFromString<EvidenceKnowledge>(stored.knowledgeJson)
            val upgraded = EvidenceDefaults.upgrade(previous)
            if (upgraded != previous) dao.evidenceKnowledge(LabEvidenceKnowledgeRow(upgraded.version, json.encodeToString(upgraded), System.currentTimeMillis()))
            return@withTransaction upgraded
        }
        EvidenceDefaults.create().also { dao.evidenceKnowledge(LabEvidenceKnowledgeRow(it.version, json.encodeToString(it), System.currentTimeMillis())) }
    }
    suspend fun save(draft: EvidenceKnowledge): EvidenceKnowledge = db.withTransaction {
        draft.validate()
        require(draft.version == knowledge().version) { "تغيرت المعرفة؛ أعد فتح المسودة" }
        draft.copy(version = draft.version + 1).also {
            dao.evidenceKnowledge(LabEvidenceKnowledgeRow(it.version, json.encodeToString(it), System.currentTimeMillis()))
        }
    }
    suspend fun comparisons(bankId: String) = dao.comparisons(bankId).map { LabComparisonView(it.id, it.fromDate, it.toDate) }
    suspend fun results(id: String) = dao.comparisonResults(id).map {
        LabComparisonItem(it.sampleId, it.body, it.expectedJson?.let { value -> json.decodeFromString<ExpectedResult>(value) },
            json.decodeFromString<LabOutcome>(it.sharedJson), json.decodeFromString<EvidenceAnalysis>(it.evidenceJson),
            it.sharedNanos / 1_000_000.0, it.evidenceNanos / 1_000_000.0)
    }
    suspend fun run(bankId: String, from: String, to: String): String {
        val (snapshot, samples) = db.withTransaction {
            val packs = dao.banks().map { json.decodeFromString<RulePack>(it.packJson) }
            val bank = packs.single { it.bank.id == bankId }.bank
            val window = LabDateWindow.of(from, to, bank.zoneId)
            val library = dao.sharedRules()?.let { json.decodeFromString<SharedRuleLibrary>(it.libraryJson) } ?: SharedRuleDefaults.create()
            LabEvidenceSnapshot(packs.map { it.bank }, library, knowledge()) to dao.samples(bankId).filter { window.contains(it.receivedAt) }
        }
        require(samples.isNotEmpty() && samples.size <= 1000) { "اختر نطاقًا يحتوي من 1 إلى 1000 رسالة محفوظة" }
        val id = UUID.randomUUID().toString()
        val rows = withContext(Dispatchers.Default) {
            val shared = SharedLabEngine(); val evidence = EvidenceLabEngine()
            samples.mapIndexed { index, sample ->
                coroutineContext.ensureActive()
                val received = Instant.ofEpochMilli(sample.receivedAt)
                var sharedResult: LabOutcome
                var evidenceResult: EvidenceAnalysis
                var sharedNanos: Long; var evidenceNanos: Long
                fun sharedRun(): Pair<LabOutcome, Long> {
                    val start = System.nanoTime()
                    val value = shared.parse(snapshot.banks, snapshot.library, sample.sender, sample.body, sample.id, received, bankId)
                    return value to (System.nanoTime() - start)
                }
                fun evidenceRun(): Pair<EvidenceAnalysis, Long> {
                    val start = System.nanoTime()
                    val value = evidence.parse(snapshot.banks, snapshot.knowledge, sample.sender, sample.body, sample.id, received, bankId)
                    return value to (System.nanoTime() - start)
                }
                if (index % 2 == 0) { val a = sharedRun(); val b = evidenceRun(); sharedResult = a.first; sharedNanos = a.second; evidenceResult = b.first; evidenceNanos = b.second }
                else { val b = evidenceRun(); val a = sharedRun(); sharedResult = a.first; sharedNanos = a.second; evidenceResult = b.first; evidenceNanos = b.second }
                LabComparisonResultRow(id, sample.id, sample.body, sample.expectedJson, json.encodeToString(sharedResult),
                    json.encodeToString(evidenceResult), sharedNanos, evidenceNanos)
            }
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        db.withTransaction {
            dao.comparison(LabComparisonRow(id, bankId, System.currentTimeMillis(), from, to, json.encodeToString(snapshot)))
            dao.comparisonResults(rows)
        }
        return id
    }
    suspend fun preview(bankId: String, draft: EvidenceKnowledge): LabKnowledgeImpact {
        draft.validate()
        val (banks, samples, current) = db.withTransaction {
            val profiles = dao.banks().map { json.decodeFromString<RulePack>(it.packJson).bank }
            Triple(profiles, profiles.flatMap { dao.samples(it.id) }, knowledge())
        }
        require(banks.any { it.id == bankId } && samples.isNotEmpty() && samples.size <= 1000) { "معاينة المعرفة العامة تحتاج من 1 إلى 1000 رسالة عبر جميع بنوك المختبر" }
        require(draft.version == current.version) { "تغيرت المعرفة؛ أعد فتح المسودة" }
        return withContext(Dispatchers.Default) {
            val engine = EvidenceLabEngine(); val improved = mutableListOf<String>(); val regressed = mutableListOf<String>(); var changed = 0
            samples.forEach { sample ->
                coroutineContext.ensureActive()
                fun parse(k: EvidenceKnowledge) = engine.parse(banks, k, sample.sender, sample.body, sample.id, Instant.ofEpochMilli(sample.receivedAt), sample.bankId).outcome
                val before = parse(current); val after = parse(draft)
                if (before.facts != after.facts || before.status != after.status) changed++
                sample.expectedJson?.let {
                    val expected = json.decodeFromString<ExpectedResult>(it)
                    val a = ReferenceFields.differences(expected, before).toSet(); val b = ReferenceFields.differences(expected, after).toSet()
                    if ((a - b).isNotEmpty()) improved += sample.id
                    if ((b - a).isNotEmpty()) regressed += sample.id
                }
            }
            LabKnowledgeImpact(improved, regressed, changed, samples.count { it.expectedJson != null }, draft, bankId,
                samples.map { it.bankId }.distinct().size, samples.filter { it.id in improved || it.id in regressed }.take(12).map { it.body })
        }
    }
}
