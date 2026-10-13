package com.baraa.masroof.lab.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.baraa.masroof.lab.application.LabWorkflow
import com.baraa.masroof.lab.application.LabRunResultView
import com.baraa.masroof.lab.engine.*
import com.baraa.masroof.lab.ui.copy.presentation.theme.*
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** The only host-facing API. Receives no production services, repositories or database. */
@Composable
fun ParsingLabEntry(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var savedBankId by rememberSaveable { mutableStateOf<String?>(null) }
    val store = remember { ViewModelStore() }
    val vm = remember {
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LabViewModel(LabWorkflow.open(context), savedBankId) as T
        })[LabViewModel::class.java]
    }
    DisposableEffect(store) { onDispose { store.clear() } }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.bankId) { savedBankId = state.bankId }
    BackHandler { onBack() }
    MasroofTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            LabScreen(state, vm, onBack)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LabScreen(state: LabUiState, vm: LabViewModel, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("banks") }
    val pack = state.snapshot?.banks?.firstOrNull { it.bank.id == state.bankId }
    var confirmReset by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("مصروف · مختبر التحليل", style = MaterialTheme.typography.titleMedium)
            Text("بيانات تجريبية مستقلة", style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }) },
        bottomBar = { NavigationBar {
            listOf("banks" to "البنوك", "play" to "التجربة", "rules" to "القواعد", "results" to "النتائج").forEach { (id, name) ->
                NavigationBarItem(selected = tab == id, onClick = { tab = id },
                    icon = { Icon(when (id) { "banks" -> Icons.Default.AccountBalance; "play" -> Icons.Default.Science;
                        "rules" -> Icons.Default.Rule; else -> Icons.Default.FactCheck }, contentDescription = null) }, label = { Text(name) })
            }
        } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(MasroofSpacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (state.snapshot?.banks?.isNotEmpty() == true) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
                    state.snapshot.banks.forEach { bank -> FilterChip(selected = bank.bank.id == state.bankId,
                        onClick = { vm.selectBank(bank.bank.id) }, enabled = !state.busy,
                        label = { Text(bank.bank.name) }) }
                }
            }
            when (tab) {
                "banks" -> {
                    Text("البنوك التجريبية", style = MaterialTheme.typography.titleLarge)
                    if (pack != null) LabCard {
                        Text(pack.bank.name, style = MaterialTheme.typography.titleMedium)
                        Text("الإصدار v${pack.version} · ${pack.rules.size} قواعد · ${state.snapshot?.samples?.size ?: 0} عينات")
                        Text("مرسلون: ${pack.bank.senders.joinToString()}")
                        Text("معرفة المرسل تحدد البنك. فهم الرسائل يحتاج قواعد وعينات.", style = MaterialTheme.typography.bodySmall)
                    }
                    BankEditor(pack, state.busy, vm::savePack)
                    if (state.snapshot?.banks?.isEmpty() == true) {
                        OutlinedButton(onClick = vm::seed, enabled = !state.busy) { Text("إضافة الجزيرة وعينات توضيحية") }
                    }
                    PackTransfer(pack, state.busy, vm::importPack)
                    OutlinedButton(onClick = { confirmReset = true }, enabled = !state.busy) { Text("مسح بيانات المختبر") }
                }
                "play" -> if (pack == null) Text("أضف بنكًا أولًا") else SamplePanel(pack, state, vm)
                "rules" -> if (pack == null) Text("أضف بنكًا أولًا") else RuleEditor(pack, state.busy, vm::savePack)
                "results" -> if (pack == null) Text("أضف بنكًا أولًا") else ResultsPanel(pack, state, vm)
            }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("مسح المختبر؟") },
        text = { Text("سيحذف بنوك المختبر وقواعده وعيناته ونتائج تجاربه.") },
        confirmButton = { TextButton(onClick = { confirmReset = false;vm.reset() }) { Text("مسح") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("إلغاء") } })
}

/** Copied MasroofCard geometry and surface tokens, scoped to the lab theme. */
@Composable
private fun LabCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MasroofThemeExtras.extendedColors
    Card(Modifier.fillMaxWidth(), shape = MasroofCardShape,
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        border = BorderStroke(MasroofSpacing.borderWidth, colors.cardBorder)) {
        Column(Modifier.padding(MasroofSpacing.cardInnerPadding + MasroofSpacing.borderWidth),
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap), content = content)
    }
}

@Composable
private fun LabField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean = true, multi: Boolean = false) {
    OutlinedTextField(value, onValueChange = onChange, label = { Text(label) }, enabled = enabled,
        modifier = Modifier.fillMaxWidth(), singleLine = !multi, minLines = if (multi) 3 else 1)
}

@Composable
private fun BankEditor(pack: RulePack?, busy: Boolean, save: (RulePack) -> Unit) {
    var create by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(pack?.bank?.id, create) { mutableStateOf(if (create) "" else pack?.bank?.name.orEmpty()) }
    var senders by rememberSaveable(pack?.bank?.id, create) { mutableStateOf(if (create) "" else pack?.bank?.senders?.joinToString("\n").orEmpty()) }
    var zone by rememberSaveable(pack?.bank?.id, create) { mutableStateOf(pack?.bank?.zoneId ?: "Asia/Riyadh") }
    LabCard {
        Text(if (create || pack == null) "إضافة بنك" else "تعريف البنك", style = MaterialTheme.typography.titleMedium)
        LabField("اسم البنك", name, { name = it }, !busy)
        LabField("مرسل SMS — كل مرسل في سطر", senders, { senders = it }, !busy, true)
        LabField("المنطقة الزمنية", zone, { zone = it }, !busy)
        Button(enabled = !busy && name.isNotBlank() && senders.isNotBlank(), onClick = {
            val profile = BankProfile(if (create || pack == null) UUID.randomUUID().toString() else pack.bank.id,
                name.trim(), lines(senders), zone.trim())
            save(if (create || pack == null) RulePack(bank = profile, version = 1, rules = emptyList()) else pack.copy(bank = profile))
            create = false
        }) { Text("حفظ إصدار") }
        if (pack != null) TextButton(onClick = { create = !create }, enabled = !busy) { Text(if (create) "إلغاء البنك الجديد" else "بنك جديد") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(pack: RulePack, busy: Boolean, save: (RulePack) -> Unit) {
    var ruleId by rememberSaveable(pack.bank.id) { mutableStateOf(pack.rules.firstOrNull()?.id) }
    val current = pack.rules.firstOrNull { it.id == ruleId }
    var family by rememberSaveable(pack.bank.id, ruleId) { mutableStateOf(current?.family ?: "PURCHASE") }
    var phrases by rememberSaveable(pack.bank.id, ruleId) { mutableStateOf(current?.phrases?.joinToString("\n").orEmpty()) }
    var excludes by rememberSaveable(pack.bank.id, ruleId) { mutableStateOf(current?.excludes?.joinToString("\n").orEmpty()) }
    var amount by rememberSaveable(pack.bank.id, ruleId) { mutableStateOf(current?.amountLabels?.joinToString("\n") ?: "بمبلغ") }
    var card by rememberSaveable(pack.bank.id, ruleId) { mutableStateOf(current?.cardLabels?.joinToString("\n") ?: "بطاقة") }
    var source by rememberSaveable(pack.bank.id, ruleId) { mutableStateOf(current?.sourceLabels?.joinToString("\n").orEmpty()) }
    var destination by rememberSaveable(pack.bank.id, ruleId) { mutableStateOf(current?.destinationLabels?.joinToString("\n").orEmpty()) }
    var merchant by rememberSaveable(pack.bank.id, ruleId) { mutableStateOf(current?.merchantLabels?.joinToString("\n") ?: "لدى") }
    var direction by rememberSaveable(pack.bank.id, ruleId, family) {
        mutableStateOf(current?.takeIf { it.family == family }?.direction ?: LabFamilies.direction(family))
    }
    Text("قواعد v${pack.version}", style = MaterialTheme.typography.titleLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
        pack.rules.forEach { r -> FilterChip(ruleId == r.id, { ruleId = r.id },
            label = { Text("${familyLabel(r.family)} · ${r.phrases.firstOrNull().orEmpty()}") }, enabled = !busy) }
        FilterChip(ruleId == null, { ruleId = null }, label = { Text("قاعدة جديدة") }, enabled = !busy)
    }
    LabCard {
        Text("نوع الرسالة")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
            LabFamilies.editable.sorted().forEach { f -> FilterChip(family == f, { family = f;direction = LabFamilies.direction(f) }, label = { Text(familyLabel(f)) }, enabled = !busy) }
        }
        LabField("تحتوي على أحد العبارات — عبارة في كل سطر", phrases, { phrases = it }, !busy, true)
        LabField("لا تحتوي على — عبارة في كل سطر", excludes, { excludes = it }, !busy, true)
        if (family in LabFamilies.financial) {
            Row(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
                FilterChip(direction == "OUTGOING", { direction = "OUTGOING" }, label = { Text("خصم") }, enabled = !busy)
                FilterChip(direction == "INCOMING", { direction = "INCOMING" }, label = { Text("إضافة") }, enabled = !busy)
            }
            LabField("وسم مبلغ العملية — كل وسم في سطر", amount, { amount = it }, !busy, true)
            LabField("وسم البطاقة", card, { card = it }, !busy)
            LabField("وسم حساب المصدر", source, { source = it }, !busy)
            LabField("وسم حساب الوجهة", destination, { destination = it }, !busy)
            LabField("وسم التاجر — اسم في سطر منفصل", merchant, { merchant = it }, !busy)
        }
        Text("العملات الحالية: SAR · USD · EUR · GBP. المبلغ يحتاج عملة صريحة. رموز التحقق لها أولوية ثابتة.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            val rule = RuleSpec(current?.id ?: "rule.${UUID.randomUUID()}", family, lines(phrases), lines(excludes),
                lines(amount), lines(card), lines(source), lines(destination), lines(merchant),
                balanceLabels = current?.balanceLabels ?: listOf("الرصيد المتاح"), direction = direction)
            save(pack.copy(rules = pack.rules.filterNot { it.id == rule.id } + rule))
        }, enabled = !busy && phrases.isNotBlank()) { Text("حفظ كإصدار جديد") }
        if (current != null) TextButton(onClick = { save(pack.copy(rules = pack.rules.filterNot { it.id == current.id }));ruleId = null }, enabled = !busy) { Text("حذف القاعدة في إصدار جديد") }
    }
}

@Composable
private fun SamplePanel(pack: RulePack, state: LabUiState, vm: LabViewModel) {
    var sender by rememberSaveable(pack.bank.id) { mutableStateOf(pack.bank.senders.first()) }
    var body by rememberSaveable(pack.bank.id) { mutableStateOf("") }
    var date by rememberSaveable(pack.bank.id) { mutableStateOf(LocalDate.now(ZoneId.of(pack.bank.zoneId)).toString()) }
    var expected by rememberSaveable(pack.bank.id) { mutableStateOf("") }
    Text("تجربة رسالة", style = MaterialTheme.typography.titleLarge)
    LabCard {
        LabField("المرسل", sender, { sender = it;vm.clearPreview() }, !state.busy)
        LabField("النص الأصلي", body, { body = it;vm.clearPreview() }, !state.busy, true)
        LabField("تاريخ العينة YYYY-MM-DD", date, { date = it }, !state.busy)
        Row(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
            Button(onClick = { vm.preview(sender, body) }, enabled = !state.busy && body.isNotBlank()) { Text("حلّل") }
            OutlinedButton(onClick = { vm.sample(sender, body, date, expected) }, enabled = !state.busy && body.isNotBlank()) { Text("حفظ العينة") }
        }
        Text("الحفظ يضيف عينة مستقلة. راجع الإجابة المتوقعة يدويًا لقياس الدقة.", style = MaterialTheme.typography.bodySmall)
        LabField("إجابة متوقعة JSON — اختياري", expected, { expected = it }, !state.busy, true)
        TextButton(onClick = { expected = LabJson.format.encodeToString(ExpectedResult("SUCCESS", LabFacts("PURCHASE", "OUTGOING", "100.00", "SAR"))) }, enabled = !state.busy) { Text("نموذج إجابة لتعديله يدويًا") }
    }
    state.preview?.let { OutcomeCard(it) }
    Text("العينات المحفوظة", style = MaterialTheme.typography.titleMedium)
    state.snapshot?.samples.orEmpty().forEach { sample ->
        LabCard {
            Text(sample.sender, style = MaterialTheme.typography.labelMedium)
            Text(sample.body)
            Text(if (!sample.annotated) "غير موسومة — لا تدخل قياس الدقة" else "لها إجابة متوقعة", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { sender = sample.sender;body = sample.body }, enabled = !state.busy) { Text("فتح للتجربة") }
        }
    }
}

@Composable
private fun ResultsPanel(pack: RulePack, state: LabUiState, vm: LabViewModel) {
    var from by rememberSaveable(pack.bank.id) { mutableStateOf(LocalDate.now(ZoneId.of(pack.bank.zoneId)).minusDays(30).toString()) }
    var to by rememberSaveable(pack.bank.id) { mutableStateOf(LocalDate.now(ZoneId.of(pack.bank.zoneId)).toString()) }
    Text("اختبار العينات", style = MaterialTheme.typography.titleLarge)
    LabCard {
        LabField("من تاريخ YYYY-MM-DD", from, { from = it }, !state.busy)
        LabField("إلى تاريخ YYYY-MM-DD — شامل", to, { to = it }, !state.busy)
        Text("النطاق يختار عينات المختبر المحفوظة حسب توقيت ${pack.bank.zoneId}.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.run(from, to) }, enabled = !state.busy) { Text("تشغيل وحفظ النتائج") }
    }
    state.snapshot?.runs.orEmpty().forEach { run ->
        FilterChip(state.selectedRun == run.id, { vm.selectRun(run.id) }, enabled = !state.busy,
            label = { Text("v${run.version} · ${run.sampleCount} عينات · ${run.fromDate} إلى ${run.toDate}") })
    }
    if (state.results.rows.isNotEmpty()) Text("مطابقة كاملة: ${state.results.matched} / ${state.results.labeled} عينة موسومة · ${state.results.unlabeled} غير موسومة")
    state.results.rows.forEach { row -> ResultCard(row) }
}

@Composable
private fun ResultCard(row: LabRunResultView) {
    var expand by rememberSaveable(row.runId, row.sampleId) { mutableStateOf(false) }
    val outcome = row.outcome
    LabCard {
        Text(row.body)
        Text(statusLabel(outcome.status), color = MaterialTheme.colorScheme.primary)
        val differences = row.differences
        Text(when { differences == null -> "لم تُحدد إجابة متوقعة"; differences.isEmpty() -> "مطابق للمُتوقع"; else -> "اختلاف: ${differences.joinToString()}" })
        TextButton(onClick = { expand = !expand }) { Text(if (expand) "إخفاء التفاصيل" else "عرض الحقائق والأدلة والمقارنة") }
        if (expand) {
            FactsText(outcome.facts)
            Text(outcome.trace.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            row.baseline?.let { baseline ->
                Text("محلل الجزيرة المنسوخ: ${statusLabel(baseline.status)}", style = MaterialTheme.typography.titleSmall)
                FactsText(baseline.facts)
            }
        }
    }
}

@Composable
private fun OutcomeCard(outcome: LabOutcome) {
    LabCard {
        Text(statusLabel(outcome.status), style = MaterialTheme.typography.titleMedium)
        FactsText(outcome.facts)
        Text("سبب القرار", style = MaterialTheme.typography.titleSmall)
        outcome.trace.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        outcome.evidence.forEach { e -> Text("${e.field}: ${e.value} ← ${e.label} · ${e.ruleId} · موضع في النص المطبّع ${e.start}–${e.endExclusive}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FactsText(facts: LabFacts) {
    Text(listOfNotNull(facts.family?.let(::familyLabel), facts.direction?.let { "الاتجاه: $it" },
        facts.amount?.let { "المبلغ: $it ${facts.currency.orEmpty()}" }, facts.card?.let { "البطاقة: •••• $it" },
        facts.source?.let { "المصدر: •••• $it" }, facts.destination?.let { "الوجهة: •••• $it" },
        facts.merchant?.let { "التاجر: $it" }, facts.balance?.let { "الرصيد: $it" }).joinToString("\n"))
}

@Composable
private fun PackTransfer(pack: RulePack?, busy: Boolean, importPack: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    TextButton(onClick = { expanded = !expanded }) { Text("استيراد / تصدير حزمة قواعد JSON") }
    if (expanded) LabCard {
        LabField("حزمة القواعد", text, { text = it }, !busy, true)
        Button(onClick = { importPack(text) }, enabled = !busy && text.isNotBlank()) { Text("استيراد كإصدار جديد") }
        if (pack != null) OutlinedButton(onClick = { text = LabJson.format.encodeToString(pack) }, enabled = !busy) { Text("عرض الحزمة الحالية للنسخ") }
        Text("القواعد عبارات ووسوم؛ الحزمة لا تشغّل كودًا أو Regex من الملف.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun lines(text: String) = text.lineSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()
private fun statusLabel(status: String) = when (status) {
    "SUCCESS" -> "نجاح التحليل التجريبي"
    "NON_FINANCIAL" -> "رسالة غير مالية"
    "REVIEW_REQUIRED" -> "تحتاج مراجعة"
    "UNSUPPORTED" -> "غير مدعوم"
    "INVALID" -> "مدخلات غير صالحة"
    "PARTIAL" -> "تحليل جزئي"
    else -> status
}
private fun familyLabel(family: String) = when (family) {
    "PURCHASE" -> "مشتريات"; "TRANSFER_IN" -> "حوالة واردة"; "TRANSFER_OUT" -> "حوالة صادرة"
    "CARD_PAYMENT" -> "سداد بطاقة"; "BILL_PAYMENT" -> "سداد فاتورة"; "FINANCING_INSTALLMENT" -> "قسط تمويل"
    "WITHDRAWAL" -> "سحب نقدي"; "REFUND" -> "استرداد"; "FEE" -> "رسوم"; "BALANCE_NOTICE" -> "إشعار رصيد"
    "NON_FINANCIAL" -> "إشعار غير مالي"; "OTP" -> "رمز تحقق"; "UNKNOWN" -> "غير معروف"; else -> family
}
