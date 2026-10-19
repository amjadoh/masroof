package com.baraa.masroof.lab.beta.data

import androidx.room.*

/** A decision about an observed reference, never an entity identity or an automatic merge. */
@DatabaseView(viewName="beta_ownership_decision",value="SELECT bankId,referenceKind AS kind,suffix,status,assetLocalId,revision FROM beta_review_item WHERE recordKind='OWNERSHIP'")
internal data class BetaOwnershipDecisionEntity(val bankId: String, val kind: String, val suffix: String,
    val status: String, val assetLocalId: String?, val revision: Long)

/** Derived exclusion from an explicit ownership decision. Rebuilt before financial screens open. */
@DatabaseView(viewName="beta_ownership_exclusion",value="SELECT rawId FROM beta_parsed_event WHERE ownershipExcluded=1")
internal data class BetaOwnershipExclusionEntity(val rawId: String)

@Dao
internal interface BetaOwnershipDao : BetaAuditWriter {
    @Query("SELECT * FROM beta_ownership_decision ORDER BY bankId,kind,suffix")
    suspend fun all(): List<BetaOwnershipDecisionEntity>
    @Query("SELECT rawId FROM beta_ownership_exclusion") suspend fun excluded(): List<String>
    @Query("UPDATE beta_parsed_event SET ownershipExcluded=1 WHERE rawId=:id") suspend fun markExcluded(id: String)
    suspend fun exclude(row: BetaOwnershipExclusionEntity) = markExcluded(row.rawId)
    @Query("UPDATE beta_parsed_event SET ownershipExcluded=0 WHERE rawId=:id") suspend fun clearExclusion(id: String)
    @Upsert suspend fun saveReview(row: BetaReviewRecord)
    @Transaction
    suspend fun put(decision: BetaOwnershipDecisionEntity) {
        saveReview(BetaReviewRecord(
        "ownership:"+auditId(decision.bankId,decision.kind,decision.suffix),"OWNERSHIP",bankId=decision.bankId,status=decision.status,
        referenceKind=decision.kind,suffix=decision.suffix,assetLocalId=decision.assetLocalId,revision=decision.revision))
        appendAudit(BetaAuditRecord(id=auditId("ownership",decision.bankId,decision.kind,decision.suffix,decision.revision),
            eventKind="OWNERSHIP",targetId=auditId(decision.bankId,decision.kind,decision.suffix),transition=decision.revision,
            operation=decision.status,bankId=decision.bankId,relatedId=decision.assetLocalId))
    }
}
