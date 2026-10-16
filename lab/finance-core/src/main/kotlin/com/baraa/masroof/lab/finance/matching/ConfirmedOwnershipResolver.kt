package com.baraa.masroof.lab.finance.matching

import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.Environment

/** Resolves confirmed registry IDs only. Neither a suffix nor a missing lookup establishes ownership. */
object ConfirmedOwnershipResolver {
    fun resolve(id: AssetId, environment: Environment, registry: Map<AssetId, OwnedAsset>): AssetResolution {
        val snapshot = registry.toMap()
        val asset = snapshot[id] ?: return AssetResolution.Unconfirmed
        if (id.environment != environment || asset.id != id) return AssetResolution.Unconfirmed
        val parentKind = when (asset.kind) {
            AssetKind.DEBIT_CARD -> AssetKind.ACCOUNT
            AssetKind.CREDIT_CARD -> AssetKind.CREDIT_FACILITY
            else -> null
        }
        if (parentKind != null) {
            val parent = asset.parentId?.let(snapshot::get) ?: return AssetResolution.Unconfirmed
            if (parent.id != asset.parentId || parent.kind != parentKind || parent.id.bank != id.bank ||
                parent.id.environment != environment
            ) return AssetResolution.Unconfirmed
        }
        return AssetResolution.Confirmed(asset)
    }
}
