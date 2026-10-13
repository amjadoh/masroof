package com.baraa.masroof.lab.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.data.*
import com.baraa.masroof.lab.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabSmsImportTest {
    private lateinit var context: Context
    private lateinit var db: LabDatabase
    private lateinit var workflow: LabWorkflow
    private var messages = emptyList<LabInboxMessage>()
    private var failure: Exception? = null
    private var calls = 0
    private val window = LabDateWindow.of("2026-10-01", "2026-10-01", "Asia/Riyadh")
    private val pack = RulePack(bank = BankProfile("demo", "تجريبي", listOf("DEMO")), version = 1,
        rules = listOf(RuleSpec("purchase", "PURCHASE", listOf("شراء"))))
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext(); context.deleteDatabase(LabDatabase.FILE_NAME)
        db = LabDatabase.open(context)
        workflow = LabWorkflow(db, object : LabInboxSource {
            override suspend fun read(senders: Set<String>, fromInclusive: Long, toExclusive: Long): LabInboxRead {
                calls++
                assertEquals(setOf("DEMO"), senders)
                assertEquals(window.fromInclusive, fromInclusive); assertEquals(window.toExclusive, toExclusive)
                failure?.let { throw it }
                return LabInboxRead(messages)
            }
        })
    }
    @After fun cleanup() { workflow.close(); context.deleteDatabase(LabDatabase.FILE_NAME); context.deleteDatabase("masroof.db") }
    private fun message(id: String, sender: String = " demo ", time: Long = window.fromInclusive) =
        LabInboxMessage(id, sender, "شراء بمبلغ 100 SAR\nالنص الأصلي", time)

    @Test fun exactRouting_inclusiveDates_dedup_andOriginalPayload() = runBlocking {
        workflow.savePack(pack)
        messages = listOf(message("1"), message("2", time = window.toExclusive - 1), message("3", "DEMO-other"),
            message("4", time = window.fromInclusive - 1), message("5", time = window.toExclusive))
        val first = workflow.importPhoneSms("demo", "2026-10-01", "2026-10-01")
        assertEquals(LabSmsImportResult(2, 0, 3), first)
        assertEquals(LabSmsImportResult(0, 2, 3), workflow.importPhoneSms("demo", "2026-10-01", "2026-10-01"))
        val rows = db.dao().samples("demo")
        assertEquals(2, rows.size); assertEquals(messages[0].body, rows[0].body)
        assertEquals(messages[0].sender, rows[0].sender); assertEquals(window.fromInclusive, rows[0].receivedAt)
        assertTrue(rows.all { it.expectedJson == null })
        assertEquals("SUCCESS", workflow.previewSaved("demo", rows[0].id).status)
        val run = workflow.run("demo", "2026-10-01", "2026-10-01")
        assertEquals(2, workflow.viewResults(run).unlabeled); assertEquals(0, workflow.viewResults(run).labeled)
    }
    @Test fun distinctProviderIdsAreNotCollapsed_andOldDataAndProductionSurvive() = runBlocking {
        val production = context.openOrCreateDatabase("masroof.db", Context.MODE_PRIVATE, null)
        try {
            production.execSQL("CREATE TABLE sentinel (value TEXT)"); production.execSQL("INSERT INTO sentinel VALUES ('keep')")
            workflow.savePack(pack); workflow.addSample("demo", "DEMO", "شراء بمبلغ 10 SAR", "2026-10-01", null)
            val oldRun = workflow.run("demo", "2026-10-01", "2026-10-01")
            messages = listOf(message("1"), message("2"))
            assertEquals(2, workflow.importPhoneSms("demo", "2026-10-01", "2026-10-01").imported)
            assertEquals(3, workflow.load("demo").samples.size); assertEquals(1, workflow.results(oldRun).size)
            workflow.reset()
            production.rawQuery("SELECT value FROM sentinel", null).use { assertTrue(it.moveToFirst()); assertEquals("keep", it.getString(0)) }
        } finally { production.close() }
    }
    @Test fun deniedPermission_providerFailure_andOversizedRangeNeverPartiallyCommit() = runBlocking {
        workflow.savePack(pack)
        for (problem in listOf(SecurityException("denied"), IllegalStateException("provider failed"))) {
            failure = problem
            try { workflow.importPhoneSms("demo", "2026-10-01", "2026-10-01"); fail() } catch (_: IllegalStateException) { }
            assertTrue(db.dao().samples("demo").isEmpty())
        }
        failure = null; messages = (1..1001).map { message(it.toString()) }
        try { workflow.importPhoneSms("demo", "2026-10-01", "2026-10-01"); fail() } catch (_: IllegalArgumentException) { }
        assertTrue(db.dao().samples("demo").isEmpty())
    }
    @Test fun reversedDatesFailBeforeInboxAccess() = runBlocking {
        workflow.savePack(pack)
        try { workflow.importPhoneSms("demo", "2026-10-02", "2026-10-01"); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
    }
    @Test fun bankEditedDuringReadRejectsStaleImport() = runBlocking {
        workflow.savePack(pack)
        val concurrent = LabWorkflow(db, object : LabInboxSource {
            override suspend fun read(senders: Set<String>, fromInclusive: Long, toExclusive: Long): LabInboxRead {
                workflow.savePack(pack.copy(bank = pack.bank.copy(senders = listOf("NEW"))))
                return LabInboxRead(listOf(message("1")))
            }
        })
        try { concurrent.importPhoneSms("demo", "2026-10-01", "2026-10-01"); fail() } catch (_: IllegalArgumentException) { }
        assertTrue(db.dao().samples("demo").isEmpty())
    }
    @Test fun daylightSavingUsesCalendarMidnights() {
        val spring = LabDateWindow.of("2026-03-08", "2026-03-08", "America/New_York")
        assertEquals(23 * 60 * 60 * 1000L, spring.toExclusive - spring.fromInclusive)
        assertTrue(spring.contains(spring.fromInclusive)); assertTrue(spring.contains(spring.toExclusive - 1))
        assertFalse(spring.contains(spring.toExclusive))
    }
}
