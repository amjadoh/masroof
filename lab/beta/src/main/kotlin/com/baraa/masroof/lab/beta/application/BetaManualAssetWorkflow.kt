package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.*

/** A draft is not an owned asset until the user explicitly confirms and saves it. */
data class ManualAssetDraft(
    val id: AssetId,
    val kind: AssetKind,
    val last4: String,
    val parentId: AssetId? = null,
    val role: CreditCardRole? = null,
    val ownedConfirmed: Boolean = false,
    val editing: Boolean = false,
    val expectedOriginal: OwnedAsset? = null,
)
fun interface ManualAssetStore {
    /** Atomic identity/parent validation and save; no ledger or SMS operations. */
    suspend fun save(asset: OwnedAsset, editing: Boolean, expectedOriginal: OwnedAsset?): OwnedAsset
}
class BetaManualAssetWorkflow(private val store: ManualAssetStore) {
    suspend fun save(draft: ManualAssetDraft): OwnedAsset {
        require(draft.ownedConfirmed) { "Ownership must be explicitly confirmed" }
        require(draft.id.environment == Environment.BETA)
        require(BetaBankCatalog.current.contains(draft.id.bank)) { "Unsupported Beta bank" }
        require(draft.kind in setOf(AssetKind.ACCOUNT, AssetKind.DEBIT_CARD, AssetKind.CREDIT_CARD, AssetKind.CREDIT_FACILITY))
        val suffix = requireNotNull(normalizeManualAssetSuffix(draft.last4.trim())) {
            "Enter at most four decimal digits"
        }.takeIf { it.isNotEmpty() }
        require(draft.kind == AssetKind.CREDIT_FACILITY || suffix != null) { "Last four digits are required" }
        require(draft.kind != AssetKind.DEBIT_CARD || draft.parentId != null) { "A debit card needs its account" }
        require(draft.kind != AssetKind.CREDIT_CARD || (draft.parentId != null && draft.role != null)) {
            "A credit card needs its facility and role"
        }
        require(!draft.editing || draft.expectedOriginal?.id == draft.id) { "Editing requires the original stable identity" }
        val asset = OwnedAsset(draft.id, draft.kind, suffix, draft.parentId, draft.role)
        return store.save(asset, draft.editing, draft.expectedOriginal)
    }
}

/** Accept Arabic and Persian decimal keyboard digits without truncating a full number. */
fun normalizeManualAssetSuffix(value: String): String? {
    if (value.length > 4) return null
    val normalized = StringBuilder()
    value.forEach { char ->
        normalized.append(when (char) {
            in '0'..'9' -> char
            in '\u0660'..'\u0669' -> '0' + (char - '\u0660')
            in '\u06f0'..'\u06f9' -> '0' + (char - '\u06f0')
            else -> return null
        })
    }
    return normalized.toString()
}
