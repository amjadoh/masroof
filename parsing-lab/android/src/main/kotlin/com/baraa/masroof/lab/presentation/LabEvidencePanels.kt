package com.baraa.masroof.lab.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.baraa.masroof.lab.application.LabComparisonItem
import com.baraa.masroof.lab.engine.*
import com.baraa.masroof.lab.engine.evidence.*
import com.baraa.masroof.lab.ui.copy.presentation.theme.MasroofSpacing
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal fun samplesAvailable(state: LabUiState) = state.snapshot?.samples?.isNotEmpty() == true
internal fun executionLabel(value: String) = when(value) { "EXECUTED" -> "منفذة بحسب النص"; "REJECTED" -> "مرفوضة"; "PENDING" -> "قيد التنفيذ"; "REQUESTED" -> "طلب"; else -> "غير مثبتة" }
internal fun fieldLabel(value: String) = when(value) {
    "family" -> "النوع"; "direction" -> "الاتجاه"; "amount" -> "مبلغ العملية"; "currency" -> "العملة"
    "card" -> "آخر البطاقة"; "source" -> "آخر حساب المصدر"; "destination" -> "آخر حساب الوجهة"
    "merchant" -> "التاجر"; "balance" -> "الرصيد"; "dueAmount" -> "المستحق"; "ibanSuffix" -> "آخر الآيبان"
    "occurredAtLocal" -> "تاريخ العملية"; "dueDate" -> "تاريخ الاستحقاق"; "executionState" -> "حالة التنفيذ"
    "cardKind" -> "نوع البطاقة"; "network" -> "الشبكة"; "feeAmount" -> "الرسوم"; "exchangeRate" -> "سعر الصرف"
    "otherAmount" -> "مبلغ آخر مستقل عن الحركة"
    "dateOnly" -> "تاريخ دون وقت"; "invalidDate" -> "تاريخ غير صالح"
    "status" -> "حالة التحليل"; else -> value
}
private fun conceptLabel(value: String): String {
    val suffix = value.substringAfter('.')
    return when(value.substringBefore('.')) {
        "field" -> "حقل: ${fieldLabel(suffix)}"; "family" -> "نوع: ${familyLabel(suffix)}"
        "state" -> "تنفيذ: ${executionLabel(suffix)}"; "card" -> if (suffix == "CREDIT") "بطاقة ائتمانية" else "بطاقة خصم"
        "network" -> "شبكة: $suffix"; else -> value
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EvidenceComparisonPanel(pack: RulePack, state: LabUiState, vm: LabViewModel) {
    var from by rememberSaveable(pack.bank.id) { mutableStateOf(LocalDate.now(ZoneId.of(pack.bank.zoneId)).minusDays(30).toString()) }
    var to by rememberSaveable(pack.bank.id) { mutableStateOf(LocalDate.now(ZoneId.of(pack.bank.zoneId)).toString()) }
    Text("مقارنة المحرك الحالي بمحرك الأدلة", style = MaterialTheme.typography.titleLarge)
    LabCard {
        LabDateField("من تاريخ", from, !state.busy) { from = it }
        LabDateField("إلى تاريخ", to, !state.busy) { to = it }
        Text("تستخدم الرسائل المحفوظة في المختبر. لا تعيد قراءة صندوق الرسائل.")
        Button(onClick = { vm.compare(from, to) }, enabled = !state.busy && from <= to && samplesAvailable(state)) { Text("تشغيل المقارنة وحفظها") }
    }
    state.comparisons.forEach { run -> FilterChip(state.selectedComparison == run.id, { vm.selectComparison(run.id) },
        label = { Text("${run.from} إلى ${run.to} · ${run.id.take(6)}") }, enabled = !state.busy) }
    val rows = state.comparisonRows
    if (rows.isEmpty()) return
    val labeled = rows.filter { it.expected != null }
    LabCard {
        Text("${rows.size} رسالة · ${labeled.size} لها إجابة · ${rows.size - labeled.size} بلا تقييم")
        Text("مطابقة الحقول المراجعة: الحالي ${labeled.count { it.sharedDifferences!!.isEmpty() }} / ${labeled.size} · الأدلة ${labeled.count { it.evidenceDifferences!!.isEmpty() }} / ${labeled.size}")
        Text("نتائج محسومة: الحالي ${rows.count { it.shared.status == "SUCCESS" }} · الأدلة ${rows.count { it.evidence.outcome.status == "SUCCESS" }}")
        Text("هذه مطابقة للحقول المراجعة فقط؛ الحقول غير المراجعة لا تثبت صحة العملية كاملة.", style = MaterialTheme.typography.bodySmall)
        Text("الزمن لكل رسالة محفوظ للتشخيص؛ يشمل أول تشغيل ولا يمثل اختبار سرعة مضبوطًا.", style = MaterialTheme.typography.bodySmall)
    }
    var filter by rememberSaveable { mutableStateOf("all") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
        listOf("all" to "الكل", "family" to "اختلاف النوع", "amount" to "اختلاف المبلغ", "direction" to "اختلاف الاتجاه", "review" to "للمراجعة", "unlabeled" to "بلا إجابة").forEach { (id, title) ->
            FilterChip(filter == id, { filter = id }, label = { Text(title) })
        }
    }
    val shown = rows.filter { row -> when(filter) {
        "unlabeled" -> row.expected == null; "review" -> row.evidence.outcome.status == "REVIEW_REQUIRED"
        "all" -> true
        else -> ReferenceFields.values(row.shared.facts)[filter] != ReferenceFields.values(row.evidence.outcome.facts)[filter]
    } }
    var visible by remember(state.selectedComparison, filter) { mutableIntStateOf(15) }
    shown.take(visible).forEach { row -> ComparisonItem(row, state.busy) { vm.annotate(row.sampleId, it) } }
    if (visible < shown.size) TextButton(onClick = { visible += 15 }) { Text("عرض المزيد") }
}

@Composable
private fun ComparisonItem(row: LabComparisonItem, busy: Boolean, annotate: (ExpectedResult) -> Unit) {
    var details by rememberSaveable(row.sampleId, row.body) { mutableStateOf(false) }
    LabCard {
        val highlight = MaterialTheme.colorScheme.secondaryContainer
        val marked: AnnotatedString = remember(row.body, row.evidence, highlight) { buildAnnotatedString {
            append(row.body)
            row.evidence.outcome.evidence.forEach { e -> if (e.start >= 0 && e.endExclusive <= length && e.start < e.endExclusive)
                addStyle(SpanStyle(background = highlight), e.start, e.endExclusive) }
        } }
        Text(marked)
        Text("الحالي: ${statusLabel(row.shared.status)} · الأدلة: ${statusLabel(row.evidence.outcome.status)}")
        TextButton(onClick = { details = !details }) { Text(if (details) "إخفاء التفاصيل" else "الحقول وأسباب القرار") }
        if (details) {
            Text("المحرك الحالي", style = MaterialTheme.typography.titleSmall); FactsText(row.shared.facts)
            Text("محرك الأدلة", style = MaterialTheme.typography.titleSmall); FactsText(row.evidence.outcome.facts)
            Text("الزمن: الحالي ${"%.2f".format(row.sharedMs)} مللي ثانية · الأدلة ${"%.2f".format(row.evidenceMs)}")
            row.evidence.relations.forEach { Text("${fieldLabel(it.role)}: ${it.value} ← ${row.body.substring(it.start, it.endExclusive)}", style = MaterialTheme.typography.bodySmall) }
            row.evidence.hypotheses.forEach { Text("تفسير: ${familyLabel(it.family)} · ${it.blockers.joinToString().ifBlank { "لا مانع أولي" }}", style = MaterialTheme.typography.bodySmall) }
            row.evidence.outcome.trace.filterNot { it.startsWith("receivedAt:") }.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("اختلافات الحالي: ${row.sharedDifferences?.joinToString { fieldLabel(it) } ?: "غير مقيم"}")
            Text("اختلافات الأدلة: ${row.evidenceDifferences?.joinToString { fieldLabel(it) } ?: "غير مقيم"}")
            EvidenceReferenceEditor(row, busy, annotate)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EvidenceReferenceEditor(row: LabComparisonItem, busy: Boolean, save: (ExpectedResult) -> Unit) {
    var editing by remember(row.sampleId) { mutableStateOf(false) }
    TextButton(onClick = { editing = !editing }, enabled = !busy) { Text(if (editing) "إلغاء التصحيح" else "مراجعة الإجابة الصحيحة") }
    if (!editing) return
    val values = remember(row.sampleId) { mutableStateMapOf<String, String>().also { map -> ReferenceFields.values(row.expected?.facts ?: LabFacts()).forEach { (k,v) -> map[k] = v.orEmpty() } } }
    val absent = remember(row.sampleId) { mutableStateMapOf<String, Boolean>().also { map -> row.expected?.absentFields?.forEach { map[it] = true } } }
    var status by remember(row.sampleId) { mutableStateOf(row.expected?.status ?: "REVIEW_REQUIRED") }
    Text("أدخل ما راجعته من النص. القيمة الفارغة غير مراجعة؛ اختر «غير موجود» فقط بعد التأكد. لا تملأ الإجابة من أحد المحركين تلقائيًا.")
    FlowRow { listOf("SUCCESS", "REVIEW_REQUIRED", "NON_FINANCIAL").forEach { s -> FilterChip(status == s, { status = s }, label = { Text(statusLabel(s)) }, enabled = !busy) } }
    ReferenceFields.values(LabFacts()).keys.forEach { field ->
        val options = when(field) {
            "family" -> (LabFamilies.editable + "UNKNOWN").sorted()
            "direction" -> listOf("INCOMING", "OUTGOING")
            "executionState" -> listOf("EXECUTED", "REJECTED", "PENDING", "REQUESTED", "UNPROVEN")
            "cardKind" -> listOf("CREDIT", "DEBIT"); "network" -> listOf("VISA", "MADA", "MASTERCARD")
            "currency" -> listOf("SAR", "USD", "EUR", "GBP"); else -> emptyList()
        }
        Text(fieldLabel(field), style = MaterialTheme.typography.labelLarge)
        if (options.isNotEmpty()) FlowRow {
            options.forEach { value -> FilterChip(values[field] == value, { values[field] = value; absent[field] = false },
                label = { Text(when(field) { "family" -> familyLabel(value); "executionState" -> executionLabel(value); else -> value }) }, enabled = !busy) }
        } else LabField(fieldLabel(field), values[field].orEmpty(), { values[field] = it; absent[field] = false }, !busy)
        Row {
            FilterChip(absent[field] == true, { absent[field] = true; values[field] = "" }, label = { Text("غير موجود") }, enabled = !busy)
            TextButton(onClick = { absent[field] = false; values[field] = "" }, enabled = !busy) { Text("غير مراجع") }
        }
    }
    Button(onClick = {
        fun v(key: String) = values[key]?.trim()?.takeIf { it.isNotEmpty() }
        save(ExpectedResult(status, LabFacts(v("family"), v("direction"), v("amount"), v("currency"), v("card"), v("source"), v("destination"),
            v("merchant"), v("balance"), v("dueAmount"), v("ibanSuffix"), v("occurredAtLocal"), v("executionState"), v("cardKind"), v("network"), v("feeAmount"), v("exchangeRate")),
            absent.filterValues { it }.keys.toSet()))
        editing = false
    }, enabled = !busy) { Text("حفظ للاختبارات القادمة") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EvidenceKnowledgeEditor(state: LabUiState, vm: LabViewModel) {
    val knowledge = state.knowledge ?: return
    var selected by rememberSaveable { mutableStateOf(knowledge.rules.firstOrNull()?.id) }
    val current = knowledge.rules.firstOrNull { it.id == selected }
    var concept by rememberSaveable(knowledge.version, selected) { mutableStateOf(current?.concept ?: "family.PURCHASE") }
    var phrases by rememberSaveable(knowledge.version, selected) { mutableStateOf(current?.phrases?.joinToString("\n").orEmpty()) }
    var excludes by rememberSaveable(knowledge.version, selected) { mutableStateOf(current?.excludes?.joinToString("\n").orEmpty()) }
    var nextLine by rememberSaveable(knowledge.version, selected) { mutableStateOf(current?.nextLine ?: false) }
    var distance by rememberSaveable(knowledge.version, selected) { mutableStateOf((current?.maxDistance ?: 80).toString()) }
    var required by rememberSaveable(knowledge.version, selected) { mutableStateOf(current?.requiredRoles?.toList().orEmpty()) }
    val id = remember(knowledge.version, selected) { selected ?: "custom.${UUID.randomUUID()}" }
    fun lines(s: String) = s.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val draft = knowledge.copy(rules = knowledge.rules.filterNot { it.id == id } + ConceptRule(id, concept, lines(phrases), lines(excludes), required.toSet(), distance.toIntOrNull() ?: 0, nextLine))
    Text("معرفة الأدلة · إصدار ${knowledge.version}", style = MaterialTheme.typography.titleLarge)
    Text("مفاهيم مشتركة لجميع البنوك. عاين أثر المسودة على رسائل جميع بنوك المختبر قبل حفظها؛ التصحيح وحده لا يغير المعرفة.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
        state.snapshot?.banks.orEmpty().forEach { pack -> FilterChip(pack.bank.id == state.bankId, { vm.selectBank(pack.bank.id) }, label = { Text(pack.bank.name) }, enabled = !state.busy) }
    }
    FlowRow { knowledge.rules.forEach { rule -> FilterChip(selected == rule.id, { selected = rule.id }, label = { Text(rule.phrases.first()) }, enabled = !state.busy) }
        FilterChip(selected == null, { selected = null }, label = { Text("قاعدة جديدة") }, enabled = !state.busy) }
    LabCard {
        Text("المفهوم")
        FlowRow { EvidenceDefaults.concepts.sorted().forEach { value -> FilterChip(concept == value, { concept = value }, label = { Text(conceptLabel(value)) }, enabled = !state.busy) } }
        LabField("عبارات التعرف — كل عبارة في سطر", phrases, { phrases = it }, !state.busy, true)
        LabField("عبارات تمنع المطابقة", excludes, { excludes = it }, !state.busy, true)
        if (concept.startsWith("field.")) {
            Row { Checkbox(nextLine, { nextLine = it }, enabled = !state.busy);Text("السماح بالقيمة في السطر التالي") }
            LabField("أقصى مسافة للربط — من 1 إلى 160 حرفًا", distance, { distance = it }, !state.busy)
        }
        Text("حقول يجب وجودها لتأييد التفسير")
        FlowRow { EvidenceDefaults.roles.sorted().forEach { role -> FilterChip(role in required,
            { required = if (role in required) required - role else required + role }, label = { Text(fieldLabel(role)) }, enabled = !state.busy) } }
        Button(onClick = { vm.previewKnowledge(draft) }, enabled = !state.busy && phrases.isNotBlank() && samplesAvailable(state)) { Text("معاينة أثر التعديل") }
        state.impact?.takeIf { it.draft == draft && it.bankId == state.bankId }?.let { impact ->
            Text("${impact.changed} تغيرت · ${impact.improved.size} تحسنت في حقول · ${impact.regressed.size} تراجعت في حقول · ${impact.labeled} لها إجابة")
            Text("المعاينة شملت ${impact.testedBanks} بنوك محفوظة؛ الرسائل التي بلا إجابات لا تدخل قياس التحسن.")
            impact.examples.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { vm.saveKnowledge(draft) }, enabled = !state.busy) { Text("حفظ إصدار جديد للمختبر") }
        }
    }
}
