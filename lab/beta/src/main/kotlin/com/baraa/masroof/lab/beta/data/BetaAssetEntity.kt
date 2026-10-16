package com.baraa.masroof.lab.beta.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Registry identity is independent of a suffix. Parent references cannot cross bank or environment. */
@Entity(
    tableName = "beta_asset",
    primaryKeys = ["environment", "bankId", "localId"],
    foreignKeys = [ForeignKey(
        entity = BetaAssetEntity::class,
        parentColumns = ["environment", "bankId", "localId"],
        childColumns = ["environment", "bankId", "parentLocalId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index(value = ["environment", "bankId", "parentLocalId"])],
)
data class BetaAssetEntity(
    val environment: String,
    val bankId: String,
    val localId: String,
    val kind: String,
    val last4: String?,
    val parentLocalId: String?,
    val creditCardRole: String?,
)

/** Aliases are evidence, deliberately non-unique across assets (including within one bank). */
@Entity(
    tableName = "beta_asset_alias",
    primaryKeys = ["environment", "bankId", "assetLocalId", "kind", "value", "evidenceId"],
    foreignKeys = [ForeignKey(
        entity = BetaAssetEntity::class,
        parentColumns = ["environment", "bankId", "localId"],
        childColumns = ["environment", "bankId", "assetLocalId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["environment", "bankId", "assetLocalId"]),
        Index(value = ["environment", "bankId", "kind", "value"]),
    ],
)
data class BetaAssetAliasEntity(
    val environment: String,
    val bankId: String,
    val assetLocalId: String,
    val kind: String,
    val value: String,
    val evidenceId: String,
)
