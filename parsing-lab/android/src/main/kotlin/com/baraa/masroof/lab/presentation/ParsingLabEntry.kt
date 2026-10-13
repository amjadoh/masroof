package com.baraa.masroof.lab.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.time.Instant
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
            if (tab != "play" && tab != "rules" && state.snapshot?.banks?.isNotEmpty() == true) {
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
                        Text("${state.snapshot?.samples?.size ?: 0} رسائل · يستخدم المكتبة المشتركة")
                        Text("مرسلون: ${pack.bank.senders.joinToString()}")
                        Text("أضف الاسم والمرسل؛ جميع البنوك تستخدم طرق الفهم المشتركة نفسها.", style = MaterialTheme.typography.bodySmall)
                    }
                    BankEditor(pack, state.busy, vm::savePack)
                    if (state.snapshot?.banks?.isEmpty() == true) {
                        OutlinedButton(onClick = vm::seed, enabled = !state.busy) { Text("إضافة الجزيرة وعينات توضيحية") }
                    }
                    PackTransfer(pack, state.busy, vm::importPack)
                    OutlinedButton(onClick = { confirmReset = true }, enabled = !state.busy) { Text("مسح بيانات المختبر") }
                }
                "play" -> if (pack == null) Text("أضف بنكًا أولًا") else SamplePanel(pack, state, vm)
                "rules" -> state.snapshot?.library?.let { RuleEditor(it, state.busy, vm::saveLibrary) }
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
        var advanced by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { advanced = !advanced }) { Text("إعدادات التوقيت") }
        if (advanced) LabField("المنطقة الزمنية", zone, { zone = it }, !busy)
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
private fun RuleEditor(library: SharedRuleLibrary, busy: Boolean, save: (SharedRuleLibrary) -> Unit) {
    var ruleId by rememberSaveable { mutableStateOf(library.rules.firstOrNull()?.id) }
    val current = library.rules.firstOrNull { it.id == ruleId }
    var family by rememberSaveable(library.version, ruleId) { mutableStateOf(current?.family ?: "PURCHASE") }
    var phrases by rememberSaveable(library.version, ruleId) { mutableStateOf(current?.phrases?.joinToString("\n").orEmpty()) }
    var excludes by rememberSaveable(library.version, ruleId) { mutableStateOf(current?.excludes?.joinToString("\n").orEmpty()) }
    var amount by rememberSaveable(library.version, ruleId) { mutableStateOf(current?.amountLabels?.joinToString("\n") ?: "بمبلغ") }
    var card by rememberSaveable(library.version, ruleId) { mutableStateOf(current?.cardLabels?.joinToString("\n") ?: "بطاقة") }
    var source by rememberSaveable(library.version, ruleId) { mutableStateOf(current?.sourceLabels?.joinToString("\n").orEmpty()) }
    var destination by rememberSaveable(library.version, ruleId) { mutableStateOf(current?.destinationLabels?.joinToString("\n").orEmpty()) }
    var merchant by rememberSaveable(library.version, ruleId) { mutableStateOf(current?.merchantLabels?.joinToString("\n") ?: "لدى") }
    var balance by rememberSaveable(library.version, ruleId) { mutableStateOf(current?.balanceLabels?.joinToString("\n") ?: "الرصيد المتاح") }
    var direction by rememberSaveable(library.version, ruleId, family) {
        mutableStateOf(current?.takeIf { it.family == family }?.direction ?: LabFamilies.direction(family))
    }
    Text("مكتبة الفهم المشتركة v${library.version}", style = MaterialTheme.typography.titleLarge)
    Text("التعديلات هنا تسري على جميع البنوك. أضف العبارات والوسوم دون برمجة؛ تنسيقات استخراج جديدة قد تحتاج تطوير المحرك.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
        library.rules.forEach { r -> FilterChip(ruleId == r.id, { ruleId = r.id },
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
            LabField("وسم البطاقة — كل وسم في سطر", card, { card = it }, !busy, true)
            LabField("وسم حساب المصدر — كل وسم في سطر", source, { source = it }, !busy, true)
            LabField("وسم حساب الوجهة — كل وسم في سطر", destination, { destination = it }, !busy, true)
            LabField("وسم التاجر — يدعم الفصل عن تاريخ بصيغة ISO", merchant, { merchant = it }, !busy, true)
            LabField("وسم الرصيد — كل وسم في سطر", balance, { balance = it }, !busy, true)
        }
        Text("العملات الحالية: SAR · USD · EUR · GBP. المبلغ يحتاج عملة صريحة. رموز التحقق لها أولوية ثابتة.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            val rule = RuleSpec(current?.id ?: "rule.${UUID.randomUUID()}", family, lines(phrases), lines(excludes),
                lines(amount), lines(card), lines(source), lines(destination), lines(merchant),
                balanceLabels = lines(balance), direction = direction)
            save(library.copy(rules = library.rules.filterNot { it.id == rule.id } + rule))
        }, enabled = !busy && phrases.isNotBlank()) { Text("حفظ كإصدار جديد") }
        if (current != null) TextButton(onClick = { save(library.copy(rules = library.rules.filterNot { it.id == current.id }));ruleId = null }, enabled = !busy) { Text("حذف القاعدة في إصدار جديد") }
    }
}

@Composable
private fun SamplePanel(pack: RulePack, state: LabUiState, vm: LabViewModel) {
    val context = LocalContext.current
    var from by rememberSaveable(pack.bank.id) { mutableStateOf(LocalDate.now(ZoneId.of(pack.bank.zoneId)).minusDays(30).toString()) }
    var to by rememberSaveable(pack.bank.id) { mutableStateOf(LocalDate.now(ZoneId.of(pack.bank.zoneId)).toString()) }
    var expanded by remember { mutableStateOf(false) }
    var permissionError by rememberSaveable { mutableStateOf(false) }
    var pendingBank by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFrom by rememberSaveable { mutableStateOf("") }
    var pendingTo by rememberSaveable { mutableStateOf("") }
    var visible by remember(pack.bank.id) { mutableIntStateOf(20) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val bank = pendingBank
        pendingBank = null
        permissionError = !granted
        if (granted && bank != null) vm.importPhoneSms(bank, pendingFrom, pendingTo)
    }
    Text("استيراد رسائل البنك", style = MaterialTheme.typography.titleLarge)
    LabCard {
        Text("اختيار البنك", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(pack.bank.name) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.snapshot?.banks.orEmpty().forEach { bank ->
                    DropdownMenuItem(text = { Text(bank.bank.name) }, onClick = { expanded = false; vm.selectBank(bank.bank.id) })
                }
            }
        }
        Text("مرسلون: ${pack.bank.senders.joinToString()}", style = MaterialTheme.typography.bodySmall)
        LabDateField("من تاريخ", from, !state.busy) { from = it }
        LabDateField("إلى تاريخ", to, !state.busy) { to = it }
        val validRange = from <= to
        if (!validRange) Text("تاريخ البداية يجب أن يسبق النهاية أو يساويها", color = MaterialTheme.colorScheme.error)
        Text("يشمل يوم البداية والنهاية حسب توقيت ${pack.bank.zoneId}. الحد 1000 رسالة في الاستيراد.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            permissionError = false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                vm.importPhoneSms(pack.bank.id, from, to)
            } else {
                pendingBank = pack.bank.id; pendingFrom = from; pendingTo = to
                permission.launch(Manifest.permission.READ_SMS)
            }
        }, enabled = !state.busy && validRange && pendingBank == null, modifier = Modifier.fillMaxWidth()) { Text("استيراد الرسائل") }
        if (permissionError) Text("لم يُمنح إذن قراءة الرسائل. يمكنك تفعيله من إعدادات أذونات التطبيق ثم إعادة الاستيراد.", color = MaterialTheme.colorScheme.error)
        Text("تُحفظ الرسائل في قاعدة المختبر فقط. إعادة الاستيراد لا تكرر الرسائل، ولا تُنشئ عمليات في مصروف.", style = MaterialTheme.typography.bodySmall)
    }
    state.preview?.let { OutcomeCard(it) }
    val samples = state.snapshot?.samples.orEmpty()
    Text("الرسائل المحفوظة · ${samples.size}", style = MaterialTheme.typography.titleMedium)
    if (samples.isEmpty()) Text("اختر البنك والفترة لبدء الاستيراد")
    samples.take(visible).forEach { sample ->
        LabCard {
            Text(sample.sender, style = MaterialTheme.typography.labelMedium)
            Text(Instant.ofEpochMilli(sample.receivedAt).atZone(ZoneId.of(pack.bank.zoneId)).toLocalDateTime().toString(), style = MaterialTheme.typography.bodySmall)
            Text(sample.body)
            Text(if (!sample.annotated) "غير موسومة — لا تدخل قياس الدقة" else "لها إجابة متوقعة", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { vm.previewSaved(sample.id) }, enabled = !state.busy) { Text("تحليل الرسالة") }
        }
    }
    if (visible < samples.size) TextButton(onClick = { visible += 20 }) { Text("عرض المزيد") }
}

@Composable
private fun ResultsPanel(pack: RulePack, state: LabUiState, vm: LabViewModel) {
    var from by rememberSaveable(pack.bank.id) { mutableStateOf(LocalDate.now(ZoneId.of(pack.bank.zoneId)).minusDays(30).toString()) }
    var to by rememberSaveable(pack.bank.id) { mutableStateOf(LocalDate.now(ZoneId.of(pack.bank.zoneId)).toString()) }
    Text("اختبار الرسائل بالمحرك المشترك", style = MaterialTheme.typography.titleLarge)
    LabCard {
        LabDateField("من تاريخ", from, !state.busy) { from = it }
        LabDateField("إلى تاريخ", to, !state.busy) { to = it }
        Text("النطاق يختار عينات المختبر المحفوظة حسب توقيت ${pack.bank.zoneId}.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.run(from, to) }, enabled = !state.busy && from <= to) { Text("تشغيل وحفظ النتائج") }
    }
    state.snapshot?.runs.orEmpty().forEach { run ->
        FilterChip(state.selectedRun == run.id, { vm.selectRun(run.id) }, enabled = !state.busy,
            label = { Text("${if (run.shared) "المكتبة المشتركة" else "قواعد البنك السابقة"} v${run.version} · ${run.sampleCount} عينات · ${run.fromDate} إلى ${run.toDate}") })
    }
    if (state.results.rows.isNotEmpty()) Text("مطابقة الحقول الموسومة: ${state.results.matched} / ${state.results.labeled} عينة موسومة · ${state.results.unlabeled} غير موسومة")
    var reviewOnly by rememberSaveable { mutableStateOf(false) }
    FilterChip(reviewOnly, { reviewOnly = !reviewOnly }, label = { Text("تحتاج مراجعة فقط") })
    val shown = state.results.rows.filter { !reviewOnly || it.outcome.status == "REVIEW_REQUIRED" }
    var visible by remember(state.selectedRun, reviewOnly) { mutableIntStateOf(20) }
    if (reviewOnly) {
        shown.groupBy { it.outcome.facts.family ?: "UNKNOWN" }.forEach { (family, rows) ->
            Text("${familyLabel(family)} · ${rows.size} رسائل", style = MaterialTheme.typography.labelLarge)
        }
    }
    shown.take(visible).forEach { row -> ResultCard(row, state.busy) { vm.annotate(row.sampleId, it) } }
    if (visible < shown.size) TextButton(onClick = { visible += 20 }) { Text("عرض المزيد") }
}

@Composable
private fun ResultCard(row: LabRunResultView, busy: Boolean, annotate: (ExpectedResult) -> Unit) {
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
            outcome.evidence.forEach { Text("${it.field}: ${it.value} ← ${it.label}", style = MaterialTheme.typography.bodySmall) }
            ExpectedEditor(row, busy, annotate)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpectedEditor(row: LabRunResultView, busy: Boolean, save: (ExpectedResult) -> Unit) {
    var editing by rememberSaveable(row.runId, row.sampleId) { mutableStateOf(false) }
    TextButton(onClick = { editing = !editing }, enabled = !busy) { Text(if (editing) "إلغاء التصحيح" else "تحديد الإجابة الصحيحة") }
    if (!editing) return
    key(row.runId, row.sampleId) {
        val initial = row.expected ?: ExpectedResult(row.outcome.status, row.outcome.facts)
        var family by remember { mutableStateOf(initial.facts.family ?: "UNKNOWN") }
        var status by remember { mutableStateOf(initial.status) }
        var amount by remember { mutableStateOf(initial.facts.amount.orEmpty()) }
        var currency by remember { mutableStateOf(initial.facts.currency ?: "SAR") }
        var card by remember { mutableStateOf(initial.facts.card.orEmpty()) }
        var source by remember { mutableStateOf(initial.facts.source.orEmpty()) }
        var destination by remember { mutableStateOf(initial.facts.destination.orEmpty()) }
        var merchant by remember { mutableStateOf(initial.facts.merchant.orEmpty()) }
        var balance by remember { mutableStateOf(initial.facts.balance.orEmpty()) }
        var due by remember { mutableStateOf(initial.facts.dueAmount.orEmpty()) }
        var iban by remember { mutableStateOf(initial.facts.ibanSuffix.orEmpty()) }
        var date by remember { mutableStateOf(initial.facts.occurredAtLocal.orEmpty()) }
        Text("حدد الحقول التي تريد اختبارها. الحقول الاختيارية الفارغة لا تدخل المقارنة. التصحيح لا يغيّر المحرك تلقائيًا.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
            (LabFamilies.editable + "UNKNOWN").sorted().forEach { f ->
                FilterChip(family == f, { family = f }, label = { Text(familyLabel(f)) }, enabled = !busy)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
            listOf("SUCCESS", "REVIEW_REQUIRED", "NON_FINANCIAL").forEach { s ->
                FilterChip(status == s, { status = s }, label = { Text(statusLabel(s)) }, enabled = !busy)
            }
        }
        LabField("مبلغ العملية الصحيح", amount, { amount = it }, !busy)
        FlowRow { listOf("SAR", "USD", "EUR", "GBP").forEach { c ->
            FilterChip(currency == c, { currency = c }, label = { Text(c) }, enabled = !busy)
        } }
        LabField("آخر أربعة أرقام للبطاقة", card, { card = it }, !busy)
        LabField("آخر أربعة أرقام لحساب المصدر", source, { source = it }, !busy)
        LabField("آخر أربعة أرقام لحساب الوجهة", destination, { destination = it }, !busy)
        LabField("التاجر", merchant, { merchant = it }, !busy)
        LabField("الرصيد", balance, { balance = it }, !busy)
        LabField("المبلغ المستحق", due, { due = it }, !busy)
        LabField("آخر أربعة أرقام للآيبان", iban, { iban = it }, !busy)
        LabField("تاريخ العملية — مثال 2026-10-01T12:30", date, { date = it }, !busy)
        Button(onClick = {
            fun optional(s: String) = s.trim().takeIf(String::isNotEmpty)
            save(ExpectedResult(status, LabFacts(family, if (family in LabFamilies.financial) LabFamilies.direction(family) else null,
                optional(amount), if (amount.isNotBlank()) currency else null, optional(card), optional(source), optional(destination),
                optional(merchant), optional(balance), optional(due), optional(iban), optional(date))))
        }, enabled = !busy) { Text("حفظ الإجابة للاختبار القادم") }
    }
}

@Composable
private fun FactsText(facts: LabFacts) {
    Text(listOfNotNull(facts.family?.let(::familyLabel), facts.direction?.let { "الاتجاه: $it" },
        facts.amount?.let { "المبلغ: $it ${facts.currency.orEmpty()}" }, facts.card?.let { "البطاقة: •••• $it" },
        facts.source?.let { "المصدر: •••• $it" }, facts.destination?.let { "الوجهة: •••• $it" },
        facts.merchant?.let { "التاجر: $it" }, facts.balance?.let { "الرصيد: $it" },
        facts.dueAmount?.let { "المستحق: $it" }, facts.ibanSuffix?.let { "آخر أرقام الآيبان: $it" },
        facts.occurredAtLocal?.let { "تاريخ العملية: $it" }).joinToString("\n"))
}

@Composable
private fun PackTransfer(pack: RulePack?, busy: Boolean, importPack: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    TextButton(onClick = { expanded = !expanded }) { Text("استيراد / تصدير تعريف بنك قديم") }
    if (expanded) LabCard {
        LabField("حزمة القواعد", text, { text = it }, !busy, true)
        Button(onClick = { importPack(text) }, enabled = !busy && text.isNotBlank()) { Text("استيراد كإصدار جديد") }
        if (pack != null) OutlinedButton(onClick = { text = LabJson.format.encodeToString(pack) }, enabled = !busy) { Text("عرض الحزمة الحالية للنسخ") }
        Text("يُستورد تعريف البنك. تبقى قواعد الحزمة القديمة محفوظة للأرشيف؛ التحليل الجديد يستخدم المكتبة المشتركة.", style = MaterialTheme.typography.bodySmall)
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
