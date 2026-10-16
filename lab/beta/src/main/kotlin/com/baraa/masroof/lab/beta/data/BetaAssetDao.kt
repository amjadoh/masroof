package com.baraa.masroof.lab.beta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
internal interface BetaAssetDao {
    @Insert
    suspend fun insert(asset: BetaAssetEntity)

    @Update
    suspend fun update(asset: BetaAssetEntity): Int

    @Query("DELETE FROM beta_asset_alias WHERE environment = :environment AND bankId = :bank AND assetLocalId = :id AND kind = 'LAST4' AND evidenceId = 'manual-owned-confirmation'")
    suspend fun clearManualSuffix(environment: String, bank: String, id: String)

    @Insert
    suspend fun insertAlias(alias: BetaAssetAliasEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM beta_asset_alias WHERE environment=:environment AND bankId=:bank AND assetLocalId=:id AND kind='LAST4' AND value=:value AND evidenceId=:evidence)")
    suspend fun hasProposalAlias(environment: String, bank: String, id: String, value: String, evidence: String): Boolean

    @Query("""SELECT DISTINCT a.* FROM beta_asset a INNER JOIN beta_asset_alias x
        ON a.environment=x.environment AND a.bankId=x.bankId AND a.localId=x.assetLocalId
        WHERE x.environment=:environment AND x.bankId=:bank AND substr(x.evidenceId,1,length(:prefix))=:prefix
        ORDER BY a.localId""")
    suspend fun proposalBindings(environment: String, bank: String, prefix: String): List<BetaAssetEntity>

    @Query("SELECT * FROM beta_asset WHERE environment = :environment AND bankId = :bank AND localId = :id")
    suspend fun get(environment: String, bank: String, id: String): BetaAssetEntity?

    @Query("SELECT * FROM beta_asset ORDER BY environment, bankId, localId")
    suspend fun all(): List<BetaAssetEntity>

    @Query("""SELECT DISTINCT a.* FROM beta_asset a INNER JOIN beta_asset_alias x
        ON a.environment = x.environment AND a.bankId = x.bankId AND a.localId = x.assetLocalId
        WHERE x.environment = :environment AND x.bankId = :bank AND x.kind = :kind AND x.value = :value
        ORDER BY a.localId""")
    suspend fun candidates(environment: String, bank: String, kind: String, value: String): List<BetaAssetEntity>
}
