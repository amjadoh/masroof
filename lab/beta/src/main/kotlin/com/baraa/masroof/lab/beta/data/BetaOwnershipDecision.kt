package com.baraa.masroof.lab.beta.data

import androidx.room.*

/** A decision about an observed reference, never an entity identity or an automatic merge. */
@Entity(tableName = "beta_ownership_decision", primaryKeys = ["bankId", "kind", "suffix"])
internal data class BetaOwnershipDecisionEntity(val bankId: String, val kind: String, val suffix: String,
    val status: String, val assetLocalId: String?, val revision: Long)

/** Derived exclusion from an explicit ownership decision. Rebuilt before financial screens open. */
@Entity(tableName = "beta_ownership_exclusion", foreignKeys = [ForeignKey(entity = BetaRawEvidenceEntity::class,
    parentColumns = ["id"], childColumns = ["rawId"], onDelete = ForeignKey.CASCADE)])
internal data class BetaOwnershipExclusionEntity(@PrimaryKey val rawId: String)

@Dao
internal interface BetaOwnershipDao {
    @Query("SELECT * FROM beta_ownership_decision ORDER BY bankId,kind,suffix")
    suspend fun all(): List<BetaOwnershipDecisionEntity>
    @Query("SELECT rawId FROM beta_ownership_exclusion") suspend fun excluded(): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun exclude(row: BetaOwnershipExclusionEntity)
    @Query("DELETE FROM beta_ownership_exclusion WHERE rawId=:id") suspend fun clearExclusion(id: String)
    @Upsert suspend fun put(decision: BetaOwnershipDecisionEntity)
}
