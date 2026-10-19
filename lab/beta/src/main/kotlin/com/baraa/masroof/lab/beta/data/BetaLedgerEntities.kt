package com.baraa.masroof.lab.beta.data

import androidx.room.*

/** Queryable current facts; the versioned snapshot remains available for exact audit validation. */
@Entity(tableName="beta_financial_transaction",primaryKeys=["environment","transactionId"],indices=[Index(value=["occurredAtEpochMillis"]),Index(value=["financialType","occurredAtEpochMillis"]),Index(value=["sourceLocalId","occurredAtEpochMillis"]),Index(value=["destinationLocalId","occurredAtEpochMillis"])])
internal data class BetaLedgerCurrentEntity(val environment: String,val transactionId: String,
    val correction: Long,val interpretation: Long,val payload: String,
    val occurredAtEpochMillis: Long? = null,val occurredAtZone: String? = null,
    val amountDecimal: String? = null,val amountCurrency: String? = null,val financialType: String? = null,
    val sourceLocalId: String? = null,val destinationLocalId: String? = null,
    val sourceBankId: String? = null,val destinationBankId: String? = null)
internal data class BetaLedgerHistoryEntity(val environment: String,val transactionId: String,val correction: Long,val interpretation: Long,val payload: String)

/** A retained evidence reservation survives revisions and suppressions. */
@Entity(tableName="beta_financial_transaction_raw_sms_link",primaryKeys=["environment","evidenceId"],
    indices=[Index(value=["environment","transactionId"]),Index(value=["environment","ledgerTransactionId"]),Index(value=["environment","correctionTransactionId"])],
    foreignKeys=[ForeignKey(entity=BetaLedgerCurrentEntity::class,parentColumns=["environment","transactionId"],childColumns=["environment","ledgerTransactionId"],onDelete=ForeignKey.RESTRICT),
        ForeignKey(entity=BetaCorrectionCurrentEntity::class,parentColumns=["environment","transactionId"],childColumns=["environment","correctionTransactionId"],onDelete=ForeignKey.RESTRICT)])
internal data class BetaEvidenceLinkRecord(val environment: String,val evidenceId: String,val transactionId: String,
    val ledgerClaim: Boolean=false,val correctionClaim: Boolean=false,
    val ledgerTransactionId: String?=transactionId.takeIf {ledgerClaim},val correctionTransactionId: String?=transactionId.takeIf {correctionClaim})
internal data class BetaLedgerEvidenceClaimEntity(val environment: String,val evidenceId: String,val transactionId: String)
internal data class BetaLedgerProofClaimEntity(val environment: String,val kind: String,val issuer: String,val reference: String,val transactionId: String)

@Dao
internal interface BetaLedgerDao : BetaAuditWriter {
    @Query("SELECT * FROM beta_financial_transaction WHERE environment=:environment AND transactionId=:transactionId")
    suspend fun current(environment: String,transactionId: String): BetaLedgerCurrentEntity?
    @Query("SELECT environment,targetId AS transactionId,correction,interpretation,payload FROM beta_audit_log WHERE eventKind='LEDGER_REVISION' AND environment=:environment AND targetId=:transactionId ORDER BY correction,interpretation")
    suspend fun history(environment: String,transactionId: String): List<BetaLedgerHistoryEntity>
    @Query("SELECT transactionId FROM beta_financial_transaction_raw_sms_link WHERE environment=:environment AND evidenceId=:evidenceId AND ledgerClaim=1")
    suspend fun evidenceOwner(environment: String,evidenceId: String): String?
    @Query("SELECT targetId FROM beta_audit_log WHERE eventKind='LEDGER_PROOF' AND environment=:environment AND proofKind=:kind AND issuer=:issuer AND reference=:reference")
    suspend fun proofOwner(environment: String,kind: String,issuer: String,reference: String): String?
    @Upsert suspend fun saveCurrent(row: BetaLedgerCurrentEntity)
    suspend fun writeCurrent(row: BetaLedgerCurrentEntity) {
        val posting=BetaLedgerCodec.decode(row.payload); val c=posting.command
        saveCurrent(row.copy(occurredAtEpochMillis=c.time.instant.toEpochMilli(),occurredAtZone=c.time.zone.id,
            amountDecimal=c.amount.amount.toPlainString(),amountCurrency=c.amount.currency.name,financialType=c.type.name,
            sourceLocalId=c.source?.localId,destinationLocalId=c.destination?.localId,sourceBankId=c.source?.bank?.value,destinationBankId=c.destination?.bank?.value))
    }
    suspend fun appendHistory(row: BetaLedgerHistoryEntity) = appendAudit(BetaAuditRecord(
        auditId("LEDGER_REVISION",row.environment,row.transactionId,row.correction,row.interpretation),"LEDGER_REVISION",row.environment,row.transactionId,row.correction,row.interpretation,payload=row.payload))
    @Query("SELECT * FROM beta_financial_transaction_raw_sms_link WHERE environment=:environment AND evidenceId=:id")
    suspend fun reservation(environment: String,id: String): BetaEvidenceLinkRecord?
    @Insert suspend fun insertReservation(row: BetaEvidenceLinkRecord)
    @Query("UPDATE beta_financial_transaction_raw_sms_link SET ledgerClaim=1,ledgerTransactionId=:owner WHERE environment=:environment AND evidenceId=:id AND transactionId=:owner")
    suspend fun markLedger(environment: String,id: String,owner: String): Int
    suspend fun claimEvidence(row: BetaLedgerEvidenceClaimEntity) {
        val old=reservation(row.environment,row.evidenceId)
        require(old==null || old.transactionId==row.transactionId) { "Evidence already reserved" }
        if(old==null) insertReservation(BetaEvidenceLinkRecord(row.environment,row.evidenceId,row.transactionId,ledgerClaim=true))
        else check(markLedger(row.environment,row.evidenceId,row.transactionId)==1)
    }
    suspend fun claimProof(row: BetaLedgerProofClaimEntity) = appendAudit(BetaAuditRecord(
        auditId("LEDGER_PROOF",row.environment,row.kind,row.issuer,row.reference),"LEDGER_PROOF",row.environment,row.transactionId,
        proofKind=row.kind,issuer=row.issuer,reference=row.reference))
}
