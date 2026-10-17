package com.baraa.masroof.lab.beta.data

import androidx.room.*

/** Current trusted observation projection. Empty after migration until explicitly refreshed.
 * No raw body or experimental candidate archive is copied into this table. */
@Entity(tableName = "beta_dashboard_evidence_facts", indices = [Index("transactionId", unique = true)],
    foreignKeys = [ForeignKey(entity = BetaRawEvidenceEntity::class, parentColumns = ["id"],
        childColumns = ["evidenceId"], onDelete = ForeignKey.RESTRICT)])
internal data class BetaDashboardFactsEntity(
    @PrimaryKey val evidenceId: String, val transactionId: String, val bankId: String,
    val archiveFingerprint: String, val observationRevision: Long,
    val correction: Long, val interpretation: Long, val boundInput: String,
    val observedAt: String, val feeKnown: Boolean, val feeAmount: String?, val feeCurrency: String?,
    val balanceAsset: String?, val balanceAmount: String?, val balanceCurrency: String?,
    val facilityAsset: String?, val dueAmount: String?, val dueCurrency: String?, val dueDate: String?,
    val merchant: String?, val counterparty: String?,
    val debitCardAsset: String?, val debitCardLast4: String?, val debitCardParent: String?,
    @ColumnInfo(defaultValue = "NULL") val messageFamily: String? = null,
) { override fun toString() = "BetaDashboardFactsEntity(redacted)" }

@Dao internal interface BetaDashboardFactsDao {
    @Upsert suspend fun put(value: BetaDashboardFactsEntity)
    @Query("SELECT * FROM beta_dashboard_evidence_facts WHERE evidenceId=:id") suspend fun evidence(id: String): BetaDashboardFactsEntity?
    @Query("SELECT f.* FROM beta_dashboard_evidence_facts f ORDER BY f.evidenceId LIMIT :limit")
    suspend fun all(limit: Int): List<BetaDashboardFactsEntity>
}
