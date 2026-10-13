package com.baraa.masroof.lab.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.data.LabDatabase
import com.baraa.masroof.lab.engine.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabSharedMigrationTest {
    @Test fun migrationPreservesVersionOneBanksSamplesRunsAndResults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(LabDatabase.FILE_NAME)
        val old = context.openOrCreateDatabase(LabDatabase.FILE_NAME, Context.MODE_PRIVATE, null)
        val schema = Json.parseToJsonElement(File("schemas/com.baraa.masroof.lab.data.LabDatabase/1.json").readText()).jsonObject.getValue("database").jsonObject
        schema.getValue("entities").jsonArray.forEach { entity ->
            val obj = entity.jsonObject
            val name = obj.getValue("tableName").jsonPrimitive.content
            old.execSQL(obj.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
            obj.getValue("indices").jsonArray.forEach { index ->
                old.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
            }
        }
        val pack = LabJson.format.encodeToString(RulePack(bank = BankProfile("old", "Old", listOf("OLD")), version = 1, rules = emptyList()))
        old.execSQL("INSERT INTO lab_banks VALUES ('old','Old',1,?)", arrayOf(pack))
        old.execSQL("INSERT INTO lab_rule_versions VALUES ('old',1,?,1)", arrayOf(pack))
        old.execSQL("INSERT INTO lab_samples VALUES ('sample','old','OLD','saved message',1,NULL)")
        old.execSQL("INSERT INTO lab_runs VALUES ('run','old',1,1,'2026-10-01','2026-10-01',?,1)", arrayOf(pack))
        val outcome = """{"status":"REVIEW_REQUIRED"}"""
        old.execSQL("INSERT INTO lab_run_results VALUES ('run','sample','OLD','saved message',NULL,?,NULL,NULL)", arrayOf(outcome))
        old.version = 1; old.close()
        val db = LabDatabase.open(context)
        try {
            val workflow = LabWorkflow(db)
            val snapshot = workflow.load("old")
            assertEquals("saved message", snapshot.samples.single().body)
            assertEquals("run", snapshot.runs.single().id)
            assertEquals(outcome, workflow.results("run").single().outcomeJson)
            assertEquals(pack, db.dao().banks().single().packJson)
            assertEquals(1, workflow.versions("old").size)
            assertTrue(snapshot.library.rules.isNotEmpty())
        } finally { db.close();context.deleteDatabase(LabDatabase.FILE_NAME) }
    }
}
