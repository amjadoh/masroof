package com.baraa.masroof.lab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.application.LabWorkflow
import com.baraa.masroof.lab.data.LabDatabase
import com.baraa.masroof.lab.engine.*
import com.baraa.masroof.lab.engine.evidence.*
import com.baraa.masroof.lab.presentation.ParsingLabEntry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** Runs in the library's isolated instrumentation package, never the installed financial app. */
@RunWith(AndroidJUnit4::class)
class LabEvidenceDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun comparisonAndKnowledgeScreensWorkWithSeparateDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        assertNotEquals("com.baraa.masroof", context.packageName)
        context.deleteDatabase(LabDatabase.FILE_NAME)
        val workflow = LabWorkflow(LabDatabase.open(context))
        workflow.savePack(RulePack(bank = BankProfile("device", "بنك التجربة", listOf("DEVICE")), version = 1, rules = emptyList()))
        workflow.addSample("device", "DEVICE", "شراء\nبمبلغ 10 SAR\nبطاقة: 1234", LocalDate.now().toString(), null)
        workflow.close()
        compose.setContent { ParsingLabEntry {} }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("بنك التجربة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("النتائج", useUnmergedTree = true).performClick()
        compose.onNodeWithText("تشغيل المقارنة وحفظها").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("1 رسالة · 0 لها إجابة · 1 بلا تقييم").fetchSemanticsNodes().isNotEmpty() }
        capture("evidence-comparison")
        compose.onNodeWithText("الحقول وأسباب القرار").performScrollTo().performClick()
        compose.onNodeWithText("مراجعة الإجابة الصحيحة").performScrollTo().assertExists()
        compose.onNodeWithText("القواعد", useUnmergedTree = true).performClick()
        compose.onNodeWithText("معرفة الأدلة").performScrollTo().performClick()
        compose.onNodeWithText("معرفة الأدلة · إصدار 1").performScrollTo().assertExists()
        capture("evidence-knowledge")
        val db = LabDatabase.open(context)
        assertEquals(1, db.dao().comparisons("device").size)
        db.close()
    }
    @Test fun pairedEngineTimingOnAndroidRuntime() {
        val now = Instant.now();val bank = BankProfile("timing", "Timing", listOf("TIMING"))
        val bodies = listOf("شراء\nبمبلغ 10 SAR\nبطاقة: 1234", "حوالة واردة\nالمبلغ: 150 SAR\nإلى حساب: 8001",
            "POS Purchase\nCredit card 1234\nof: 38.00 SAR\nAvailable Balance: 100 SAR\nDue Amount: 200 SAR",
            "رمز التحقق لعملية الشراء هو 9876 بمبلغ 10 SAR")
        val old = SharedLabEngine();val new = EvidenceLabEngine();val library = SharedRuleDefaults.create();val knowledge = EvidenceDefaults.create()
        fun a(body: String) = old.parse(listOf(bank), library, "TIMING", body, "timing", now)
        fun b(body: String) = new.parse(listOf(bank), knowledge, "TIMING", body, "timing", now)
        val aMs = mutableListOf<Double>();val bMs = mutableListOf<Double>()
        val initialMemory = android.os.Debug.MemoryInfo().also(android.os.Debug::getMemoryInfo).totalPss
        var sampledMaxPss = initialMemory
        bodies.forEach { body ->
            repeat(5) { a(body);b(body) }
            repeat(25) { i ->
                fun ma() { val start = System.nanoTime();a(body);aMs += (System.nanoTime()-start)/1_000_000.0 }
                fun mb() { val start = System.nanoTime();b(body);bMs += (System.nanoTime()-start)/1_000_000.0 }
                if (i%2==0) { ma();mb() } else { mb();ma() }
            }
            sampledMaxPss = maxOf(sampledMaxPss, android.os.Debug.MemoryInfo().also(android.os.Debug::getMemoryInfo).totalPss)
        }
        fun q(v: List<Double>, p: Double) = v.sorted()[((v.size-1)*p).toInt()]
        val result = "android-runtime, synthetic=4, repeats=25, shared median=${q(aMs,.5)}, p95=${q(aMs,.95)}, evidence median=${q(bMs,.5)}, p95=${q(bMs,.95)} ms; process PSS initial=$initialMemory KB, sampledMax=$sampledMaxPss KB (test process, not engine peak)"
        android.util.Log.i("LabEvidenceBenchmark", result)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", result) })
        assertEquals("NON_FINANCIAL", b(bodies.last()).outcome.status)
        assertEquals("SUCCESS", b(bodies.first()).outcome.status)
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) };bitmap.recycle() }
    }
}
