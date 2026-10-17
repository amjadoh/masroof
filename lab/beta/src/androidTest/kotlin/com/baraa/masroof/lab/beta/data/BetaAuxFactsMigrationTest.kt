package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaAuxFactsMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @After fun cleanup() { BetaContainer.resetOffline(context) }

    @Test fun eightToNinePreservesPriorTablesAndUnknownAuxFacts() {
        BetaContainer.resetOffline(context)
        val before = linkedMapOf<String, List<String>>()
        val columns = linkedMapOf<String, String>()
        helper.createDatabase(BetaDatabase.NAME, 8).apply {
            execSQL("INSERT INTO beta_shell_marker VALUES ('seven','preserved')")
            execSQL("INSERT INTO beta_asset VALUES ('BETA','BANK_ALJAZIRA','owned','ACCOUNT','1234',NULL,NULL)")
            execSQL("INSERT INTO beta_bank_selection VALUES ('BANK_ALJAZIRA','ENABLED')")
            execSQL("INSERT INTO beta_raw_evidence VALUES ('saved','namespace','provider','EXPLICIT_SAMPLE','fixture','synthetic','2026-10-17T00:00:00Z','USER_SUPPLIED','2026-10-17T00:00:00Z','route','[]','[]')")
            execSQL("INSERT INTO beta_financial_adoption VALUES ('saved','evidence:saved','BANK_ALJAZIRA','archive','REVIEW','held','opaque',2,3,'request',4)")
            execSQL("INSERT INTO beta_financial_approval_retry VALUES ('saved','exact request')")
            execSQL("INSERT INTO beta_dashboard_evidence_facts VALUES ('saved','evidence:saved','BANK_ALJAZIRA','archive',4,2,3,'opaque','2026-10-17T00:00:00Z',1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)")
            query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'beta_%' ORDER BY name").use { cursor ->
                while (cursor.moveToNext()) before[cursor.getString(0)] = emptyList()
            }
            for (table in before.keys.toList()) before[table] = buildList {
                query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
                    columns[table] = cursor.columnNames.joinToString(",") { "`$it`" }
                    add(cursor.columnNames.joinToString("|"))
                    while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") {
                        if (cursor.isNull(it)) "<NULL>" else cursor.getString(it) })
                }
            }
            assertEquals(19, before.size); close()
        }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 9, true).apply {
            for ((table, expected) in before) {
                val actual = buildList {
                    query("SELECT ${columns.getValue(table)} FROM $table ORDER BY rowid").use { cursor ->
                        add(cursor.columnNames.joinToString("|"))
                        while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") {
                            if (cursor.isNull(it)) "<NULL>" else cursor.getString(it) })
                    }
                }
                assertEquals(table, expected, actual)
            }
            query("SELECT COUNT(*) FROM beta_dashboard_aux_facts").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            query("SELECT messageFamily FROM beta_dashboard_evidence_facts").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
            query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }; close()
        }
    }

    @Test fun earliestBetaReachesNineWithoutInventingFacts() {
        BetaContainer.resetOffline(context)
        helper.createDatabase(BetaDatabase.NAME, 1).apply { execSQL("INSERT INTO beta_shell_marker VALUES ('one','preserved')"); close() }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 9, true).apply {
            query("SELECT note FROM beta_shell_marker").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
            for (table in listOf("beta_ledger_current", "beta_dashboard_evidence_facts", "beta_dashboard_aux_facts"))
                query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            close()
        }
    }
}
