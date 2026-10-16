package com.baraa.masroof.lab.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.lab.application.LabSnapshot
import com.baraa.masroof.lab.application.LabWorkflow
import com.baraa.masroof.lab.application.LabResultSet
import com.baraa.masroof.lab.engine.*
import com.baraa.masroof.lab.engine.evidence.*
import com.baraa.masroof.lab.application.LabComparisonItem
import com.baraa.masroof.lab.application.LabComparisonView
import com.baraa.masroof.lab.application.LabKnowledgeImpact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LabUiState(val snapshot: LabSnapshot? = null, val bankId: String? = null, val busy: Boolean = false,
                      val error: String? = null, val notice: String? = null, val preview: LabOutcome? = null,
                      val results: LabResultSet = LabResultSet(), val selectedRun: String? = null,
                      val knowledge: EvidenceKnowledge? = null, val comparisons: List<LabComparisonView> = emptyList(),
                      val comparisonRows: List<LabComparisonItem> = emptyList(), val selectedComparison: String? = null,
                      val impact: LabKnowledgeImpact? = null)

class LabViewModel(private val workflow: LabWorkflow, initialBankId: String? = null) : ViewModel() {
    private val mutable = MutableStateFlow(LabUiState(bankId = initialBankId))
    val state = mutable.asStateFlow()
    init { action { refresh() } }
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message ?: "تعذر إكمال التجربة") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    private suspend fun refresh() {
        val snapshot = workflow.load(mutable.value.bankId)
        val bankId = mutable.value.bankId?.takeIf { id -> snapshot.banks.any { it.bank.id == id } } ?: snapshot.banks.firstOrNull()?.bank?.id
        mutable.value = mutable.value.copy(snapshot = snapshot, bankId = bankId, results = snapshot.results,
            selectedRun = snapshot.runs.firstOrNull()?.id)
        val comparisons = bankId?.let { workflow.comparisons(it) }.orEmpty()
        val comparison = comparisons.firstOrNull()?.id
        mutable.value = mutable.value.copy(knowledge = workflow.evidenceKnowledge(), comparisons = comparisons,
            selectedComparison = comparison, comparisonRows = comparison?.let { workflow.comparisonResults(it) }.orEmpty(), impact = null)
    }
    fun selectBank(id: String) = action { mutable.value = mutable.value.copy(bankId = id, preview = null); refresh() }
    fun savePack(pack: RulePack) = action {
        val saved = workflow.savePack(pack)
        mutable.value = mutable.value.copy(bankId = saved.bank.id);refresh()
        mutable.value = mutable.value.copy(notice = "حُفظ إصدار مستقل v${saved.version}")
    }
    fun saveLibrary(library: SharedRuleLibrary) = action {
        val saved = workflow.saveLibrary(library); refresh()
        mutable.value = mutable.value.copy(preview = null, notice = "حُفظت المكتبة المشتركة v${saved.version} لجميع البنوك؛ أعد الاختبار للمقارنة")
    }
    fun annotate(sampleId: String, expected: ExpectedResult) = action {
        workflow.annotate(requireNotNull(mutable.value.bankId), sampleId, expected)
        mutable.value = mutable.value.copy(notice = "حُفظ التصحيح للاختبارات القادمة؛ نتائج التجارب السابقة محفوظة كما هي")
    }
    fun sample(sender: String, body: String, date: String, expected: String?) = action {
        workflow.addSample(requireNotNull(mutable.value.bankId) { "اختر بنكًا" }, sender, body, date, expected);refresh()
        mutable.value = mutable.value.copy(notice = "حُفظت العينة في المختبر")
    }
    fun preview(sender: String, body: String) = action {
        val outcome = workflow.preview(requireNotNull(mutable.value.bankId) { "اختر بنكًا" }, sender, body)
        mutable.value = mutable.value.copy(preview = outcome)
    }
    fun clearPreview() { mutable.value = mutable.value.copy(preview = null) }
    fun previewSaved(id: String) = action {
        mutable.value = mutable.value.copy(preview = workflow.previewSaved(requireNotNull(mutable.value.bankId), id))
    }
    fun importPhoneSms(bankId: String, from: String, to: String) = action {
        val result = workflow.importPhoneSms(bankId, from, to)
        mutable.value = mutable.value.copy(bankId = bankId, preview = null);refresh()
        mutable.value = mutable.value.copy(notice = "استُوردت ${result.imported} رسالة إلى المختبر · ${result.duplicates} مكررة · ${result.skipped} متجاوزة")
    }
    fun run(from: String, to: String) = action {
        workflow.run(requireNotNull(mutable.value.bankId) { "اختر بنكًا" }, from, to);refresh()
        mutable.value = mutable.value.copy(notice = "اكتمل الاختبار وحُفظت نتائجه")
    }
    fun selectRun(id: String) = action { mutable.value = mutable.value.copy(results = workflow.viewResults(id), selectedRun = id) }
    fun compare(from: String, to: String) = action {
        workflow.compare(requireNotNull(mutable.value.bankId), from, to);refresh()
        mutable.value = mutable.value.copy(notice = "حُفظت مقارنة المحركين؛ افتح النتائج ثم مقارنة الأدلة")
    }
    fun selectComparison(id: String) = action { mutable.value = mutable.value.copy(comparisonRows = workflow.comparisonResults(id), selectedComparison = id) }
    fun previewKnowledge(draft: EvidenceKnowledge) = action {
        mutable.value = mutable.value.copy(impact = workflow.previewKnowledge(requireNotNull(mutable.value.bankId), draft))
    }
    fun saveKnowledge(draft: EvidenceKnowledge) = action {
        val impact = requireNotNull(mutable.value.impact) { "عاين أثر التعديل أولًا" }
        require(impact.draft == draft && impact.bankId == mutable.value.bankId) { "تغيرت المسودة؛ أعد المعاينة" }
        val saved = workflow.saveKnowledge(draft);refresh()
        mutable.value = mutable.value.copy(notice = "حُفظ إصدار معرفة الأدلة ${saved.version}؛ أعد تشغيل المقارنة")
    }
    fun seed() = action { workflow.seedExamples();refresh(); mutable.value = mutable.value.copy(notice = "العينات المضافة توضيحية ومجهّلة") }
    fun importPack(text: String) = action {
        val pack = workflow.importPack(text);mutable.value = mutable.value.copy(bankId = pack.bank.id);refresh()
    }
    fun reset() = action { workflow.reset();mutable.value = LabUiState(busy = true);refresh() }
    override fun onCleared() { workflow.close() }
}
