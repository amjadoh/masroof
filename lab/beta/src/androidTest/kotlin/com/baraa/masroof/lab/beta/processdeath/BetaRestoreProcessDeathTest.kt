package com.baraa.masroof.lab.beta.processdeath

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaBackupRecovery
import com.baraa.masroof.lab.beta.BetaContainer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** The existing external lifecycle job kills the actual UID while the restore thread waits. */
class BetaRestoreProcessDeathTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun arm(stage: BetaBackupRecovery.Stage) {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta ->
            beta.putMarker("backup", "synthetic"); beta.putPreferenceProbe("BACKUP"); beta.backup()
            beta.putMarker("current", "synthetic"); beta.putPreferenceProbe("CURRENT")
        }
        File(context.filesDir, "beta_restore_test_pid").writeText(Process.myPid().toString())
        val entered = CountDownLatch(1)
        Thread {
            BetaBackupRecovery(context) { if (it == stage) { entered.countDown(); CountDownLatch(1).await() } }.restore()
        }.apply { isDaemon = true; start() }
        assertTrue(entered.await(20, TimeUnit.SECONDS))
    }
    private fun resume(committed: Boolean) {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("betaResume"))
        assertNotEquals(File(context.filesDir, "beta_restore_test_pid").readText(), Process.myPid().toString())
        BetaContainer(context).use { beta ->
            assertEquals(if (committed) 1 else 2, beta.markerCount())
            assertEquals(if (committed) "BACKUP" else "CURRENT", beta.preferenceProbe())
            assertTrue(beta.settings().backupAvailable)
            val entries = setOf("database.db", "preferences.json", "manifest.json")
            assertEquals(entries, beta.backupDatabaseFile().parentFile!!.list()!!.toSet())
            assertTrue(beta.restore())
            assertEquals(1, beta.markerCount()); assertEquals("BACKUP", beta.preferenceProbe())
            assertEquals(entries, beta.backupDatabaseFile().parentFile!!.list()!!.toSet())
        }
    }
    @Test fun armAfterDatabaseInstall() = arm(BetaBackupRecovery.Stage.DATABASE_INSTALLED)
    @Test fun resumeAfterDatabaseInstall() = resume(false)
    @Test fun armAfterPreferencesInstall() = arm(BetaBackupRecovery.Stage.PREFERENCES_INSTALLED)
    @Test fun resumeAfterPreferencesInstall() = resume(false)
    @Test fun armAfterCommit() = arm(BetaBackupRecovery.Stage.COMMITTED)
    @Test fun resumeAfterCommit() = resume(true)

    @Test fun armCommittedWal() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta -> beta.putMarker("backup", "synthetic"); beta.putPreferenceProbe("BACKUP"); beta.backup(); beta.putMarker("current", "synthetic"); beta.putPreferenceProbe("CURRENT") }
        retainedWalDatabase = android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(com.baraa.masroof.lab.beta.BetaEnvironment.DATABASE_NAME).absolutePath, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE or android.database.sqlite.SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
        ).also { db ->
            db.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { assertTrue(it.moveToFirst()) }
            db.execSQL("INSERT INTO beta_shell_marker (id,note) VALUES ('wal-only','synthetic committed WAL')")
        }
        assertTrue(File(context.getDatabasePath(com.baraa.masroof.lab.beta.BetaEnvironment.DATABASE_NAME).path + "-wal").length() > 0)
        File(context.filesDir, "beta_restore_test_pid").writeText(Process.myPid().toString())
    }

    @Test fun armRestoreWithCommittedWal() {
        assertNotEquals(File(context.filesDir, "beta_restore_test_pid").readText(), Process.myPid().toString())
        assertTrue(File(context.getDatabasePath(com.baraa.masroof.lab.beta.BetaEnvironment.DATABASE_NAME).path + "-wal").length() > 0)
        File(context.filesDir, "beta_restore_test_pid").writeText(Process.myPid().toString())
        val entered = CountDownLatch(1)
        Thread {
            BetaBackupRecovery(context) { if (it == BetaBackupRecovery.Stage.DATABASE_INSTALLED) { entered.countDown(); CountDownLatch(1).await() } }.restore()
        }.apply { isDaemon = true; start() }
        assertTrue(entered.await(20, TimeUnit.SECONDS))
    }

    @Test fun resumeRestoreWithCommittedWal() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("betaResume"))
        assertNotEquals(File(context.filesDir, "beta_restore_test_pid").readText(), Process.myPid().toString())
        BetaContainer(context).use { beta ->
            assertEquals(3, beta.markerCount()); assertEquals("CURRENT", beta.preferenceProbe())
            assertEquals(setOf("database.db", "preferences.json", "manifest.json"), beta.backupDatabaseFile().parentFile!!.list()!!.toSet())
        }
    }

    companion object {
        // Keep the native connection alive until the external job kills the UID, without checkpointing.
        private var retainedWalDatabase: android.database.sqlite.SQLiteDatabase? = null
    }
}
