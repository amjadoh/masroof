package com.baraa.masroof.lab.data

import android.content.Context
import androidx.room.*

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
    LabRunRow::class, LabRunResultRow::class], version = 1, exportSchema = true)
abstract class LabDatabase : RoomDatabase() {
    abstract fun dao(): LabDao
    companion object {
        const val FILE_NAME = "masroof_parsing_lab.db"
        fun open(context: Context): LabDatabase = Room.databaseBuilder(context.applicationContext,
            LabDatabase::class.java, FILE_NAME).build()
    }
}
