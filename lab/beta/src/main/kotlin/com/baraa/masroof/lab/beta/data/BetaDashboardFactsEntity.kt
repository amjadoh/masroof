package com.baraa.masroof.lab.beta.data

import androidx.room.*

/** Current trusted observation projection. Empty after migration until explicitly refreshed.
 * No raw body or experimental candidate archive is copied into this table. */
@DatabaseView(viewName = "beta_dashboard_evidence_facts", value = "SELECT rawId AS evidenceId, f_transactionId AS transactionId, f_bankId AS bankId, f_archiveFingerprint AS archiveFingerprint, f_observationRevision AS observationRevision, f_correction AS correction, f_interpretation AS interpretation, f_boundInput AS boundInput, f_observedAt AS observedAt, f_feeKnown AS feeKnown, f_feeAmount AS feeAmount, f_feeCurrency AS feeCurrency, f_balanceAsset AS balanceAsset, f_balanceAmount AS balanceAmount, f_balanceCurrency AS balanceCurrency, f_facilityAsset AS facilityAsset, f_dueAmount AS dueAmount, f_dueCurrency AS dueCurrency, f_dueDate AS dueDate, f_merchant AS merchant, f_counterparty AS counterparty, f_debitCardAsset AS debitCardAsset, f_debitCardLast4 AS debitCardLast4, f_debitCardParent AS debitCardParent, f_messageFamily AS messageFamily FROM beta_parsed_event WHERE f_present = 1")
internal data class BetaDashboardFactsEntity(
    val evidenceId: String, val transactionId: String, val bankId: String,
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
    @Query("UPDATE beta_parsed_event SET f_present=1, f_epochMillis=:epoch, f_transactionId=:transactionId, f_bankId=:bankId, f_archiveFingerprint=:archiveFingerprint, f_observationRevision=:observationRevision, f_correction=:correction, f_interpretation=:interpretation, f_boundInput=:boundInput, f_observedAt=:observedAt, f_feeKnown=:feeKnown, f_feeAmount=:feeAmount, f_feeCurrency=:feeCurrency, f_balanceAsset=:balanceAsset, f_balanceAmount=:balanceAmount, f_balanceCurrency=:balanceCurrency, f_facilityAsset=:facilityAsset, f_dueAmount=:dueAmount, f_dueCurrency=:dueCurrency, f_dueDate=:dueDate, f_merchant=:merchant, f_counterparty=:counterparty, f_debitCardAsset=:debitCardAsset, f_debitCardLast4=:debitCardLast4, f_debitCardParent=:debitCardParent, f_messageFamily=:messageFamily WHERE rawId=:id")
    suspend fun save(id: String, epoch: Long?, transactionId: String, bankId: String, archiveFingerprint: String, observationRevision: Long, correction: Long, interpretation: Long, boundInput: String, observedAt: String, feeKnown: Boolean, feeAmount: String?, feeCurrency: String?, balanceAsset: String?, balanceAmount: String?, balanceCurrency: String?, facilityAsset: String?, dueAmount: String?, dueCurrency: String?, dueDate: String?, merchant: String?, counterparty: String?, debitCardAsset: String?, debitCardLast4: String?, debitCardParent: String?, messageFamily: String?): Int
    suspend fun put(value: BetaDashboardFactsEntity) { check(save(value.evidenceId, java.time.Instant.parse(value.observedAt).toEpochMilli(), value.transactionId, value.bankId, value.archiveFingerprint, value.observationRevision, value.correction, value.interpretation, value.boundInput, value.observedAt, value.feeKnown, value.feeAmount, value.feeCurrency, value.balanceAsset, value.balanceAmount, value.balanceCurrency, value.facilityAsset, value.dueAmount, value.dueCurrency, value.dueDate, value.merchant, value.counterparty, value.debitCardAsset, value.debitCardLast4, value.debitCardParent, value.messageFamily) == 1) }
    @RawQuery suspend fun scoped(query: androidx.sqlite.db.SupportSQLiteQuery): List<BetaDashboardFactsEntity>
    @Query("SELECT * FROM beta_dashboard_evidence_facts WHERE evidenceId=:id") suspend fun evidence(id: String): BetaDashboardFactsEntity?
    @Query("SELECT f.* FROM beta_dashboard_evidence_facts f ORDER BY f.evidenceId LIMIT :limit")
    suspend fun all(limit: Int): List<BetaDashboardFactsEntity>
}
