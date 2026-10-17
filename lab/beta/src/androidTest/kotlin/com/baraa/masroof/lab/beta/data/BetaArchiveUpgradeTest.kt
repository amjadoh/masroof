package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaBackupRecovery
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEncryptedArchive
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** M12 preparation: a synthetic old Beta file, never an installed production database. */
class BetaArchiveUpgradeTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)
    @Test fun earliestArchiveUpgradesOnlyInStage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        helper.createDatabase(BetaDatabase.NAME, 1).apply {
            execSQL("INSERT INTO beta_shell_marker VALUES ('old-beta','Synthetic v1 upgrade')"); close()
        }
        val recovery = BetaBackupRecovery(context)
        recovery.publish(context.getDatabasePath(BetaDatabase.NAME), mapOf("beta_ui_theme" to "DARK"))
        val password = "Synthetic upgrade password".toCharArray()
        try {
            val bytes = ByteArrayOutputStream().also { BetaEncryptedArchive().write(recovery.backupFile().parentFile!!, it, password) }.toByteArray()
            BetaContainer(context).use { beta ->
                beta.putMarker("current", "synthetic"); beta.setTheme("LIGHT")
                assertEquals(2, beta.markerCount())
                assertTrue(beta.importArchive(bytes.inputStream(), password))
                assertEquals(1, beta.markerCount()); assertEquals("DARK", beta.settings().theme)
            }
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(BetaDatabase.NAME).absolutePath, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                assertEquals(BetaDatabase.VERSION, db.version)
                db.rawQuery("PRAGMA foreign_key_check", null).use { assertEquals(0, it.count) }
                db.rawQuery("SELECT COUNT(*) FROM beta_ledger_current", null).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
            // The published historical snapshot is immutable and was not migrated in place.
            android.database.sqlite.SQLiteDatabase.openDatabase(recovery.backupFile().absolutePath, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { assertEquals(1, it.version) }
        } finally { password.fill('\u0000') }
    }
}
