package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.value.BankId

/** Supported banks are capabilities; choosing one never changes detection or creates ownership. */
data class SupportedBetaBank(val id: BankId)
class BetaBankCatalog(val supported: List<SupportedBetaBank>) {
    init { require(supported.map { it.id }.distinct().size == supported.size) }
    fun contains(id: BankId) = supported.any { it.id == id }
    companion object {
        val current = BetaBankCatalog(listOf(SupportedBetaBank(BankId("BANK_ALJAZIRA"))))
    }
}
enum class BankSelectionState { ENABLED, DISABLED }
data class UserBankSelection(val bankId: BankId, val state: BankSelectionState)
interface BetaBankSelectionStore {
    suspend fun all(): List<UserBankSelection>
    suspend fun save(selection: UserBankSelection)
}
data class BetaBankChoice(val bankId: BankId, val state: BankSelectionState?)
class BetaBankSelectionWorkflow(
    private val store: BetaBankSelectionStore,
    private val catalog: BetaBankCatalog = BetaBankCatalog.current,
) {
    suspend fun choices(): List<BetaBankChoice> {
        val selections = store.all().associateBy { it.bankId }
        return catalog.supported.map { BetaBankChoice(it.id, selections[it.id]?.state) }
    }
    suspend fun enable(bank: BankId) = set(bank, BankSelectionState.ENABLED)
    suspend fun disable(bank: BankId) {
        require(store.all().any { it.bankId == bank }) { "Bank has not been selected" }
        set(bank, BankSelectionState.DISABLED)
    }
    private suspend fun set(bank: BankId, state: BankSelectionState) {
        require(catalog.contains(bank)) { "Unsupported Beta bank" }
        store.save(UserBankSelection(bank, state))
    }
}

/** Apply only AFTER routing over the full catalog. Never resolve collisions using selections. */
class BetaBankImportScopePolicy {
    enum class Rejection { UNKNOWN, AMBIGUOUS, NOT_ENABLED, OUTSIDE_REQUESTED_HISTORY }
    sealed interface Decision {
        data class Accept(val bank: BankId) : Decision
        data class Reject(val reason: Rejection) : Decision
    }
    fun evaluate(
        routedCandidates: Set<BankId>,
        selections: List<UserBankSelection>,
        requestedHistoryBanks: Set<BankId>? = null,
    ): Decision {
        if (routedCandidates.isEmpty()) return Decision.Reject(Rejection.UNKNOWN)
        if (routedCandidates.size != 1) return Decision.Reject(Rejection.AMBIGUOUS)
        val bank = routedCandidates.single()
        if (selections.none { it.bankId == bank && it.state == BankSelectionState.ENABLED })
            return Decision.Reject(Rejection.NOT_ENABLED)
        if (requestedHistoryBanks != null && bank !in requestedHistoryBanks)
            return Decision.Reject(Rejection.OUTSIDE_REQUESTED_HISTORY)
        return Decision.Accept(bank)
    }
}
