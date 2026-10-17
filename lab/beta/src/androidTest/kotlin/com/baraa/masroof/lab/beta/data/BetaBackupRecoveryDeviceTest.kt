package com.baraa.masroof.lab.beta.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Native Android WAL behavior is deliberately tested, not inferred from Robolectric. */
class BetaBackupRecoveryDeviceTest {
    @Test fun snapshotIsStandaloneAndReadOnlyValidationNeverCreatesWalSidecars() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta ->
            beta.putMarker("snapshot", "synthetic"); beta.putPreferenceProbe("BACKUP")
            beta.backup()
            val file = beta.backupDatabaseFile()
            val expected = setOf("database.db", "preferences.json", "manifest.json")
            assertEquals(expected, file.parentFile!!.list()!!.toSet())
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                database.rawQuery("PRAGMA journal_mode", null).use { assertTrue(it.moveToFirst()); assertEquals("delete", it.getString(0)) }
            }
            assertEquals(expected, file.parentFile!!.list()!!.toSet())
            beta.putMarker("current", "synthetic"); beta.putPreferenceProbe("CURRENT")
            assertTrue(beta.restore()); assertEquals(1, beta.markerCount()); assertEquals("BACKUP", beta.preferenceProbe())
            assertEquals(expected, file.parentFile!!.list()!!.toSet())
            File(file.parentFile, "preferences.json").appendText("bad")
            assertThrows(Exception::class.java) { beta.restore() }
            assertEquals(1, beta.markerCount()); assertEquals("BACKUP", beta.preferenceProbe())
        }
    }
}
