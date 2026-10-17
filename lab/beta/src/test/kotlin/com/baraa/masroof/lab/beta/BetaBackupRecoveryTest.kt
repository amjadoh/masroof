package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BetaBackupRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { BetaContainer.resetOffline(context) }

    @Test fun jsonPreferencesRoundTripSeparatorsNewlinesAndTheme() {
        BetaContainer(context).use { beta ->
            beta.putMarker("before", "synthetic")
            beta.putPreferenceProbe("a=b\nc=د")
            beta.setTheme("DARK"); beta.backup()
            beta.putMarker("after", "synthetic"); beta.putPreferenceProbe("later"); beta.setTheme("LIGHT")
            assertTrue(beta.restore())
            assertEquals(1, beta.markerCount()); assertEquals("a=b\nc=د", beta.preferenceProbe())
            assertEquals("DARK", beta.settings().theme)
        }
    }

    @Test fun corruptDatabaseAndPreferencesAreRejectedWithoutChangingCurrent() {
        for (entry in listOf("database.db", "preferences.json", "manifest.json")) {
            BetaContainer.resetOffline(context)
            BetaContainer(context).use { beta ->
                beta.putMarker("before", "synthetic"); beta.putPreferenceProbe("A"); beta.backup()
                beta.putMarker("after", "synthetic"); beta.putPreferenceProbe("B")
                File(beta.backupDatabaseFile().parentFile, entry).appendText("broken")
                assertThrows(Exception::class.java) { beta.restore() }
                assertEquals(2, beta.markerCount()); assertEquals("B", beta.preferenceProbe())
            }
        }
    }

    @Test fun lowStorageDuringNewBackupKeepsPreviousPublishedBackup() {
        BetaContainer(context).use { beta -> beta.putMarker("first", "synthetic"); beta.backup(); beta.putMarker("second", "synthetic") }
        assertThrows(IOException::class.java) { BetaBackupRecovery(context) { if (it == BetaBackupRecovery.Stage.BACKUP_STAGED) throw IOException("ENOSPC synthetic") }.backup() }
        BetaContainer(context).use { beta -> assertTrue(beta.restore()); assertEquals(1, beta.markerCount()) }
    }

    @Test fun exceptionsAtEveryPrecommitBoundaryRollBackDatabaseAndPreferences() {
        for (stage in listOf(BetaBackupRecovery.Stage.RESTORE_VALIDATED, BetaBackupRecovery.Stage.JOURNALED, BetaBackupRecovery.Stage.DATABASE_INSTALLED, BetaBackupRecovery.Stage.PREFERENCES_INSTALLED)) {
            seedOriginalAndBackup()
            assertThrows(IOException::class.java) { BetaBackupRecovery(context) { if (it == stage) throw IOException("synthetic storage interruption") }.restore() }
            assertOriginal()
        }
    }

    @Test fun processDeathAfterDatabaseOrPreferencesInstallationRollsBackOnNextContainerOpen() {
        for (stage in listOf(BetaBackupRecovery.Stage.JOURNALED, BetaBackupRecovery.Stage.DATABASE_INSTALLED, BetaBackupRecovery.Stage.PREFERENCES_INSTALLED)) {
            seedOriginalAndBackup()
            assertThrows(SimulatedDeath::class.java) { BetaBackupRecovery(context) { if (it == stage) throw SimulatedDeath() }.restore() }
            // No finally/catch rollback ran. The new composition root must recover before Room opens.
            assertOriginal()
        }
    }

    @Test fun committedRestoreSurvivesDeathBeforeJournalCleanup() {
        seedOriginalAndBackup()
        assertThrows(SimulatedDeath::class.java) { BetaBackupRecovery(context) { if (it == BetaBackupRecovery.Stage.COMMITTED) throw SimulatedDeath() }.restore() }
        BetaContainer(context).use { beta -> assertEquals(1, beta.markerCount()); assertEquals("BACKUP", beta.preferenceProbe()) }
    }

    @Test fun legacyUnsealedFilesAreNotAutomaticallyTrustedOrInstalled() {
        BetaContainer(context).use { it.putMarker("keep", "synthetic") }
        File(context.filesDir, BetaEnvironment.BACKUP_DATABASE_FILE_NAME).writeText("untrusted database")
        File(context.filesDir, BetaEnvironment.BACKUP_PREFS_FILE_NAME).writeText("x=y")
        assertFalse(BetaContainer.restoreOffline(context))
        BetaContainer(context).use { assertEquals(1, it.markerCount()) }
    }

    @Test fun malformedRecoveryJournalFailsClosedAndPreservesFilesForRetry() {
        BetaContainer(context).use { it.putMarker("keep", "synthetic") }
        File(context.filesDir, "beta_recovery_v1/restore_journal").writeText("broken")
        assertThrows(Exception::class.java) { BetaContainer(context) }
        assertTrue(context.getDatabasePath(BetaEnvironment.DATABASE_NAME).isFile)
        File(context.filesDir, "beta_recovery_v1/restore_journal").delete()
        BetaContainer(context).use { assertEquals(1, it.markerCount()) }
    }

    @Test fun currentVersionWithUnchangedRoomIdentityAndMissingTableIsRejected() {
        seedOriginalAndBackup()
        val file = BetaBackupRecovery(context).backupFile()
        android.database.sqlite.SQLiteDatabase.openDatabase(file.absolutePath, null, 0).use { db ->
            db.execSQL("DROP TABLE beta_shell_marker")
        }
        val manifest = File(file.parentFile, "manifest.json")
        val metadata = org.json.JSONObject(manifest.readText())
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        manifest.writeText(metadata.put("databaseSha256", digest).toString())
        assertThrows(Exception::class.java) { BetaBackupRecovery(context).restore() }
        assertOriginal()
    }

    private fun seedOriginalAndBackup() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta -> beta.putMarker("backup", "synthetic"); beta.putPreferenceProbe("BACKUP"); beta.backup(); beta.putMarker("current", "synthetic"); beta.putPreferenceProbe("CURRENT") }
    }
    private fun assertOriginal() { BetaContainer(context).use { assertEquals(2, it.markerCount()); assertEquals("CURRENT", it.preferenceProbe()) } }
    private class SimulatedDeath : Error("synthetic process death")
}
