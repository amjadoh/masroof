package com.baraa.masroof.lab.application

import android.content.Context
import androidx.room.withTransaction
import com.baraa.masroof.lab.data.*
import com.baraa.masroof.lab.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.time.*
import java.util.UUID

data class LabSampleView(val id: String, val sender: String, val body: String, val receivedAt: Long, val annotated: Boolean)
data class LabRunView(val id: String, val version: Int, val fromDate: String, val toDate: String, val sampleCount: Int)
data class LabRunResultView(val runId: String, val sampleId: String, val body: String, val outcome: LabOutcome,
                            val baseline: LabOutcome?, val differences: List<String>?)
data class LabResultSet(val rows: List<LabRunResultView> = emptyList(), val matched: Int = 0, val labeled: Int = 0, val unlabeled: Int = 0)
data class LabSnapshot(val banks: List<RulePack>, val samples: List<LabSampleView>, val runs: List<LabRunView>,
                       val results: LabResultSet)

class LabWorkflow(private val db: LabDatabase) {
    companion object {
        fun open(context: Context) = LabWorkflow(LabDatabase.open(context))
    }
    private val dao = db.dao()
    private val json = LabJson.format
    private val engine = DynamicLabEngine()

    suspend fun load(bankId: String?): LabSnapshot {
        val banks = dao.banks().map { json.decodeFromString<RulePack>(it.packJson) }
        val selected = bankId ?: banks.firstOrNull()?.bank?.id
        val runs = selected?.let { dao.runs(it) }.orEmpty()
        return LabSnapshot(banks, selected?.let { dao.samples(it) }.orEmpty().map {
            LabSampleView(it.id, it.sender, it.body, it.receivedAt, it.expectedJson != null)
        }, runs.map { LabRunView(it.id, it.version, it.fromDate, it.toDate, it.sampleCount) },
            runs.firstOrNull()?.let { viewResults(it.id) } ?: LabResultSet())
    }

    suspend fun savePack(draft: RulePack): RulePack = db.withTransaction {
        val banks = dao.banks()
        val pack = draft.copy(version = (banks.firstOrNull { it.id == draft.bank.id }?.version ?: 0) + 1)
        RulePackValidation.check(pack)
        val otherSenders = banks.filter { it.id != pack.bank.id }.flatMap {
            json.decodeFromString<RulePack>(it.packJson).bank.senders.map(RulePackValidation::senderKey)
        }.toSet()
        require(pack.bank.senders.none { RulePackValidation.senderKey(it) in otherSenders }) { "هذا المرسل مرتبط ببنك آخر؛ تعارض التوجيه" }
        val text = json.encodeToString(pack)
        dao.version(LabRuleVersionRow(pack.bank.id, pack.version, text, System.currentTimeMillis()))
        dao.bank(LabBankRow(pack.bank.id, pack.bank.name, pack.version, text))
        pack
    }

    suspend fun addSample(bankId: String, sender: String, body: String, date: String, expectedText: String?) {
        require(body.length in 1..16_000 && sender.isNotBlank()) { "أدخل رسالة ومرسلًا صالحين" }
        val pack = dao.banks().firstOrNull { it.id == bankId }?.let { json.decodeFromString<RulePack>(it.packJson) }
            ?: error("البنك غير موجود")
        val expected = expectedText?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<ExpectedResult>(it) }
        expected?.let { validateExpected(it) }
        val instant = LocalDate.parse(date).atTime(12, 0).atZone(ZoneId.of(pack.bank.zoneId)).toInstant()
        dao.sample(LabSampleRow(UUID.randomUUID().toString(), bankId, sender.trim(), body, instant.toEpochMilli(),
            expected?.let { json.encodeToString(it) }))
    }

    private fun validateExpected(expected: ExpectedResult) {
        require(expected.status in setOf("SUCCESS", "REVIEW_REQUIRED", "NON_FINANCIAL", "UNSUPPORTED", "INVALID", "PARTIAL")) { "حالة متوقعة غير صالحة" }
        require(expected.facts.family == null || expected.facts.family in LabFamilies.editable + setOf("OTP", "UNKNOWN")) { "تصنيف متوقع غير صالح" }
        if (expected.status == "SUCCESS") {
            require(expected.facts.family in LabFamilies.financial && expected.facts.direction in setOf("INCOMING", "OUTGOING") &&
                expected.facts.currency in setOf("SAR", "USD", "EUR", "GBP") &&
                expected.facts.amount?.toBigDecimalOrNull()?.let { it.signum() > 0 && it.scale() <= 2 } == true) { "الإجابة المالية تحتاج مبلغًا وعملة واتجاهًا" }
        }
    }

    suspend fun preview(bankId: String, sender: String, body: String): LabOutcome = withContext(Dispatchers.Default) {
        val packs = dao.banks().map { json.decodeFromString<RulePack>(it.packJson) }
        require(packs.any { it.bank.id == bankId }) { "اختر بنكًا" }
        engine.parse(packs, sender, body, "preview", Instant.now(), bankId)
    }

    suspend fun run(bankId: String, from: String, to: String): String {
        // Snapshot inputs before computation. Completed runs and annotations are immutable.
        val input = db.withTransaction {
            val packs = dao.banks().map { json.decodeFromString<RulePack>(it.packJson) }
            val pack = packs.single { it.bank.id == bankId }
            val zone = ZoneId.of(pack.bank.zoneId)
            val start = LocalDate.parse(from); val end = LocalDate.parse(to)
            require(!end.isBefore(start)) { "تاريخ النهاية يسبق البداية" }
            val lower = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val upper = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Triple(packs, pack, dao.samples(bankId).filter { it.receivedAt >= lower && it.receivedAt < upper })
        }
        require(input.third.isNotEmpty()) { "لا توجد عينات في المدى المحدد" }
        require(input.third.size <= 1_000) { "حد الدفعة 1000 عينة" }
        val id = UUID.randomUUID().toString()
        val rows = withContext(Dispatchers.Default) {
            input.third.map { sample ->
                coroutineContext.ensureActive()
                val instant = Instant.ofEpochMilli(sample.receivedAt)
                val outcome = engine.parse(input.first, sample.sender, sample.body, sample.id, instant, bankId)
                val expected = sample.expectedJson?.let { json.decodeFromString<ExpectedResult>(it) }
                val baseline = if (bankId == "BANK_ALJAZIRA") LabResultProjection.baseline(sample.sender, sample.body, sample.id, instant) else null
                LabRunResultRow(id, sample.id, sample.sender, sample.body, sample.expectedJson, json.encodeToString(outcome),
                    baseline?.let { json.encodeToString(it) }, expected?.let { json.encodeToString(LabComparison.differences(it, outcome)) })
            }
        }
        db.withTransaction {
            dao.run(LabRunRow(id, bankId, input.second.version, System.currentTimeMillis(), from, to,
                json.encodeToString(input.second), rows.size))
            dao.results(rows)
        }
        return id
    }

    suspend fun results(runId: String) = dao.results(runId)
    suspend fun versions(bankId: String) = dao.versions(bankId)
    suspend fun viewResults(runId: String): LabResultSet {
        val rows = dao.results(runId).map { row ->
            LabRunResultView(row.runId, row.sampleId, row.body, json.decodeFromString<LabOutcome>(row.outcomeJson),
                row.baselineJson?.let { json.decodeFromString<LabOutcome>(it) },
                row.differencesJson?.let { json.decodeFromString<List<String>>(it) })
        }
        val labeled = rows.filter { it.differences != null }
        return LabResultSet(rows, labeled.count { it.differences!!.isEmpty() }, labeled.size, rows.size - labeled.size)
    }
    suspend fun importPack(text: String): RulePack {
        require(text.length <= 256_000) { "الملف كبير جدًا" }
        return savePack(json.decodeFromString<RulePack>(text))
    }
    suspend fun reset() = db.withTransaction {
        dao.clearResults(); dao.clearRuns(); dao.clearSamples(); dao.clearVersions(); dao.clearBanks()
    }
    fun close() = db.close()

    suspend fun seedExamples() = db.withTransaction {
        if (dao.banks().isNotEmpty()) return@withTransaction
        val profile = BankProfile("BANK_ALJAZIRA", "بنك الجزيرة — تجربة", listOf("AlJazira", "JaziraBank"))
        val rules = listOf(
            RuleSpec("purchase.pos", "PURCHASE", listOf("شراء عبر نقاط البيع", "pos purchase"), cardLabels = listOf("بطاقة ائتمانية", "بطاقة")),
            RuleSpec("transfer.in", "TRANSFER_IN", listOf("حوالة واردة"), direction = "INCOMING", destinationLabels = listOf("إلى حساب")),
            RuleSpec("transfer.out", "TRANSFER_OUT", listOf("حوالة صادرة"), sourceLabels = listOf("من حساب")),
            RuleSpec("bill.payment", "BILL_PAYMENT", listOf("سداد فاتورة")),
            RuleSpec("card.payment", "CARD_PAYMENT", listOf("سداد بطاقة")),
        )
        val pack = RulePack(bank = profile, version = 1, rules = rules)
        RulePackValidation.check(pack)
        val text = json.encodeToString(pack)
        dao.bank(LabBankRow(profile.id, profile.name, 1, text)); dao.version(LabRuleVersionRow(profile.id, 1, text, System.currentTimeMillis()))
        val today = LocalDate.now(ZoneId.of(profile.zoneId)).atTime(12, 0).atZone(ZoneId.of(profile.zoneId)).toInstant().toEpochMilli()
        val examples = listOf(
            "شراء عبر نقاط البيع\nبطاقة: 1234\nبمبلغ: 178.02 SAR\nلدى: متجر المثال\nالرصيد المتاح: 4821.98 SAR" to ExpectedResult("SUCCESS", LabFacts("PURCHASE", "OUTGOING", "178.02", "SAR", card = "1234", merchant = "متجر المثال")),
            "رمز التحقق لعملية الشراء هو 9876. بمبلغ 178.02 SAR" to ExpectedResult("NON_FINANCIAL", LabFacts(family = "OTP")),
            "شراء عبر نقاط البيع\nبمبلغ 178.02 SAR\nبمبلغ 180.02 SAR" to ExpectedResult("REVIEW_REQUIRED", LabFacts("PURCHASE")),
            "تم تحديث تفاصيل منتجك البنكي" to ExpectedResult("REVIEW_REQUIRED", LabFacts("UNKNOWN")),
        )
        examples.forEach { (body, expected) -> dao.sample(LabSampleRow(UUID.randomUUID().toString(), profile.id, "AlJazira", body,
            today, json.encodeToString(expected))) }
    }
}
