package com.baraa.masroof.lab.beta.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.verification.SyntheticFinancialHistory
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Constructed v8 synthetic fixture, not an old production APK or a database downgrade API. */
class BetaRichLegacyArchiveTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)
    @Test fun encryptedEightPreservesFinancialHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        lateinit var fixture: JSONObject
        BetaContainer(context, com.baraa.masroof.lab.beta.application.evidence.LabEvidenceComparer(com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService.independent())).use { fixture = SyntheticFinancialHistory.seed(it, 80) }
        val expected = SyntheticFinancialHistory.fingerprint(context)
        val source = File(context.cacheDir, "synthetic_current.db")
        context.getDatabasePath(BetaDatabase.NAME).copyTo(source, overwrite = true)
        check(context.deleteDatabase(BetaDatabase.NAME))
        val priorTables = mutableListOf<String>()
        helper.createDatabase(BetaDatabase.NAME, 8).use { old ->
            old.execSQL("PRAGMA foreign_keys=OFF")
            old.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'beta_%' ORDER BY name").use {
                while (it.moveToNext()) priorTables += it.getString(0)
            }
            SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { current ->
                old.beginTransaction()
                try {
                    priorTables.forEach { table ->
                        check(table.matches(Regex("beta_[a-z_]+")))
                        val columns = mutableListOf<String>()
                        old.query("PRAGMA table_info($table)").use { while (it.moveToNext()) columns += it.getString(1) }
                        check(columns.all { it.matches(Regex("[a-zA-Z_][a-zA-Z_0-9]*")) })
                        current.rawQuery("SELECT ${columns.joinToString(",")} FROM $table ORDER BY rowid", null).use { rows ->
                            while (rows.moveToNext()) {
                                val values = Array<Any?>(rows.columnCount) { index -> when (rows.getType(index)) {
                                    Cursor.FIELD_TYPE_NULL -> null
                                    Cursor.FIELD_TYPE_INTEGER -> rows.getLong(index)
                                    Cursor.FIELD_TYPE_FLOAT -> rows.getDouble(index)
                                    Cursor.FIELD_TYPE_BLOB -> rows.getBlob(index)
                                    else -> rows.getString(index)
                                } }
                                old.execSQL("INSERT INTO $table (${columns.joinToString(",")}) VALUES (${columns.joinToString(",") { "?" }})", values)
                            }
                        }
                    }
                    old.setTransactionSuccessful()
                } finally { old.endTransaction() }
            }
            old.execSQL("PRAGMA foreign_keys=ON")
            old.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
        val recovery = BetaBackupRecovery(context)
        val preferences = context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE).all.mapValues { it.value as String }
        recovery.publish(context.getDatabasePath(BetaDatabase.NAME), preferences)
        val password = "Synthetic old schema password".toCharArray()
        try {
            val bytes = ByteArrayOutputStream().also { BetaEncryptedArchive().write(recovery.backupFile().parentFile!!, it, password) }.toByteArray()
            check(context.deleteDatabase(BetaDatabase.NAME))
            BetaContainer(context).use { beta ->
                beta.putMarker("current", "synthetic"); beta.setTheme("LIGHT")
                assertTrue(beta.importArchive(bytes.inputStream(), password))
                SyntheticFinancialHistory.verify(beta, fixture)
            }
            val restored = SyntheticFinancialHistory.fingerprint(context)
            assertEquals(BetaDatabase.VERSION, restored.getInt("schema"))
            priorTables.filterNot { it == "beta_dashboard_evidence_facts" }.forEach { table ->
                assertEquals(table, expected.getJSONObject(table).toString(), restored.getJSONObject(table).toString())
            }
            assertEquals(expected.getJSONObject("preferences").toString(), restored.getJSONObject("preferences").toString())
            SQLiteDatabase.openDatabase(recovery.backupFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { assertEquals(8, it.version) }
        } finally {
            password.fill('\u0000')
            listOf("", "-wal", "-shm", "-journal").forEach { File(source.path + it).delete() }
        }
    }
}
