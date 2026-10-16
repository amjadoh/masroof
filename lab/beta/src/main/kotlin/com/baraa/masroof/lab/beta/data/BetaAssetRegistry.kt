package com.baraa.masroof.lab.beta.data

import androidx.room.withTransaction
import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.asset.AssetKind
import com.baraa.masroof.lab.finance.asset.CreditCardRole
import com.baraa.masroof.lab.finance.asset.OwnedAsset
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.finance.value.Environment

enum class BetaAliasKind { LAST4, ACCOUNT_REFERENCE, CARD_REFERENCE }

data class BetaAssetAlias(val kind: BetaAliasKind, val value: String, val evidenceId: String) {
    init {
        require(value.isNotBlank() && evidenceId.isNotBlank())
        require(kind != BetaAliasKind.LAST4 || value.matches(Regex("[0-9]{4}")))
    }
}

/** Explicit registration only. Discovery never merges assets or chooses among colliding aliases. */
class BetaAssetRegistry(private val database: BetaDatabase) {
    suspend fun register(asset: OwnedAsset, aliases: List<BetaAssetAlias> = emptyList()) {
        require(asset.id.environment == Environment.BETA) { "Beta registry cannot store rehearsal data" }
        database.withTransaction {
            val parent = asset.parentId?.let {
                database.assetDao().get(it.environment.name, it.bank.value, it.localId)
                    ?: error("Parent asset must be registered first")
            }
            require(parent == null || when (asset.kind) {
                AssetKind.DEBIT_CARD -> parent.kind == AssetKind.ACCOUNT.name
                AssetKind.CREDIT_CARD -> parent.kind == AssetKind.CREDIT_FACILITY.name
                else -> false
            }) { "Card parent kind does not match" }
            database.assetDao().insert(asset.toEntity())
            aliases.forEach { alias ->
                database.assetDao().insertAlias(BetaAssetAliasEntity(
                    asset.id.environment.name, asset.id.bank.value, asset.id.localId,
                    alias.kind.name, alias.value, alias.evidenceId,
                ))
            }
        }
    }

    /** Even a single suffix match remains discovery evidence for a later explicit decision. */
    suspend fun candidates(bank: BankId, kind: BetaAliasKind, value: String): List<OwnedAsset> =
        database.assetDao().candidates(Environment.BETA.name, bank.value, kind.name, value).map { it.toAsset() }

    suspend fun all(): List<OwnedAsset> = database.assetDao().all().map { it.toAsset() }

    private fun OwnedAsset.toEntity() = BetaAssetEntity(
        id.environment.name, id.bank.value, id.localId, kind.name, last4,
        parentId?.localId, creditCardRole?.name,
    )

    private fun BetaAssetEntity.toAsset(): OwnedAsset {
        val id = AssetId(Environment.valueOf(environment), BankId(bankId), localId)
        return OwnedAsset(id, AssetKind.valueOf(kind), last4,
            parentLocalId?.let { AssetId(id.environment, id.bank, it) },
            creditCardRole?.let(CreditCardRole::valueOf))
    }
}
