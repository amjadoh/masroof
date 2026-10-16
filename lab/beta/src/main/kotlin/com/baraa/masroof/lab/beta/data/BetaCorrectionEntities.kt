package com.baraa.masroof.lab.beta.data

import androidx.room.*

/** An anchor exists even for suppression before any financial posting. No fake ledger row. */
@Entity(tableName = "beta_correction_current", primaryKeys = ["environment", "transactionId"])
internal data class BetaCorrectionCurrentEntity(
    val environment: String, val transactionId: String, val correction: Long, val interpretation: Long,
    val transition: Long, val activity: String, val payload: String,
)

@Entity(tableName = "beta_correction_audit", primaryKeys = ["environment", "transactionId", "transition"],
    foreignKeys = [ForeignKey(entity = BetaCorrectionCurrentEntity::class,
        parentColumns = ["environment", "transactionId"], childColumns = ["environment", "transactionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["environment", "decisionId"], unique = true)])
internal data class BetaCorrectionAuditEntity(
    val environment: String, val transactionId: String, val transition: Long,
    val decisionId: String?, val operation: String, val payload: String,
)

@Entity(tableName = "beta_correction_evidence_claim", primaryKeys = ["environment", "evidenceId"],
    foreignKeys = [ForeignKey(entity = BetaCorrectionCurrentEntity::class,
        parentColumns = ["environment", "transactionId"], childColumns = ["environment", "transactionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["environment", "transactionId"])])
internal data class BetaCorrectionEvidenceClaimEntity(val environment: String, val evidenceId: String, val transactionId: String)

@Entity(tableName = "beta_correction_proof_claim", primaryKeys = ["environment", "kind", "issuer", "reference"],
    foreignKeys = [ForeignKey(entity = BetaCorrectionCurrentEntity::class,
        parentColumns = ["environment", "transactionId"], childColumns = ["environment", "transactionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["environment", "transactionId"])])
internal data class BetaCorrectionProofClaimEntity(
    val environment: String, val kind: String, val issuer: String, val reference: String, val transactionId: String,
)

@Dao internal interface BetaCorrectionDao {
    @Query("SELECT * FROM beta_correction_current WHERE environment=:environment AND transactionId=:id")
    suspend fun current(environment: String, id: String): BetaCorrectionCurrentEntity?
    @Query("SELECT * FROM beta_correction_audit WHERE environment=:environment AND decisionId=:id")
    suspend fun decision(environment: String, id: String): BetaCorrectionAuditEntity?
    @Query("SELECT * FROM beta_correction_audit WHERE environment=:environment AND transactionId=:id ORDER BY transition")
    suspend fun history(environment: String, id: String): List<BetaCorrectionAuditEntity>
    @Query("SELECT transactionId FROM beta_correction_evidence_claim WHERE environment=:environment AND evidenceId=:id")
    suspend fun evidenceOwner(environment: String, id: String): String?
    @Query("SELECT transactionId FROM beta_correction_proof_claim WHERE environment=:environment AND kind=:kind AND issuer=:issuer AND reference=:reference")
    suspend fun proofOwner(environment: String, kind: String, issuer: String, reference: String): String?
    @Upsert suspend fun current(row: BetaCorrectionCurrentEntity)
    @Insert suspend fun append(row: BetaCorrectionAuditEntity)
    @Insert suspend fun evidence(row: BetaCorrectionEvidenceClaimEntity)
    @Insert suspend fun proof(row: BetaCorrectionProofClaimEntity)
}
