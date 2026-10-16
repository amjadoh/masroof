package com.baraa.masroof.lab.beta

import com.baraa.masroof.lab.beta.application.BetaManualAssetWorkflow
import com.baraa.masroof.lab.beta.application.ManualAssetDraft
import com.baraa.masroof.lab.beta.application.BetaBankSelectionWorkflow
import com.baraa.masroof.lab.beta.data.RoomBetaBankSelectionStore
import com.baraa.masroof.lab.finance.value.BankId
import android.content.Context
import androidx.room.Room
import com.baraa.masroof.lab.beta.data.BetaAssetAlias
import com.baraa.masroof.lab.beta.data.BetaAssetRegistry
import com.baraa.masroof.lab.finance.asset.OwnedAsset
import com.baraa.masroof.lab.beta.data.BetaDatabase
import com.baraa.masroof.lab.beta.data.BetaShellMarkerEntity
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.runBlocking

/**
 * Beta composition root with an isolated explicit asset registry.
 *
 * Opens [BetaEnvironment.DATABASE_NAME] only. It does not import the production
 * composition root and it does not expose production repositories.
 */
class BetaContainer(
    context: Context,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val lock = ReentrantLock()
    private var closed = false
    private var database = openDatabase()
    private val preferences = appContext.getSharedPreferences(
        BetaEnvironment.PREFS_NAME,
        Context.MODE_PRIVATE,
    ).also { BetaEnvironment.requireBetaPreferenceName(BetaEnvironment.PREFS_NAME) }

    fun bankChoices() = lock.withLock {
        check(!closed)
        runBlocking { BetaBankSelectionWorkflow(RoomBetaBankSelectionStore(database)).choices() }
    }

    /** Explicit caller-supplied batch, comparison only; no inbox access or live scheduling. */
    fun importEvidence(request: com.baraa.masroof.lab.beta.application.evidence.EvidenceRequest,
        messages: List<com.baraa.masroof.lab.beta.application.evidence.EvidenceMessage>) = lock.withLock {
        check(!closed)
        runBlocking { evidenceWorkflow().importBatch(request, messages) }
    }

    fun reprocessEvidence(id: String) = lock.withLock {
        check(!closed)
        runBlocking { evidenceWorkflow().reprocess(id) }
    }

    fun retryEvidence(limit: Int = 100) = lock.withLock {
        check(!closed)
        runBlocking { evidenceWorkflow().retryPending(limit) }
    }

    fun evidenceSummary(id: String) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaEvidenceStore(database).summary(id) }
    }

    fun decideEvidence(id: String, expectedRevision: Long,
        decision: com.baraa.masroof.lab.beta.application.evidence.EvidenceReviewDecision) = lock.withLock {
        check(!closed)
        runBlocking { evidenceWorkflow().decide(id, expectedRevision, decision) }
    }

    private fun evidenceWorkflow() = com.baraa.masroof.lab.beta.application.evidence.ProcessBetaEvidence(
        com.baraa.masroof.lab.beta.data.RoomBetaEvidenceStore(database), RoomBetaBankSelectionStore(database),
        com.baraa.masroof.lab.beta.application.evidence.LabEvidenceRouter(),
        com.baraa.masroof.lab.beta.application.evidence.LabEvidenceComparer())

    fun setBankEnabled(bank: BankId, enabled: Boolean) = lock.withLock {
        check(!closed)
        runBlocking {
            val workflow = BetaBankSelectionWorkflow(RoomBetaBankSelectionStore(database))
            if (enabled) workflow.enable(bank) else workflow.disable(bank)
        }
    }

    fun saveManualAsset(draft: ManualAssetDraft): OwnedAsset = lock.withLock {
        check(!closed)
        runBlocking { BetaManualAssetWorkflow(BetaAssetRegistry(database)).save(draft) }
    }

    /** Explicit Beta ownership registration; shares the backup/reset lifetime lock. */
    fun registerAsset(asset: OwnedAsset, aliases: List<BetaAssetAlias> = emptyList()) = lock.withLock {
        check(!closed)
        runBlocking { BetaAssetRegistry(database).register(asset, aliases) }
    }

    fun assets(): List<OwnedAsset> = lock.withLock {
        check(!closed)
        runBlocking { BetaAssetRegistry(database).all() }
    }

    fun markerCount(): Int = lock.withLock {
        check(!closed)
        runBlocking { database.shellMarkerDao().count() }
    }

    fun putMarker(id: String, note: String) = lock.withLock {
        check(!closed)
        runBlocking {
            database.shellMarkerDao().upsert(BetaShellMarkerEntity(id = id, note = note))
        }
    }

    fun putPreferenceProbe(value: String) = lock.withLock {
        check(!closed)
        preferences.edit().putString(PREFERENCE_PROBE_KEY, value).commit()
    }

    fun preferenceProbe(): String? = lock.withLock {
        check(!closed)
        preferences.getString(PREFERENCE_PROBE_KEY, null)
    }

    fun backup() = lock.withLock {
        check(!closed)
        database.close()
        try {
            copyLiveDatabaseToBackup()
            exportPreferences()
        } finally {
            database = openDatabase()
        }
    }

    fun restore(): Boolean = lock.withLock {
        check(!closed)
        database.close()
        try {
            restoreOffline(appContext)
        } finally {
            database = openDatabase()
        }
    }

    fun reset() = lock.withLock {
        check(!closed)
        database.close()
        try {
            resetOffline(appContext)
        } finally {
            database = openDatabase()
        }
    }

    fun backupDatabaseFile(): File = backupDatabase(appContext)

    override fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            database.close()
        }
    }

    private fun openDatabase(): BetaDatabase {
        val name = BetaDatabase.NAME
        BetaEnvironment.requireBetaDatabaseName(name)
        val path = appContext.getDatabasePath(name)
        check(path.name == name) { "Resolved beta database path is ${path.name}" }
        return Room.databaseBuilder(appContext, BetaDatabase::class.java, name).build()
    }

    private fun copyLiveDatabaseToBackup() {
        val live = appContext.getDatabasePath(BetaEnvironment.DATABASE_NAME)
        val backup = backupDatabase(appContext)
        BetaEnvironment.requireBetaDatabaseName(live.name)
        BetaEnvironment.requireBackupDatabaseName(backup.name)
        check(live.absolutePath != backup.absolutePath)
        backup.parentFile?.mkdirs()
        live.copyTo(backup, overwrite = true)
    }

    private fun exportPreferences() {
        val destination = backupPreferences(appContext)
        BetaEnvironment.requireBackupPreferenceName(destination.name)
        val lines = preferences.all.entries
            .sortedBy { it.key }
            .joinToString("\n") { (key, value) ->
                check(value is String) { "Beta preferences store strings only" }
                "$key=$value"
            }
        destination.writeText(lines)
    }

    companion object {
        const val PREFERENCE_PROBE_KEY: String = "beta_preference_probe"

        fun restoreOffline(context: Context): Boolean {
            val appContext = context.applicationContext
            val backup = backupDatabase(appContext)
            val prefsBackup = backupPreferences(appContext)
            if (!backup.isFile || !prefsBackup.isFile) return false
            BetaEnvironment.requireBackupDatabaseName(backup.name)
            BetaEnvironment.requireBetaDatabaseName(BetaEnvironment.DATABASE_NAME)
            appContext.deleteDatabase(BetaEnvironment.DATABASE_NAME)
            backup.copyTo(appContext.getDatabasePath(BetaEnvironment.DATABASE_NAME), overwrite = true)
            importPreferences(appContext, prefsBackup)
            return true
        }

        fun resetOffline(context: Context) {
            val appContext = context.applicationContext
            BetaEnvironment.requireBetaDatabaseName(BetaEnvironment.DATABASE_NAME)
            BetaEnvironment.requireBetaPreferenceName(BetaEnvironment.PREFS_NAME)
            appContext.deleteDatabase(BetaEnvironment.DATABASE_NAME)
            appContext.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            backupDatabase(appContext).delete()
            backupPreferences(appContext).delete()
        }

        private fun importPreferences(context: Context, file: File) {
            BetaEnvironment.requireBackupPreferenceName(file.name)
            val preferences = context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE)
            val editor = preferences.edit().clear()
            file.readLines().forEach { line ->
                if (line.isEmpty()) return@forEach
                editor.putString(line.substringBefore('='), line.substringAfter('='))
            }
            editor.commit()
        }

        private fun backupDatabase(context: Context): File =
            File(context.filesDir, BetaEnvironment.BACKUP_DATABASE_FILE_NAME)

        private fun backupPreferences(context: Context): File =
            File(context.filesDir, BetaEnvironment.BACKUP_PREFS_FILE_NAME)
    }
}
