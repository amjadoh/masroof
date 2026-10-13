package com.baraa.masroof.lab.application

import android.content.Context
import androidx.room.withTransaction
import com.baraa.masroof.lab.data.*
import com.baraa.masroof.lab.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import java.time.*
import java.util.UUID
import java.security.MessageDigest

data class LabSampleView(val id: String, val sender: String, val body: String, val receivedAt: Long, val annotated: Boolean)
data class LabRunView(val id: String, val version: Int, val fromDate: String, val toDate: String, val sampleCount: Int, val shared: Boolean = false)
data class LabRunResultView(val runId: String, val sampleId: String, val body: String, val outcome: LabOutcome,
                            val baseline: LabOutcome?, val differences: List<String>?, val expected: ExpectedResult? = null)
data class LabResultSet(val rows: List<LabRunResultView> = emptyList(), val matched: Int = 0, val labeled: Int = 0, val unlabeled: Int = 0)
data class LabSnapshot(val banks: List<RulePack>, val samples: List<LabSampleView>, val runs: List<LabRunView>,
                       val results: LabResultSet, val library: SharedRuleLibrary)
data class LabSmsImportResult(val imported: Int, val duplicates: Int, val skipped: Int)

class LabWorkflow(private val db: LabDatabase, private val inbox: LabInboxSource? = null) {
    companion object {
        fun open(context: Context) = LabWorkflow(LabDatabase.open(context), AndroidLabInboxSource(context.applicationContext.contentResolver))
    }
    private val dao = db.dao()
    private val json = LabJson.format
    private val engine = SharedLabEngine()

    suspend fun sharedLibrary(): SharedRuleLibrary = db.withTransaction {
        dao.sharedRules()?.let { return@withTransaction json.decodeFromString<SharedRuleLibrary>(it.libraryJson) }
        SharedRuleDefaults.create().also { dao.sharedRules(LabSharedRuleRow(it.version, json.encodeToString(it), System.currentTimeMillis())) }
    }

    suspend fun saveLibrary(draft: SharedRuleLibrary): SharedRuleLibrary = db.withTransaction {
        val current = sharedLibrary()
        require(draft.version == current.version) { "تغيرت المكتبة؛ أعد فتح القواعد قبل الحفظ" }
        draft.copy(version = current.version + 1).also {
            it.validate()
            dao.sharedRules(LabSharedRuleRow(it.version, json.encodeToString(it), System.currentTimeMillis()))
        }
    }

    suspend fun annotate(bankId: String, sampleId: String, expected: ExpectedResult) {
        validateExpected(expected)
        require(dao.annotate(bankId, sampleId, json.encodeToString(expected)) == 1) { "الرسالة غير موجودة" }
    }

    suspend fun load(bankId: String?): LabSnapshot {
        val banks = dao.banks().map { json.decodeFromString<RulePack>(it.packJson) }
        val selected = bankId ?: banks.firstOrNull()?.bank?.id
        val runs = selected?.let { dao.runs(it) }.orEmpty()
        return LabSnapshot(banks, selected?.let { dao.samples(it) }.orEmpty().map {
            LabSampleView(it.id, it.sender, it.body, it.receivedAt, it.expectedJson != null)
        }, runs.map { LabRunView(it.id, it.version, it.fromDate, it.toDate, it.sampleCount,
            "library" in json.parseToJsonElement(it.packJson).jsonObject) },
            runs.firstOrNull()?.let { viewResults(it.id) } ?: LabResultSet(), sharedLibrary())
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
        listOf(expected.facts.card, expected.facts.source, expected.facts.destination, expected.facts.ibanSuffix).filterNotNull().forEach {
            require(it.matches(Regex("[0-9]{4}"))) { "معرف البطاقة أو الحساب يحتاج أربعة أرقام إنجليزية" }
        }
        listOf(expected.facts.amount, expected.facts.balance, expected.facts.dueAmount).filterNotNull().forEach {
            require(it.length <= 20 && it.toBigDecimalOrNull()?.let { n -> n.signum() >= 0 && n.scale() <= 2 } == true) { "المبلغ غير صالح؛ استخدم أرقامًا إنجليزية ونقطة عشرية" }
        }
        expected.facts.occurredAtLocal?.let { LocalDateTime.parse(it) }
        require((expected.facts.merchant?.length ?: 0) <= 300) { "اسم التاجر طويل جدًا" }
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
        engine.parse(packs.map { it.bank }, sharedLibrary(), sender, body, "preview", Instant.now(), bankId)
    }

    suspend fun previewSaved(bankId: String, sampleId: String): LabOutcome = withContext(Dispatchers.Default) {
        val sample = requireNotNull(dao.savedSample(bankId, sampleId)) { "الرسالة غير موجودة في البنك المحدد" }
        val packs = dao.banks().map { json.decodeFromString<RulePack>(it.packJson) }
        engine.parse(packs.map { it.bank }, sharedLibrary(), sample.sender, sample.body, sample.id, Instant.ofEpochMilli(sample.receivedAt), bankId)
    }

    suspend fun run(bankId: String, from: String, to: String): String {
        // Snapshot inputs before computation. Completed runs and annotations are immutable.
        val captured = db.withTransaction {
            val packs = dao.banks().map { json.decodeFromString<RulePack>(it.packJson) }
            val pack = packs.single { it.bank.id == bankId }
            val window = LabDateWindow.of(from, to, pack.bank.zoneId)
            Triple(packs, pack, dao.samples(bankId).filter { window.contains(it.receivedAt) }) to sharedLibrary()
        }
        val (input, library) = captured
        require(input.third.isNotEmpty()) { "لا توجد عينات في المدى المحدد" }
        require(input.third.size <= 1_000) { "حد الدفعة 1000 عينة" }
        val id = UUID.randomUUID().toString()
        val rows = withContext(Dispatchers.Default) {
            input.third.map { sample ->
                coroutineContext.ensureActive()
                val instant = Instant.ofEpochMilli(sample.receivedAt)
                val outcome = engine.parse(input.first.map { it.bank }, library, sample.sender, sample.body, sample.id, instant, bankId)
                val expected = sample.expectedJson?.let { json.decodeFromString<ExpectedResult>(it) }
                val baseline = if (bankId == "BANK_ALJAZIRA") LabResultProjection.baseline(sample.sender, sample.body, sample.id, instant) else null
                LabRunResultRow(id, sample.id, sample.sender, sample.body, sample.expectedJson, json.encodeToString(outcome),
                    baseline?.let { json.encodeToString(it) }, expected?.let { json.encodeToString(LabComparison.differences(it, outcome)) })
            }
        }
        db.withTransaction {
            dao.run(LabRunRow(id, bankId, library.version, System.currentTimeMillis(), from, to,
                json.encodeToString(LabRunSnapshot(input.second, library, SharedLabEngine.VERSION)), rows.size))
            dao.results(rows)
        }
        return id
    }

    suspend fun results(runId: String) = dao.results(runId)
    suspend fun importPhoneSms(bankId: String, from: String, to: String): LabSmsImportResult {
        val selected = dao.banks().singleOrNull { it.id == bankId } ?: error("اختر بنكًا محفوظًا")
        val pack = json.decodeFromString<RulePack>(selected.packJson)
        RulePackValidation.check(pack)
        val window = LabDateWindow.of(from, to, pack.bank.zoneId)
        val source = requireNotNull(inbox) { "قراءة رسائل الجوال غير متاحة" }
        val read = try { source.read(pack.bank.senders.toSet(), window.fromInclusive, window.toExclusive) }
            catch (e: SecurityException) { throw IllegalStateException("لم يُسمح بقراءة SMS؛ امنح المختبر إذن قراءة الرسائل", e) }
        val allowed = pack.bank.senders.map(RulePackValidation::senderKey).toSet()
        // Recheck the reader contract before writing. Nothing is auto-annotated or posted.
        val valid = read.messages.filter { window.contains(it.receivedAt) && RulePackValidation.senderKey(it.sender) in allowed &&
            it.providerId.isNotBlank() && it.body.isNotBlank() && it.body.length <= 16_000 }
        require(valid.size <= AndroidLabInboxSource.MAX_MESSAGES) { "النطاق يتجاوز 1000 رسالة؛ اختر فترة أقصر" }
        val rows = withContext(Dispatchers.Default) { valid.map { message ->
            coroutineContext.ensureActive()
            val parts = listOf(bankId, message.providerId, message.receivedAt.toString(), RulePackValidation.senderKey(message.sender), message.body)
            val canonical = parts.joinToString("") { "${it.length}:$it" }
            val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            LabSampleRow("sms-$hash", bankId, message.sender, message.body, message.receivedAt, null)
        } }
        val inserted = db.withTransaction {
            // A configuration change during permission/provider access must not use a stale bank definition.
            require(dao.banks().singleOrNull { it.id == bankId }?.packJson == selected.packJson) { "تغير تعريف البنك؛ أعد الاستيراد" }
            dao.importedSamples(rows).count { it != -1L }
        }
        return LabSmsImportResult(inserted, rows.size - inserted, read.skipped + read.messages.size - valid.size)
    }
    suspend fun versions(bankId: String) = dao.versions(bankId)
    suspend fun viewResults(runId: String): LabResultSet {
        val rows = dao.results(runId).map { row ->
            LabRunResultView(row.runId, row.sampleId, row.body, json.decodeFromString<LabOutcome>(row.outcomeJson),
                row.baselineJson?.let { json.decodeFromString<LabOutcome>(it) },
                row.differencesJson?.let { json.decodeFromString<List<String>>(it) },
                row.expectedJson?.let { json.decodeFromString<ExpectedResult>(it) })
        }
        val labeled = rows.filter { it.differences != null }
        return LabResultSet(rows, labeled.count { it.differences!!.isEmpty() }, labeled.size, rows.size - labeled.size)
    }
    suspend fun importPack(text: String): RulePack {
        require(text.length <= 256_000) { "الملف كبير جدًا" }
        return savePack(json.decodeFromString<RulePack>(text))
    }
    suspend fun reset() = db.withTransaction {
        dao.clearResults(); dao.clearRuns(); dao.clearSamples(); dao.clearVersions(); dao.clearBanks(); dao.clearSharedRules()
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
