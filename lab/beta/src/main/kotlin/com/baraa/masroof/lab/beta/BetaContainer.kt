package com.baraa.masroof.lab.beta

import com.baraa.masroof.lab.beta.application.BetaManualAssetWorkflow
import com.baraa.masroof.lab.beta.application.ManualAssetDraft
import com.baraa.masroof.lab.beta.application.BetaStartState
import com.baraa.masroof.lab.beta.application.BetaBankSelectionWorkflow
import com.baraa.masroof.lab.beta.data.RoomBetaBankSelectionStore
import com.baraa.masroof.lab.finance.value.BankId
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
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
    private val recovery = BetaBackupRecovery(appContext).also { it.recover() }
    private var database = openDatabase()
    private val preferences = appContext.getSharedPreferences(
        BetaEnvironment.PREFS_NAME,
        Context.MODE_PRIVATE,
    ).also { BetaEnvironment.requireBetaPreferenceName(BetaEnvironment.PREFS_NAME) }

    /** Read only: prior Beta rows/preferences are history, never a new user's initial accounts. */
    fun startState(): BetaStartState = lock.withLock {
        check(!closed)
        val sqlite = database.openHelper.writableDatabase
        val stored = preferences.all[START_KEY]
        if (stored != null) {
            check(stored == START_VERSION) { "Unsupported Beta entry state" }
            return@withLock BetaStartState.READY
        }
        // Open/validate Room even when only preferences exist. No destructive fallback or reset.
        val hasRows = sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')").use { tables ->
            var found = false
            while (!found && tables.moveToNext()) {
                val table = tables.getString(0).replace("\"", "\"\"")
                found = sqlite.query("SELECT 1 FROM \"$table\" LIMIT 1").use { it.moveToFirst() }
            }
            found
        }
        if (hasRows || preferences.all.isNotEmpty()) BetaStartState.EXISTING
        else BetaStartState.NEW
    }

    /** Explicit user action; retains every existing row and unrelated preference. */
    fun acknowledgeStart() = lock.withLock {
        check(!closed)
        startState() // Reject unknown future state or unreadable database rather than replacing it.
        check(preferences.edit().putString(START_KEY, START_VERSION).commit()) { "Could not save Beta entry state" }
    }

    fun hasParsingLabHistory(): Boolean = lock.withLock {
        check(!closed)
        parsingLabFiles().any { it.exists() }
    }

    /** User-confirmed, one-time disposal of old experiments. Never called by entry/relaunch. */
    fun startFresh() = lock.withLock {
        check(!closed)
        check(startState() != BetaStartState.READY) { "Entry already acknowledged" }
        val name = BetaEnvironment.PARSING_LAB_DATABASE_NAME
        check(name != BetaEnvironment.PRODUCTION_DATABASE_NAME && name != BetaDatabase.NAME)
        val lease = appContext.getDatabasePath("$name.cleanup.lock")
        lease.parentFile?.mkdirs()
        java.io.RandomAccessFile(lease, "rw").channel.use { channel ->
            val exclusive = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
            checkNotNull(exclusive) { "Close Parsing Lab before cleaning experiments" }.use {
                val files = parsingLabFiles()
                check(files.none { it.exists() } || appContext.deleteDatabase(name)) { "Could not delete Parsing Lab" }
                check(files.none { it.exists() }) { "Parsing Lab files remain" }
                reset()
            }
        }
    }

    private fun parsingLabFiles(): List<File> {
        val path = appContext.getDatabasePath(BetaEnvironment.PARSING_LAB_DATABASE_NAME)
        check(path.name == BetaEnvironment.PARSING_LAB_DATABASE_NAME)
        return listOf(path) + listOf("-wal", "-shm", "-journal").map { File(path.path + it) }
    }

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

    /** Explicit bounded write-side maintenance after a Beta schema upgrade. */
    fun refreshDashboardFacts(afterId: String = "", limit: Int = 100) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaDashboardFacts(database).refreshPage(afterId, limit) }
    }

    fun dashboard(query: com.baraa.masroof.lab.finance.dashboard.DashboardQuery,
        periods: com.baraa.masroof.lab.finance.dashboard.producer.DashboardPeriods = com.baraa.masroof.lab.finance.dashboard.producer.DashboardPeriods()) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.finance.dashboard.producer.BetaDashboardProducer(
            com.baraa.masroof.lab.beta.data.RoomBetaDashboardSource(database, periods)).readWithCoverage(query) }
    }

    /** Controls retain the lifecycle lock on every operation, including callers holding this facade across restore. */
    fun dashboardControls(): com.baraa.masroof.lab.beta.application.BetaDashboardControls = object : com.baraa.masroof.lab.beta.application.BetaDashboardControls {
        override suspend fun read() = kotlinx.coroutines.withContext(BetaIo.dispatcher) { lock.withLock {
            check(!closed)
            runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaDashboardConfiguration(database).read() }
        } }
        override suspend fun save(expectedRevision: Long, draft: com.baraa.masroof.lab.beta.application.BetaDashboardConfigurationDraft) = kotlinx.coroutines.withContext(BetaIo.dispatcher) { lock.withLock {
            check(!closed)
            runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaDashboardConfiguration(database).save(expectedRevision, draft) }
        } }
    }

    fun financialReviewPage(afterId: String = "", limit: Int = 25) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialAdoption(database).reviewPage(afterId, limit) }
    }

    fun financialApprovalPreview(id: String) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialAdoption(database).preview(id) }
    }

    fun approveEvidenceFinancial(request: com.baraa.masroof.lab.beta.application.evidence.BetaFinancialApproval) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialAdoption(database).approve(request) }
    }

    fun retryFinancialApproval(id: String, expected: com.baraa.masroof.lab.beta.application.evidence.BetaFinancialApproval? = null) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialAdoption(database).retryApproval(id, expected) }
    }

    fun cancelFinancialApproval(request: com.baraa.masroof.lab.beta.application.evidence.BetaFinancialApproval) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialAdoption(database).cancelApproval(request) }
    }

    fun financialObservation(key: com.baraa.masroof.lab.finance.posting.PostingKey) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialAdoption(database).observation(key) }
    }

    fun reconcileApprovedEvidence(id: String) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialAdoption(database).reconcileApprovedEvidence(id) }
    }

    fun enrichEvidenceFx(id: String, expected: com.baraa.masroof.lab.finance.posting.PostingRevision,
        market: com.baraa.masroof.lab.finance.fx.FxMarketRateProvider) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialAdoption(database).enrich(id, expected, market) }
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

    fun historySession() = lock.withLock {
        check(!closed)
        com.baraa.masroof.lab.beta.data.PreferenceHistorySessionStore(preferences).load()
    }

    fun startHistory(days: Int, now: Long = System.currentTimeMillis()) = lock.withLock {
        check(!closed)
        require(days in listOf(7, 30, 90))
        val banks = bankChoices().filter { it.state == com.baraa.masroof.lab.beta.application.BankSelectionState.ENABLED }.map { it.bankId }.toSet()
        require(banks.isNotEmpty())
        val session = com.baraa.masroof.lab.beta.application.history.HistorySession(
            maxOf(0, now - days * com.baraa.masroof.lab.beta.application.history.HistorySession.DAY), now, banks)
        com.baraa.masroof.lab.beta.data.PreferenceHistorySessionStore(preferences).save(session)
        session
    }

    fun stepHistory() = lock.withLock {
        check(!closed)
        val workflow = evidenceWorkflow()
        com.baraa.masroof.lab.beta.application.history.BetaHistoryImport(
            com.baraa.masroof.lab.beta.data.PreferenceHistorySessionStore(preferences),
            com.baraa.masroof.lab.beta.data.AndroidHistorySource(appContext),
            { request, messages -> runBlocking { workflow.importBatch(request, messages) } }).step()
    }

    /** Safe bounded metadata page; no raw text enters presentation. */
    fun evidencePage(afterId: String = "", limit: Int = 25) = lock.withLock {
        check(!closed)
        require(limit in 1..100)
        runBlocking {
            val store = com.baraa.masroof.lab.beta.data.RoomBetaEvidenceStore(database)
            database.evidenceDao().idsAfter(afterId, limit).map { store.summary(it) }
        }
    }

    fun confirmProposal(proposal: com.baraa.masroof.lab.beta.application.BetaAssetProposal, draft: ManualAssetDraft): OwnedAsset = lock.withLock {
        check(!closed)
        require(draft.id == proposal.id && !draft.editing)
        require(bankChoices().any { it.bankId == proposal.id.bank && it.state == com.baraa.masroof.lab.beta.application.BankSelectionState.ENABLED })
        runBlocking { database.withTransaction {
            val store = com.baraa.masroof.lab.beta.data.RoomBetaEvidenceStore(database)
            require(proposal.copy(confirmedAssetId = null) in store.discoveryProposals(proposal.evidenceId)) { "Stale proposal" }
            require(store.proposalBinding(proposal).let { it == null || it == draft.id }) { "Already bound to another asset" }
            BetaManualAssetWorkflow(BetaAssetRegistry(database)).save(draft).also { store.recordProposalConfirmation(proposal) }
        } }
    }

    fun linkExistingProposal(proposal: com.baraa.masroof.lab.beta.application.BetaAssetProposal, expectedAsset: OwnedAsset, ownedConfirmed: Boolean): OwnedAsset = lock.withLock {
        check(!closed)
        require(ownedConfirmed)
        require(expectedAsset.id.environment == com.baraa.masroof.lab.finance.value.Environment.BETA && expectedAsset.id.bank == proposal.id.bank)
        runBlocking { database.withTransaction {
            require(database.bankSelectionDao().all().any { it.bankId == proposal.id.bank.value && it.state == com.baraa.masroof.lab.beta.application.BankSelectionState.ENABLED.name })
            val store = com.baraa.masroof.lab.beta.data.RoomBetaEvidenceStore(database)
            require(proposal.copy(confirmedAssetId = null) in store.discoveryProposals(proposal.evidenceId)) { "Stale proposal" }
            val asset = BetaAssetRegistry(database).all().singleOrNull { it.id == expectedAsset.id }
            require(asset != null && asset == expectedAsset && asset.kind == proposal.kind && asset.last4 == proposal.last4) { "Asset changed or incompatible" }
            store.recordProposalConfirmation(proposal, expectedAsset.id)
            expectedAsset
        } }
    }

    fun discoveryPage(afterId: String = "", limit: Int = 25) = lock.withLock {
        check(!closed)
        require(limit in 1..25)
        val enabled = bankChoices().filter { it.state == com.baraa.masroof.lab.beta.application.BankSelectionState.ENABLED }.map { it.bankId }.toSet()
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaEvidenceStore(database).discoveryPage(afterId, limit).let {
            it.copy(proposals = it.proposals.filter { proposal -> proposal.id.bank in enabled })
        } }
    }

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

    /** Explicit resolved Beta input only. Evidence import/comparison never calls this API. */
    fun postFinancial(input: com.baraa.masroof.lab.finance.classification.FinancialClassificationInput) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialLedger(database).post(input) }
    }

    fun postFinancial(transfer: com.baraa.masroof.lab.finance.matching.MatchedSelfTransfer) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialLedger(database).post(transfer) }
    }

    fun financialPosting(key: com.baraa.masroof.lab.finance.posting.PostingKey) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialLedger(database).read(key) }
    }

    fun financialHistory(key: com.baraa.masroof.lab.finance.posting.PostingKey) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialLedger(database).history(key) }
    }

    fun submitCorrection(observation: com.baraa.masroof.lab.finance.corrections.CorrectionObservation,
        request: com.baraa.masroof.lab.finance.corrections.CorrectionRequest) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaCorrectionLifecycle(database).submit(observation, request) }
    }

    fun rebaseCorrection(observation: com.baraa.masroof.lab.finance.corrections.CorrectionObservation, expectedCorrectionRevision: Long) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaCorrectionLifecycle(database).rebase(observation, expectedCorrectionRevision) }
    }

    fun correction(key: com.baraa.masroof.lab.finance.posting.PostingKey) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaCorrectionLifecycle(database).read(key) }
    }

    fun reviewPage(afterId: String = "", limit: Int = 25) = lock.withLock {
        check(!closed); runBlocking { com.baraa.masroof.lab.beta.application.corrections.BetaReviewWorkflow(database).page(afterId, limit) }
    }
    fun reviewDetail(key: com.baraa.masroof.lab.finance.posting.PostingKey) = lock.withLock {
        check(!closed); runBlocking { com.baraa.masroof.lab.beta.application.corrections.BetaReviewWorkflow(database).detail(key) }
    }
    fun saveReview(token: com.baraa.masroof.lab.beta.application.corrections.BetaReviewToken, decisionId: String,
        decision: com.baraa.masroof.lab.finance.corrections.FinancialDecision, metadata: Map<String, String> = emptyMap()) = lock.withLock {
        check(!closed); runBlocking { com.baraa.masroof.lab.beta.application.corrections.BetaReviewWorkflow(database).save(token, decisionId, decision, metadata) }
    }

    fun correctionHistory(key: com.baraa.masroof.lab.finance.posting.PostingKey) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaCorrectionLifecycle(database).history(key) }
    }

    /** Prior result for audit; never a financial projection input. */
    fun financialAuditPosting(key: com.baraa.masroof.lab.finance.posting.PostingKey) = lock.withLock {
        check(!closed)
        runBlocking { com.baraa.masroof.lab.beta.data.RoomBetaFinancialLedger(database).readAudit(key) }
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
            recovery.backup()
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

    /** The caller owns streams and erases the ephemeral password after return. */
    fun exportArchive(output: java.io.OutputStream, password: CharArray) = lock.withLock {
        check(!closed)
        database.close()
        try { recovery.exportArchive(output, password) } finally { database = openDatabase() }
    }

    fun importArchive(input: java.io.InputStream, password: CharArray): Boolean = lock.withLock {
        check(!closed)
        database.close()
        try { recovery.importArchive(input, password) } finally { database = openDatabase() }
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

    fun backupDatabaseFile(): File = recovery.backupFile()

    fun settings(): BetaSettings = lock.withLock {
        check(!closed)
        BetaSettings(preferences.getString("beta_ui_theme", "SYSTEM") ?: "SYSTEM", recovery.hasBackup())
    }

    fun setTheme(theme: String) = lock.withLock {
        check(!closed)
        require(theme in setOf("SYSTEM", "LIGHT", "DARK"))
        check(preferences.edit().putString("beta_ui_theme", theme).commit())
    }

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
        if (path.exists()) {
            // Android's default corruption handler can delete files. Validate read-only with a
            // non-destructive handler before handing an existing Beta database to Room.
            android.database.sqlite.SQLiteDatabase.openDatabase(path.path, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY, android.database.DatabaseErrorHandler { }).use { existing ->
                existing.rawQuery("PRAGMA quick_check", null).use { result ->
                    check(result.moveToFirst() && result.getString(0) == "ok" && !result.moveToNext()) {
                        "Existing Beta database needs recovery"
                    }
                }
            }
        }
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
        internal const val START_KEY = "beta_entry_state"
        private const val START_VERSION = "acknowledged-v1"
        const val PREFERENCE_PROBE_KEY: String = "beta_preference_probe"

        fun restoreOffline(context: Context): Boolean {
            return BetaBackupRecovery(context.applicationContext).restore()
        }

        fun resetOffline(context: Context) {
            val appContext = context.applicationContext
            BetaBackupRecovery(appContext).clear()
            BetaEnvironment.requireBetaDatabaseName(BetaEnvironment.DATABASE_NAME)
            BetaEnvironment.requireBetaPreferenceName(BetaEnvironment.PREFS_NAME)
            val live = appContext.getDatabasePath(BetaEnvironment.DATABASE_NAME)
            val databaseFiles = listOf(live) + listOf("-wal", "-shm", "-journal").map { File(live.path + it) }
            val hadDatabaseFiles = databaseFiles.any { it.exists() }
            val deleted = appContext.deleteDatabase(BetaEnvironment.DATABASE_NAME)
            check(!hadDatabaseFiles || deleted) { "Could not clear Beta database" }
            check(databaseFiles.none { it.exists() }) { "Could not clear Beta database sidecars" }
            check(appContext.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()) { "Could not clear Beta preferences" }
            listOf(backupDatabase(appContext), backupPreferences(appContext)).forEach { file ->
                check(!file.exists() || file.delete()) { "Could not clear legacy Beta snapshot" }
            }
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

data class BetaSettings(val theme: String, val backupAvailable: Boolean)
