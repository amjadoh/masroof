package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.asset.*

/** Keeps historical registry identities intact while excluding explicitly external assets and their dependants. */
internal fun ownedBetaAssets(assets: List<OwnedAsset>, external: Set<BetaOwnershipReference>): List<OwnedAsset> {
    val hidden = assets.filter { asset ->
        val kind = when (asset.kind) {
            AssetKind.ACCOUNT -> BetaOwnershipKind.ACCOUNT
            AssetKind.DEBIT_CARD, AssetKind.CREDIT_CARD -> BetaOwnershipKind.CARD
            else -> null
        }
        val suffix = asset.last4
        kind != null && suffix != null && BetaOwnershipReference(asset.id.bank, kind, suffix) in external
    }.mapTo(hashSetOf()) { it.id }
    do {
        val before = hidden.size
        assets.filter { it.parentId in hidden }.forEach { hidden += it.id }
    } while (before != hidden.size)
    return assets.filter { it.id !in hidden }
}
