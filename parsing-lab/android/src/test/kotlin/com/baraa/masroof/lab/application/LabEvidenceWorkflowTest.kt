package com.baraa.masroof.lab.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.data.LabDatabase
import com.baraa.masroof.lab.engine.*
import com.baraa.masroof.lab.engine.evidence.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabEvidenceWorkflowTest {
    private lateinit var context: Context
    private lateinit var db: LabDatabase
    private lateinit var workflow: LabWorkflow
    private val pack = RulePack(bank = BankProfile("demo", "Demo", listOf("DEMO")), version = 1, rules = emptyList())
    @Before fun setup() { context = ApplicationProvider.getApplicationContext();context.deleteDatabase(LabDatabase.FILE_NAME);db = LabDatabase.open(context);workflow = LabWorkflow(db) }
    @After fun close() { db.close();context.deleteDatabase(LabDatabase.FILE_NAME);context.deleteDatabase("masroof.db") }
    @Test fun pairedResultsAndKnowledgeAreImmutableAndSurviveRestart() = runBlocking {
        workflow.savePack(pack)
        workflow.addSample("demo", "DEMO", "شراء\nبمبلغ: 10 SAR\nبطاقة: 1234", "2026-10-01", null)
        val first = workflow.compare("demo", "2026-10-01", "2026-10-01")
        val saved = workflow.comparisonResults(first)
        val sample = workflow.load("demo").samples.single()
        workflow.annotate("demo", sample.id, ExpectedResult("SUCCESS", LabFacts(family = "PURCHASE", amount = "10", currency = "SAR", direction = "OUTGOING"), setOf("network")))
        val initial = workflow.evidenceKnowledge()
        workflow.saveKnowledge(initial.copy(rules = initial.rules.map { if (it.concept == "field.amount") it.copy(phrases = listOf("قيمة جديدة")) else it }))
        try { workflow.saveKnowledge(initial);fail("accepted stale draft") } catch (_: IllegalArgumentException) { }
        val second = workflow.compare("demo", "2026-10-01", "2026-10-01")
        assertEquals("SUCCESS", saved.single().shared.status)
        assertEquals("SUCCESS", saved.single().evidence.outcome.status)
        assertEquals("REVIEW_REQUIRED", workflow.comparisonResults(second).single().evidence.outcome.status)
        assertNull(saved.single().expected)
        assertNotNull(workflow.comparisonResults(second).single().expected)
        assertEquals(saved, workflow.comparisonResults(first))
        workflow.close();db = LabDatabase.open(context);workflow = LabWorkflow(db)
        assertEquals(saved, workflow.comparisonResults(first))
        assertEquals(2, workflow.evidenceKnowledge().version)
        assertEquals(2, workflow.comparisons("demo").size)
    }
    @Test fun previewShowsRegressionAndDoesNotSaveOrTouchOtherDatabase() = runBlocking {
        val sentinel = context.openOrCreateDatabase("masroof.db", Context.MODE_PRIVATE, null)
        sentinel.execSQL("CREATE TABLE sentinel (value TEXT)");sentinel.execSQL("INSERT INTO sentinel VALUES ('keep')")
        try {
            workflow.savePack(pack)
            val expected = ExpectedResult("SUCCESS", LabFacts("PURCHASE", "OUTGOING", "10", "SAR"))
            workflow.addSample("demo", "DEMO", "شراء بمبلغ 10 SAR", "2026-10-01", LabJson.format.encodeToString(expected))
            workflow.savePack(pack.copy(bank = BankProfile("other", "Other", listOf("OTHER"))))
            workflow.addSample("other", "OTHER", "شراء بمبلغ 10 SAR", "2026-10-01", LabJson.format.encodeToString(expected))
            val initial = workflow.evidenceKnowledge()
            val draft = initial.copy(rules = initial.rules.map { if (it.concept == "field.amount") it.copy(phrases = listOf("قيمة")) else it })
            val preview = workflow.previewKnowledge("demo", draft)
            assertEquals(2, preview.regressed.size);assertEquals(2, preview.changed);assertEquals(2, preview.testedBanks)
            assertEquals(initial, workflow.evidenceKnowledge());assertTrue(workflow.comparisons("demo").isEmpty())
            workflow.reset()
            assertTrue(db.dao().comparisons("demo").isEmpty())
            assertNull(db.dao().evidenceKnowledge())
            sentinel.rawQuery("SELECT value FROM sentinel", null).use { assertTrue(it.moveToFirst());assertEquals("keep", it.getString(0)) }
        } finally { sentinel.close() }
    }
    @Test fun cancellationAndEmptyWindowDoNotSaveCompletedComparison() = runBlocking {
        workflow.savePack(pack)
        try { workflow.compare("demo", "2026-10-01", "2026-10-01");fail() } catch (_: IllegalArgumentException) { }
        workflow.addSample("demo", "DEMO", "شراء بمبلغ 10 SAR", "2026-10-01", null)
        val job = launch(start = CoroutineStart.LAZY) { workflow.compare("demo", "2026-10-01", "2026-10-01") }
        job.cancel();job.join()
        assertTrue(workflow.comparisons("demo").isEmpty())
    }
    @Test fun migrationFromVersionTwoPreservesLegacyRowsAndAllowsComparison() = runBlocking {
        db.close();context.deleteDatabase(LabDatabase.FILE_NAME)
        val old = context.openOrCreateDatabase(LabDatabase.FILE_NAME, Context.MODE_PRIVATE, null)
        val schema = Json.parseToJsonElement(File("schemas/com.baraa.masroof.lab.data.LabDatabase/2.json").readText()).jsonObject.getValue("database").jsonObject
        schema.getValue("entities").jsonArray.forEach { element ->
            val entity = element.jsonObject;val name = entity.getValue("tableName").jsonPrimitive.content
            old.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
            entity.getValue("indices").jsonArray.forEach { old.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name)) }
        }
        val packJson = LabJson.format.encodeToString(pack)
        old.execSQL("INSERT INTO lab_banks VALUES ('demo','Demo',1,?)", arrayOf(packJson))
        old.execSQL("INSERT INTO lab_samples VALUES ('legacy','demo','DEMO','شراء بمبلغ 10 SAR',1790845200000,NULL)")
        old.execSQL("INSERT INTO lab_shared_rule_versions VALUES (7,?,1)", arrayOf(LabJson.format.encodeToString(SharedRuleDefaults.create().copy(version = 7))))
        old.version = 2;old.close()
        db = LabDatabase.open(context);workflow = LabWorkflow(db)
        assertEquals("legacy", workflow.load("demo").samples.single().id)
        assertEquals(7, workflow.sharedLibrary().version)
        val id = workflow.compare("demo", "2026-01-01", "2026-12-31")
        assertEquals(1, workflow.comparisonResults(id).size)
        assertEquals(packJson, db.dao().banks().single().packJson)
    }
}
