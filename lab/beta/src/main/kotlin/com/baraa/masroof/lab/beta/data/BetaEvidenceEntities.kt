package com.baraa.masroof.lab.beta.data

import androidx.room.*

@Entity(tableName = "beta_raw_evidence", indices = [Index(value = ["namespace", "providerId"], unique = true)])
internal data class BetaRawEvidenceEntity(
    @PrimaryKey val id: String, val namespace: String, val providerId: String, val origin: String,
    val sender: String, val body: String, val receivedAt: String, val timeSource: String,
    val capturedAt: String, val captureRoute: String, val captureSelections: String, val historyBanks: String,
) { override fun toString() = "BetaRawEvidenceEntity(redacted)" }

@Entity(tableName = "beta_evidence_outcome", foreignKeys = [ForeignKey(entity = BetaRawEvidenceEntity::class,
    parentColumns = ["id"], childColumns = ["rawId"], onDelete = ForeignKey.CASCADE)])
internal data class BetaEvidenceOutcomeEntity(@PrimaryKey val rawId: String, val disposition: String,
    val route: String, val reasons: String, val baselineJson: String, val candidateJson: String, val updatedAt: String,
    val attempts: Int) {
    override fun toString() = "BetaEvidenceOutcomeEntity(redacted)"
}

@Entity(tableName = "beta_evidence_retry", foreignKeys = [ForeignKey(entity = BetaRawEvidenceEntity::class,
    parentColumns = ["id"], childColumns = ["rawId"], onDelete = ForeignKey.CASCADE)])
internal data class BetaEvidenceRetryEntity(@PrimaryKey val rawId: String, val stage: String,
    val attempts: Int, val code: String, val updatedAt: String)

@Entity(tableName = "beta_evidence_decision", foreignKeys = [ForeignKey(entity = BetaRawEvidenceEntity::class,
    parentColumns = ["id"], childColumns = ["rawId"], onDelete = ForeignKey.CASCADE)])
internal data class BetaEvidenceDecisionEntity(@PrimaryKey val rawId: String, val revision: Long,
    val decision: String, val updatedAt: String)

@Dao
internal interface BetaEvidenceDao {
    @Query("SELECT * FROM beta_raw_evidence WHERE id=:id") suspend fun raw(id: String): BetaRawEvidenceEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRaw(raw: BetaRawEvidenceEntity): Long
    @Query("SELECT * FROM beta_evidence_outcome WHERE rawId=:id") suspend fun outcome(id: String): BetaEvidenceOutcomeEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun outcome(outcome: BetaEvidenceOutcomeEntity)
    @Query("SELECT * FROM beta_evidence_retry WHERE rawId=:id") suspend fun retry(id: String): BetaEvidenceRetryEntity?
    @Upsert suspend fun retry(retry: BetaEvidenceRetryEntity)
    @Query("DELETE FROM beta_evidence_retry WHERE rawId=:id") suspend fun clearRetry(id: String)
    @Query("SELECT rawId FROM beta_evidence_retry ORDER BY updatedAt,rawId LIMIT 1000") suspend fun pending(): List<String>
    @Query("SELECT * FROM beta_evidence_decision WHERE rawId=:id") suspend fun decision(id: String): BetaEvidenceDecisionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun decision(decision: BetaEvidenceDecisionEntity)
}
