package com.baraa.masroof.lab.beta.data

import androidx.room.*

/** One captured message has one stable transaction key. No raw text in processing metadata. */
@DatabaseView(viewName="beta_financial_adoption",value="SELECT evidenceId,transactionId,bankId,archiveFingerprint,status,reason,approvedInput,correction,interpretation,approvalRequest,observationRevision FROM beta_review_item WHERE recordKind='FINANCIAL'")
internal data class BetaFinancialAdoptionEntity(val evidenceId: String,
    val transactionId: String, val bankId: String, val archiveFingerprint: String,
    val status: String, val reason: String, val approvedInput: String,
    val correction: Long, val interpretation: Long, val approvalRequest: String, val observationRevision: Long) {
    override fun toString() = "BetaFinancialAdoptionEntity(redacted)"
}

@DatabaseView(viewName="beta_financial_approval_retry",value="SELECT rawId AS evidenceId,request FROM beta_processing_retry WHERE kind='APPROVAL'")
internal data class BetaFinancialApprovalRetryEntity(val evidenceId: String, val request: String) {
    override fun toString() = "BetaFinancialApprovalRetryEntity(redacted)"
}

@Dao internal interface BetaFinancialAdoptionDao {
    @Query("SELECT * FROM beta_financial_adoption WHERE evidenceId=:id") suspend fun evidence(id: String): BetaFinancialAdoptionEntity?
    @Query("SELECT * FROM beta_financial_adoption WHERE transactionId=:id") suspend fun transaction(id: String): BetaFinancialAdoptionEntity?
    @Query("SELECT * FROM beta_financial_adoption WHERE bankId=:bank") suspend fun bank(bank: String): List<BetaFinancialAdoptionEntity>
    @Upsert suspend fun saveReview(row: BetaReviewRecord)
    suspend fun put(value: BetaFinancialAdoptionEntity) = saveReview(BetaReviewRecord("financial:"+value.evidenceId,"FINANCIAL",value.evidenceId,value.transactionId,value.bankId,
        value.status,value.reason,value.archiveFingerprint,value.approvedInput,value.correction,value.interpretation,value.approvalRequest,value.observationRevision))
    @Query("UPDATE beta_review_item SET status='REVIEW',reason=:reason,observationRevision=observationRevision+1 WHERE recordKind='FINANCIAL' AND evidenceId=:id") suspend fun hold(id: String, reason: String)
    @Upsert suspend fun saveRetry(row: BetaRetryRecord)
    suspend fun retry(value: BetaFinancialApprovalRetryEntity) = saveRetry(BetaRetryRecord(value.evidenceId,"APPROVAL",request=value.request))
    @Query("SELECT * FROM beta_financial_approval_retry WHERE evidenceId=:id") suspend fun retry(id: String): BetaFinancialApprovalRetryEntity?
    @Query("DELETE FROM beta_processing_retry WHERE rawId=:id AND kind='APPROVAL'") suspend fun clearRetry(id: String)
}
