package com.baraa.masroof.lab.beta.data

import androidx.room.*

/** One captured message has one stable transaction key. No raw text in processing metadata. */
@Entity(tableName = "beta_financial_adoption", indices = [Index("transactionId", unique = true), Index("bankId")],
    foreignKeys = [ForeignKey(entity = BetaRawEvidenceEntity::class, parentColumns = ["id"],
        childColumns = ["evidenceId"], onDelete = ForeignKey.RESTRICT)])
internal data class BetaFinancialAdoptionEntity(@PrimaryKey val evidenceId: String,
    val transactionId: String, val bankId: String, val archiveFingerprint: String,
    val status: String, val reason: String, val approvedInput: String,
    val correction: Long, val interpretation: Long, val approvalRequest: String, val observationRevision: Long) {
    override fun toString() = "BetaFinancialAdoptionEntity(redacted)"
}

@Entity(tableName = "beta_financial_approval_retry", foreignKeys = [ForeignKey(entity = BetaRawEvidenceEntity::class,
    parentColumns = ["id"], childColumns = ["evidenceId"], onDelete = ForeignKey.RESTRICT)])
internal data class BetaFinancialApprovalRetryEntity(@PrimaryKey val evidenceId: String, val request: String) {
    override fun toString() = "BetaFinancialApprovalRetryEntity(redacted)"
}

@Dao internal interface BetaFinancialAdoptionDao {
    @Query("SELECT * FROM beta_financial_adoption WHERE evidenceId=:id") suspend fun evidence(id: String): BetaFinancialAdoptionEntity?
    @Query("SELECT * FROM beta_financial_adoption WHERE transactionId=:id") suspend fun transaction(id: String): BetaFinancialAdoptionEntity?
    @Query("SELECT * FROM beta_financial_adoption WHERE bankId=:bank") suspend fun bank(bank: String): List<BetaFinancialAdoptionEntity>
    @Upsert suspend fun put(value: BetaFinancialAdoptionEntity)
    @Query("UPDATE beta_financial_adoption SET status='REVIEW',reason=:reason,observationRevision=observationRevision+1 WHERE evidenceId=:id") suspend fun hold(id: String, reason: String)
    @Upsert suspend fun retry(value: BetaFinancialApprovalRetryEntity)
    @Query("SELECT * FROM beta_financial_approval_retry WHERE evidenceId=:id") suspend fun retry(id: String): BetaFinancialApprovalRetryEntity?
    @Query("DELETE FROM beta_financial_approval_retry WHERE evidenceId=:id") suspend fun clearRetry(id: String)
}
