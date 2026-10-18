package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.room.util.TableInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.application.betaDashboardControls
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class BetaConfigMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @After fun cleanup() { BetaContainer.resetOffline(context) }

    @Test fun nineToTenPreservesAllPriorTables() {
        BetaContainer.resetOffline(context)
        val before = linkedMapOf<String, List<List<String?>>>()
        helper.createDatabase(BetaDatabase.NAME, 9).apply {
            execSQL("INSERT INTO beta_shell_marker VALUES ('nine','preserved')")
            execSQL("INSERT INTO beta_asset VALUES ('BETA','BANK_ALJAZIRA','owned','ACCOUNT','3001',NULL,NULL)")
            execSQL("INSERT INTO beta_bank_selection VALUES ('BANK_ALJAZIRA','ENABLED')")
            query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'beta_%' ORDER BY name").use {
                while (it.moveToNext()) before[it.getString(0)] = emptyList()
            }
            assertEquals(20, before.size)
            for (table in before.keys.toList()) before[table] = rows(this, table)
            close()
        }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 10, true).apply {
            for ((table, values) in before) assertEquals(table, values, rows(this, table))
            query("SELECT COUNT(*) FROM beta_dashboard_configuration").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            assertFresh(this); close()
        }
    }

    @Test fun oneToTenKeepsUnknownConfiguration() {
        BetaContainer.resetOffline(context)
        helper.createDatabase(BetaDatabase.NAME, 1).apply { execSQL("INSERT INTO beta_shell_marker VALUES ('one','preserved')"); close() }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 10, true).apply {
            query("SELECT note FROM beta_shell_marker").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
            query("SELECT COUNT(*) FROM beta_dashboard_configuration").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            assertFresh(this); close()
        }
    }

    @Test fun encryptedNineRestoresThroughStrictSchemaValidation() = runBlocking {
        BetaContainer.resetOffline(context)
        helper.createDatabase(BetaDatabase.NAME, 9).apply { execSQL("INSERT INTO beta_shell_marker VALUES ('nine','synthetic archive')"); close() }
        val recovery = BetaBackupRecovery(context)
        recovery.publish(context.getDatabasePath(BetaDatabase.NAME), mapOf("beta_ui_theme" to "DARK"))
        val password = "Synthetic schema nine archive".toCharArray()
        try {
            val archive = ByteArrayOutputStream().also { BetaEncryptedArchive().write(recovery.backupFile().parentFile!!, it, password) }.toByteArray()
            BetaContainer(context).use { beta ->
                beta.putMarker("current", "synthetic")
                assertEquals(2, beta.markerCount())
                assertTrue(beta.importArchive(archive.inputStream(), password))
                assertEquals(1, beta.markerCount())
                assertEquals("DARK", beta.settings().theme)
                assertEquals(0, betaDashboardControls(beta).read().revision)
            }
            val migrated = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
            try {
                assertEquals(11, migrated.openHelper.readableDatabase.version)
                assertFresh(migrated.openHelper.readableDatabase, 11)
            } finally { migrated.close() }
            android.database.sqlite.SQLiteDatabase.openDatabase(recovery.backupFile().absolutePath, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { assertEquals(9, it.version) }
        } finally { password.fill('\u0000') }
    }

    private fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> = buildList {
        db.query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
            add(cursor.columnNames.toList())
            while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) })
        }
    }
    private fun assertFresh(migrated: SupportSQLiteDatabase, version: Int = 10) {
        val name = "beta-schema-ten-reference.db"
        context.deleteDatabase(name)
        val fresh = helper.createDatabase(name, version)
        try {
            val actual = fresh
            val tables = buildList { actual.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'beta_%' ORDER BY name").use {
                while (it.moveToNext()) add(it.getString(0))
            } }
            assertEquals(if (version == 10) 21 else 22, tables.size)
            for (table in tables) assertEquals(table, TableInfo.read(actual, table), TableInfo.read(migrated, table))
            migrated.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { fresh.close(); context.deleteDatabase(name) }
    }
}
