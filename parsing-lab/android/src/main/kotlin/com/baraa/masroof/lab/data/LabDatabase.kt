package com.baraa.masroof.lab.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "lab_shared_rule_versions")
data class LabSharedRuleRow(@PrimaryKey val version: Int, val libraryJson: String, val createdAt: Long)

@Entity(tableName = "lab_evidence_knowledge")
data class LabEvidenceKnowledgeRow(@PrimaryKey val version: Int, val knowledgeJson: String, val createdAt: Long)
@Entity(tableName = "lab_comparisons")
data class LabComparisonRow(@PrimaryKey val id: String, val bankId: String, val createdAt: Long,
    val fromDate: String, val toDate: String, val snapshotJson: String)
@Entity(tableName = "lab_comparison_results", primaryKeys = ["comparisonId", "sampleId"], indices = [Index("comparisonId")])
data class LabComparisonResultRow(val comparisonId: String, val sampleId: String, val body: String,
    val expectedJson: String?, val sharedJson: String, val evidenceJson: String, val sharedNanos: Long, val evidenceNanos: Long)

@Entity(tableName = "lab_banks")
data class LabBankRow(@PrimaryKey val id: String, val name: String, val version: Int, val packJson: String)
@Entity(tableName = "lab_rule_versions", primaryKeys = ["bankId", "version"])
data class LabRuleVersionRow(val bankId: String, val version: Int, val packJson: String, val createdAt: Long)
@Entity(tableName = "lab_samples", indices = [Index("bankId")])
data class LabSampleRow(@PrimaryKey val id: String, val bankId: String, val sender: String, val body: String,
                        val receivedAt: Long, val expectedJson: String?)
@Entity(tableName = "lab_runs")
data class LabRunRow(@PrimaryKey val id: String, val bankId: String, val version: Int, val startedAt: Long,
                     val fromDate: String, val toDate: String, val packJson: String, val sampleCount: Int)
@Entity(tableName = "lab_run_results", primaryKeys = ["runId", "sampleId"], indices = [Index("runId")])
data class LabRunResultRow(val runId: String, val sampleId: String, val sender: String, val body: String,
                          val expectedJson: String?, val outcomeJson: String, val baselineJson: String?,
                          val differencesJson: String?)

@Dao
interface LabDao {
    @Query("SELECT * FROM lab_evidence_knowledge ORDER BY version DESC LIMIT 1") suspend fun evidenceKnowledge(): LabEvidenceKnowledgeRow?
    @Insert suspend fun evidenceKnowledge(row: LabEvidenceKnowledgeRow)
    @Insert suspend fun comparison(row: LabComparisonRow)
    @Insert suspend fun comparisonResults(rows: List<LabComparisonResultRow>)
    @Query("SELECT * FROM lab_comparisons WHERE bankId = :bankId ORDER BY createdAt DESC, id DESC") suspend fun comparisons(bankId: String): List<LabComparisonRow>
    @Query("SELECT * FROM lab_comparison_results WHERE comparisonId = :id ORDER BY sampleId") suspend fun comparisonResults(id: String): List<LabComparisonResultRow>
    @Query("DELETE FROM lab_comparison_results") suspend fun clearComparisonResults()
    @Query("DELETE FROM lab_comparisons") suspend fun clearComparisons()
    @Query("DELETE FROM lab_evidence_knowledge") suspend fun clearEvidenceKnowledge()
    @Query("SELECT * FROM lab_shared_rule_versions ORDER BY version DESC LIMIT 1") suspend fun sharedRules(): LabSharedRuleRow?
    @Insert suspend fun sharedRules(row: LabSharedRuleRow)
    @Query("DELETE FROM lab_shared_rule_versions") suspend fun clearSharedRules()
    @Query("UPDATE lab_samples SET expectedJson = :expected WHERE bankId = :bankId AND id = :id") suspend fun annotate(bankId: String, id: String, expected: String?): Int
    @Query("SELECT * FROM lab_samples WHERE bankId = :bankId AND id = :id") suspend fun savedSample(bankId: String, id: String): LabSampleRow?
    @Query("SELECT * FROM lab_banks ORDER BY name") suspend fun banks(): List<LabBankRow>
    @Query("SELECT * FROM lab_samples WHERE bankId = :bankId ORDER BY receivedAt, id") suspend fun samples(bankId: String): List<LabSampleRow>
    @Query("SELECT * FROM lab_runs WHERE bankId = :bankId ORDER BY startedAt DESC") suspend fun runs(bankId: String): List<LabRunRow>
    @Query("SELECT * FROM lab_run_results WHERE runId = :runId ORDER BY sampleId") suspend fun results(runId: String): List<LabRunResultRow>
    @Query("SELECT * FROM lab_rule_versions WHERE bankId = :bankId ORDER BY version DESC") suspend fun versions(bankId: String): List<LabRuleVersionRow>
    @Upsert suspend fun bank(row: LabBankRow)
    @Insert suspend fun version(row: LabRuleVersionRow)
    @Insert suspend fun sample(row: LabSampleRow)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun importedSamples(rows: List<LabSampleRow>): List<Long>
    @Insert suspend fun run(row: LabRunRow)
    @Insert suspend fun results(rows: List<LabRunResultRow>)
    @Query("DELETE FROM lab_run_results") suspend fun clearResults()
    @Query("DELETE FROM lab_runs") suspend fun clearRuns()
    @Query("DELETE FROM lab_samples") suspend fun clearSamples()
    @Query("DELETE FROM lab_rule_versions") suspend fun clearVersions()
    @Query("DELETE FROM lab_banks") suspend fun clearBanks()
}

@Database(entities = [LabBankRow::class, LabRuleVersionRow::class, LabSampleRow::class,
    LabRunRow::class, LabRunResultRow::class, LabSharedRuleRow::class, LabEvidenceKnowledgeRow::class,
    LabComparisonRow::class, LabComparisonResultRow::class], version = 3, exportSchema = true)
abstract class LabDatabase : RoomDatabase() {
    abstract fun dao(): LabDao
    companion object {
        const val FILE_NAME = "masroof_parsing_lab.db"
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS lab_evidence_knowledge (version INTEGER NOT NULL, knowledgeJson TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(version))")
                db.execSQL("CREATE TABLE IF NOT EXISTS lab_comparisons (id TEXT NOT NULL, bankId TEXT NOT NULL, createdAt INTEGER NOT NULL, fromDate TEXT NOT NULL, toDate TEXT NOT NULL, snapshotJson TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS lab_comparison_results (comparisonId TEXT NOT NULL, sampleId TEXT NOT NULL, body TEXT NOT NULL, expectedJson TEXT, sharedJson TEXT NOT NULL, evidenceJson TEXT NOT NULL, sharedNanos INTEGER NOT NULL, evidenceNanos INTEGER NOT NULL, PRIMARY KEY(comparisonId, sampleId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_comparison_results_comparisonId ON lab_comparison_results (comparisonId)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS lab_shared_rule_versions (version INTEGER NOT NULL, libraryJson TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(version))")
            }
        }
        fun open(context: Context): LabDatabase = Room.databaseBuilder(context.applicationContext,
            LabDatabase::class.java, FILE_NAME).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
