package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.BankId

enum class BetaOwnershipKind { ACCOUNT, CARD }
enum class BetaOwnershipStatus { UNKNOWN, OWNED, EXTERNAL, REVIEW }
data class BetaOwnershipReference(val bank: BankId, val kind: BetaOwnershipKind, val suffix: String)
data class BetaOwnershipObservation(val reference: BetaOwnershipReference, val proposal: BetaAssetProposal? = null, val evidenceRevision: String = "")
data class BetaOwnershipCandidate(val reference: BetaOwnershipReference, val status: BetaOwnershipStatus,
    val token: String, val asset: OwnedAsset?, val choices: List<OwnedAsset>, val conflictingKinds: Boolean)
data class BetaOwnershipSnapshot(val candidates: List<BetaOwnershipCandidate>) {
    val configurationCount get() = candidates.count { it.status == BetaOwnershipStatus.OWNED && it.reference.kind == BetaOwnershipKind.CARD &&
        (it.asset == null || it.asset.parentId == null || (it.asset.kind == AssetKind.CREDIT_CARD && it.asset.creditCardRole == null)) }
    val unresolvedCount get() = candidates.count { it.status == BetaOwnershipStatus.UNKNOWN }
}
