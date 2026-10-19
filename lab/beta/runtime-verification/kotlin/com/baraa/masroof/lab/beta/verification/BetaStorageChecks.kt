package com.baraa.masroof.lab.beta.verification

import android.content.Context
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.beta.application.BetaStartState
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*

/** Isolated test-context files only; shared between Robolectric and the native emulator. */
object BetaStorageChecks {
    fun missingDatabaseRetainsSidecars(context: Context) = interruptedStorage(context, emptyMain = false)
    fun emptyDatabaseRetainsSidecars(context: Context) = interruptedStorage(context, emptyMain = true)

    fun missingAcknowledgedDatabaseRetainsBackup(context: Context) = interruptedStorage(context, emptyMain = false, includeSidecars = false)

    private fun interruptedStorage(context: Context, emptyMain: Boolean, includeSidecars: Boolean = true) {
        BetaContainer.resetOffline(context)
        try {
            val protected = BetaEnvironment.forbiddenDatabaseNames.flatMap { name ->
                listOf("", "-wal", "-shm", "-journal").map { suffix ->
                    context.getDatabasePath(name + suffix).apply {
                        parentFile?.mkdirs(); writeText("synthetic-protected-$name$suffix")
                    }
                }
            }
            BetaEnvironment.forbiddenPreferenceNames.forEach { name ->
                assertTrue(context.getSharedPreferences(name, 0).edit().putString("m3-sentinel", "synthetic-retained").commit())
            }
            val protectedHashes = protected.associateWith(::hash)
            BetaContainer(context).use { beta ->
                assertEquals(BetaStartState.NEW, beta.startState())
                beta.acknowledgeStart(); beta.putMarker("m3-storage", "synthetic-retained")
                beta.putPreferenceProbe("m3-retained"); beta.backup()
            }
            val prefs = context.getSharedPreferences(BetaEnvironment.PREFS_NAME, 0)
            // Cover existing data from before entry acknowledgement as well as acknowledged history.
            if (includeSidecars) assertTrue(prefs.edit().remove(BetaContainer.START_KEY).commit())
            val beforePrefs = prefs.all.toMap()
            val recovery = File(context.filesDir, "beta_recovery_v2")
            val beforeRecovery = hashes(recovery)
            val live = context.getDatabasePath(BetaEnvironment.DATABASE_NAME)
            assertTrue(live.delete()) // Simulated interruption, in isolated fixture only.
            if (emptyMain) live.writeBytes(byteArrayOf())
            val sidecars = (if (includeSidecars) listOf("-wal", "-shm", "-journal") else emptyList()).map { suffix ->
                File(live.path + suffix).apply { writeText("synthetic-retained-$suffix") }
            }
            val beforeSidecars = sidecars.associateWith(::hash)
            repeat(2) {
                assertThrows(Exception::class.java) { BetaContainer(context).use { it.startState() } }
                assertEquals(emptyMain, live.exists())
                if (emptyMain) assertEquals(0L, live.length())
                assertEquals(beforeSidecars, sidecars.associateWith(::hash))
                assertEquals(beforePrefs, prefs.all)
                assertEquals(beforeRecovery, hashes(recovery))
                assertEquals(protectedHashes, protected.associateWith(::hash))
                BetaEnvironment.forbiddenPreferenceNames.forEach { name ->
                    assertEquals("synthetic-retained", context.getSharedPreferences(name, 0).getString("m3-sentinel", null))
                }
            }
            // Recovery is explicit, not an automatic replacement during entry.
            assertTrue(BetaContainer.restoreOffline(context))
            BetaContainer(context).use { beta ->
                assertEquals(BetaStartState.READY, beta.startState())
                assertEquals(1, beta.markerCount()); assertEquals("m3-retained", beta.preferenceProbe())
            }
            assertEquals(protectedHashes, protected.associateWith(::hash))
        } finally { BetaContainer.resetOffline(context) }
    }

    private fun hashes(root: File) = root.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(root).invariantSeparatorsPath to hash(it) }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
}
