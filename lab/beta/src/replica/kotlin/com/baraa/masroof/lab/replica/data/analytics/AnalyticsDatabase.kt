package com.baraa.masroof.lab.replica.data.analytics

import android.content.Context
import androidx.room.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.baraa.masroof.lab.replica.data.room.PreservingOpenHelperFactory

@Serializable @Entity(tableName = "analysis_snapshot", indices = [Index("issuedAt")])
data class AnalysisSnapshotRow(@PrimaryKey val id: String, val sourceHash: String, val issuedAt: Long, val payload: String)
@Serializable @Entity(tableName = "analysis_model")
data class AnalysisModelRow(@PrimaryKey val id: String, val payload: String)
@Serializable @Entity(tableName = "recurring_candidate")
data class RecurringCandidateRow(@PrimaryKey val id: String, val status: String, val payload: String)
@Serializable @Entity(tableName = "analysis_alert")
data class AnalysisAlertRow(@PrimaryKey val id: String, val kind: String, val title: String, val amount: Long,
    val firstSeen: Long, val observedAt: Long, val confirmed: Boolean = false, val read: Boolean = false,
    val snoozedUntil: Long = 0, val resolved: Boolean = false, val lastNotified: Long = 0,
    val sourceHash: String = "", val evidenceId: String? = null, val readAt: Long = 0)
@Serializable @Entity(tableName = "analysis_preferences")
data class AnalysisPreferencesRow(@PrimaryKey val id: Int = 1, val budget: Long? = null,
    val phone: Boolean = false, val budgetAlerts: Boolean = true, val paymentAlerts: Boolean = true,
    val dueAlerts: Boolean = true, val reminderDays: Int = 3, val weekly: Boolean = false,
    val cycleSummary: Boolean = true, val enabledAt: Long = 0, val gapDays: String = "")

@Dao interface AnalyticsDao {
    @Query("SELECT * FROM analysis_snapshot ORDER BY rowid DESC LIMIT 1") suspend fun latest(): AnalysisSnapshotRow?
    @Query("SELECT * FROM analysis_snapshot ORDER BY rowid ASC") suspend fun snapshots(): List<AnalysisSnapshotRow>
    @Query("SELECT * FROM analysis_snapshot ORDER BY rowid DESC LIMIT 20") suspend fun recentSnapshots(): List<AnalysisSnapshotRow>
    @Query("SELECT * FROM analysis_model") suspend fun models(): List<AnalysisModelRow>
    @Query("SELECT * FROM recurring_candidate") suspend fun candidates(): List<RecurringCandidateRow>
    @Query("SELECT * FROM analysis_alert") suspend fun alerts(): List<AnalysisAlertRow>
    @Query("SELECT * FROM analysis_preferences WHERE id=1") suspend fun preferences(): AnalysisPreferencesRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun snapshot(value: AnalysisSnapshotRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun model(value: AnalysisModelRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun candidates(values: List<RecurringCandidateRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun alerts(values: List<AnalysisAlertRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun preferences(value: AnalysisPreferencesRow)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun initializePreferences(value: AnalysisPreferencesRow)
    @Query("DELETE FROM analysis_snapshot") suspend fun clearSnapshots()
    @Query("DELETE FROM analysis_model") suspend fun clearModels()
    @Query("DELETE FROM recurring_candidate") suspend fun clearCandidates()
    @Query("DELETE FROM analysis_alert") suspend fun clearAlerts()
    @Query("DELETE FROM analysis_preferences") suspend fun clearPreferences()
}

@Database(entities = [AnalysisSnapshotRow::class, AnalysisModelRow::class, RecurringCandidateRow::class,
    AnalysisAlertRow::class, AnalysisPreferencesRow::class], version = 1, exportSchema = true)
abstract class AnalyticsDatabase : RoomDatabase() {
    abstract fun analytics(): AnalyticsDao
    companion object {
        const val NAME = "analytics.db"
        fun open(context: Context): AnalyticsDatabase = Room.databaseBuilder(context, AnalyticsDatabase::class.java, NAME)
            .openHelperFactory(PreservingOpenHelperFactory()).build()
    }
}

@Serializable data class AnalyticsArchive(val version: Int = 1, val snapshots: List<AnalysisSnapshotRow> = emptyList(),
    val models: List<AnalysisModelRow> = emptyList(), val candidates: List<RecurringCandidateRow> = emptyList(),
    val alerts: List<AnalysisAlertRow> = emptyList(), val preferences: AnalysisPreferencesRow? = null)

object AnalyticsJson {
    val format = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}

/** Only the caller's isolated context is accepted; no financial database is opened here. */
object AnalyticsArchiveStore {
    fun validate(raw: String?): AnalyticsArchive? {
        if (raw == null) return null
        require(raw.length <= 128 * 1024 * 1024) { "Analysis archive too large" }
        val archive = AnalyticsJson.format.decodeFromString<AnalyticsArchive>(raw)
        require(archive.version == 1)
        require(archive.snapshots.map { it.id }.distinct().size == archive.snapshots.size)
        require(archive.models.map { it.id }.distinct().size == archive.models.size)
        require(archive.candidates.map { it.id }.distinct().size == archive.candidates.size)
        require(archive.alerts.map { it.id }.distinct().size == archive.alerts.size)
        archive.preferences?.let { require(it.id == 1 && (it.budget == null || it.budget > 0) && it.reminderDays in 1..30) }
        archive.snapshots.forEach { require(it.sourceHash.length == 64 && it.issuedAt >= 0) }
        return archive
    }
    suspend fun export(database: AnalyticsDatabase): String = database.withTransaction {
        val dao = database.analytics()
        AnalyticsJson.format.encodeToString(AnalyticsArchive(snapshots = dao.snapshots(), models = dao.models(),
            candidates = dao.candidates(), alerts = dao.alerts(), preferences = dao.preferences()))
    }
    suspend fun restore(database: AnalyticsDatabase, raw: String?) {
        val archive = validate(raw) ?: AnalyticsArchive()
        database.withTransaction {
            val dao = database.analytics()
            dao.clearSnapshots(); dao.clearModels(); dao.clearCandidates(); dao.clearAlerts(); dao.clearPreferences()
            archive.snapshots.forEach { dao.snapshot(it) }; archive.models.forEach { dao.model(it) }
            dao.candidates(archive.candidates); dao.alerts(archive.alerts)
            archive.preferences?.let { dao.preferences(it) }
        }
    }
}
