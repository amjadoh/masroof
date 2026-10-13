package com.baraa.masroof.lab.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.data.LabDatabase
import com.baraa.masroof.lab.engine.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabWorkflowTest {
    @Test fun upgradingExampleTemplateKeepsOldVersionsAndSavedResults() = runBlocking {
        workflow.seedExamples()
        val bank = "BANK_ALJAZIRA"
        val original = workflow.load(bank).banks.single()
        val legacy = original.copy(rules = original.rules.map { if (it.id == "purchase.pos") it.copy(
            amountLabels = listOf("بمبلغ"), cardLabels = listOf("بطاقة ائتمانية", "بطاقة"),
            merchantLabels = listOf("لدى"), balanceLabels = listOf("الرصيد المتاح")) else it })
        workflow.savePack(legacy)
        workflow.addSample(bank, "AlJazira", "POS Purchase Contactless\nCredit card 1234\nof :42.00 SAR\nAt: TEST_MERCHANT\nAvailable Balance : 9000.00 SAR", "2026-10-01", null)
        val old = workflow.run(bank, "2026-10-01", "2026-10-01")
        assertEquals("REVIEW_REQUIRED", workflow.viewResults(old).rows.single().outcome.status)
        workflow.upgradeExamplePurchase(bank)
        val upgraded = workflow.load(bank).banks.single()
        assertEquals(3, upgraded.version)
        assertEquals(legacy.rules.filterNot { it.id == "purchase.pos" }, upgraded.rules.filterNot { it.id == "purchase.pos" })
        val fresh = workflow.run(bank, "2026-10-01", "2026-10-01")
        assertEquals("1234", workflow.viewResults(fresh).rows.single().outcome.facts.card)
        assertEquals("SUCCESS", workflow.viewResults(fresh).rows.single().outcome.status)
        assertEquals("REVIEW_REQUIRED", workflow.viewResults(old).rows.single().outcome.status)
        assertEquals(3, workflow.versions(bank).size)
    }
    private lateinit var context: Context
    private lateinit var db: LabDatabase
    private lateinit var workflow: LabWorkflow
    private val pack = RulePack(bank = BankProfile("demo", "تجريبي", listOf("DEMO")), version = 1,
        rules = listOf(RuleSpec("purchase", "PURCHASE", listOf("شراء"))))
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext();context.deleteDatabase(LabDatabase.FILE_NAME)
        db = LabDatabase.open(context);workflow = LabWorkflow(db)
    }
    @After fun cleanup() { workflow.close();context.deleteDatabase(LabDatabase.FILE_NAME);context.deleteDatabase("masroof.db") }
    @Test fun resetTouchesOnlyLabDatabase() = runBlocking {
        val production = context.openOrCreateDatabase("masroof.db", Context.MODE_PRIVATE, null)
        production.execSQL("CREATE TABLE sentinel (value TEXT)");production.execSQL("INSERT INTO sentinel VALUES ('keep')")
        workflow.savePack(pack);workflow.reset()
        assertTrue(workflow.load(null).banks.isEmpty())
        production.rawQuery("SELECT value FROM sentinel", null).use { assertTrue(it.moveToFirst());assertEquals("keep", it.getString(0)) }
        production.close()
    }
    @Test fun runsKeepVersionAndExpectedSnapshotsAcrossEditsAndRestart() = runBlocking {
        workflow.savePack(pack)
        val expected = LabJson.format.encodeToString(ExpectedResult("SUCCESS", LabFacts("PURCHASE", "OUTGOING", "100", "SAR")))
        workflow.addSample("demo", "DEMO", "شراء بمبلغ 100 SAR", "2026-10-01", expected)
        val first = workflow.run("demo", "2026-10-01", "2026-10-01")
        workflow.savePack(pack.copy(rules = listOf(pack.rules.single().copy(amountLabels = listOf("القيمة")))))
        val second = workflow.run("demo", "2026-10-01", "2026-10-01")
        assertEquals("SUCCESS", LabJson.format.decodeFromString<LabOutcome>(workflow.results(first).single().outcomeJson).status)
        assertEquals("REVIEW_REQUIRED", LabJson.format.decodeFromString<LabOutcome>(workflow.results(second).single().outcomeJson).status)
        assertEquals(1, workflow.viewResults(first).matched)
        assertEquals(0, workflow.viewResults(second).matched)
        assertEquals(1, workflow.viewResults(second).labeled)
        workflow.close();db = LabDatabase.open(context);workflow = LabWorkflow(db)
        val state = workflow.load("demo")
        assertEquals(2, state.runs.size);assertEquals(setOf(1, 2), workflow.versions("demo").map { it.version }.toSet())
        assertEquals(expected, workflow.results(first).single().expectedJson)
    }
    @Test fun dateBoundsAreInclusiveAndIndependentOfProductionImportSetting() = runBlocking {
        workflow.savePack(pack)
        listOf("2026-09-30", "2026-10-01", "2026-10-02").forEach { workflow.addSample("demo", "DEMO", "شراء بمبلغ 100 SAR", it, null) }
        val run = workflow.run("demo", "2026-10-01", "2026-10-01")
        assertEquals(1, workflow.results(run).size)
    }
    @Test fun duplicateSenderImportIsRejectedWithoutAddingVersion() = runBlocking {
        workflow.savePack(pack)
        try { workflow.savePack(pack.copy(bank = pack.bank.copy(id = "other", senders = listOf(" demo "))));fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, workflow.load("demo").banks.size)
    }
    @Test fun annotationsCanDescribeUnroutedMessagesWithoutInventingAFamily() = runBlocking {
        workflow.savePack(pack)
        val expected = LabJson.format.encodeToString(ExpectedResult("REVIEW_REQUIRED", LabFacts()))
        workflow.addSample("demo", "OTHER", "شراء بمبلغ 100 SAR", "2026-10-01", expected)
        val run = workflow.run("demo", "2026-10-01", "2026-10-01")
        assertEquals(1, workflow.viewResults(run).matched)
    }
}
