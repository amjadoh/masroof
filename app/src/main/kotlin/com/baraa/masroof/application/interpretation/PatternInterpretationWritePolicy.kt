package com.baraa.masroof.application.interpretation

import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter
import com.baraa.masroof.parsing.model.PatternInterpretationStamp
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import kotlinx.coroutines.CancellationException

/** Called under the financial transaction's access lease, which excludes configuration edits / restores. */
class PatternInterpretationWritePolicy(private val store: MessagePatternStore,
    private val accounts: com.baraa.masroof.domain.repository.AccountRegistryRepository? = null,
    private val cards: com.baraa.masroof.domain.repository.CardRegistryRepository? = null) {
    /**
     * [posted] evidence is never re-read, so only its pattern's enabled state and owned accounts are checked:
     * a later save (new generation or revision) must not stop a posted leg from pairing.
     */
    suspend fun isCurrent(record: ParsedEventRecord, posted: Boolean = false): Boolean {
        if (!isPatternDerived(record)) return true
        val stamp = PatternInterpretationStamp.decode(record.event.confidence.reasons) ?: return false
        return try {
            val configuration = store.load()
            val pattern = configuration.banks.singleOrNull { it.bankId == record.event.bank.id }?.currentPatterns
                ?.singleOrNull { it.id == stamp.patternId } ?: return false
            val contract = pattern.factContract ?: return false
            if (stamp.roleProfile != (pattern.roleProfile != null)) return false
            if (!pattern.enabled) return false
            if (!posted && (pattern.revision != stamp.revision ||
                PatternFactInterpreter(configuration).fingerprint != stamp.fingerprint)) return false
            for (binding in contract.accountBindings) {
                // Confirmed bank setup declares its own currency; an approved built-in link does not.
                if (contract.currency != null && binding.role != com.baraa.masroof.engine.bank.patterns.PatternAccountRole.CARD) {
                    val observed = if (binding.role == com.baraa.masroof.engine.bank.patterns.PatternAccountRole.SOURCE) record.event.sourceAccountRef else record.event.destinationAccountRef
                    val cardReference = record.event.cardRef
                    // A card-explicit purchase/refund does not consume the account fallback.
                    // Card ownership remains enforced by financial classification.
                    if (observed == null && stamp.roleProfile && binding.field == null &&
                        record.event.messageFamily in setOf(com.baraa.masroof.domain.model.MessageFamily.PURCHASE,
                            com.baraa.masroof.domain.model.MessageFamily.REFUND,
                            com.baraa.masroof.domain.model.MessageFamily.BILL_PAYMENT) &&
                        cardReference?.bank?.id == binding.bank.value) {
                        val reference = requireNotNull(cardReference)
                        val card = cards?.get(reference) ?: return false
                        if (card.bank != reference.bank || card.last4 != reference.last4 ||
                            card.ownership != com.baraa.masroof.domain.model.OwnershipStatus.OWNED) return false
                        continue
                    }
                    val fallback = com.baraa.masroof.domain.model.AccountReference(com.baraa.masroof.domain.model.Bank(binding.bank.value), binding.last4)
                    // Role-reader constants are fallbacks, not aliases. The reader gives
                    // an explicit same-bank account precedence; validate that account's
                    // ownership instead. Exact observed-reference bindings stay strict.
                    val reference = if (stamp.roleProfile && binding.field == null && observed?.bank == fallback.bank)
                        requireNotNull(observed) else fallback
                    val account = accounts?.get(reference) ?: return false
                    if (account.bank != reference.bank || account.maskedNumber != reference.maskedNumber || account.ownership != com.baraa.masroof.domain.model.OwnershipStatus.OWNED) return false
                    if (observed != reference) return false
                    continue
                }
                if (binding.role == com.baraa.masroof.engine.bank.patterns.PatternAccountRole.CARD) {
                    val card = com.baraa.masroof.domain.model.CardReference(com.baraa.masroof.domain.model.Bank(binding.bank.value), binding.last4)
                    // A card link fills only a message without one; when it was used, the card must still be owned.
                    if (record.event.cardRef == card && cards?.get(card)?.ownership != com.baraa.masroof.domain.model.OwnershipStatus.OWNED) return false
                    continue
                }
                val reference = com.baraa.masroof.domain.model.AccountReference(com.baraa.masroof.domain.model.Bank(binding.bank.value), binding.last4)
                val observed = if (binding.role == com.baraa.masroof.engine.bank.patterns.PatternAccountRole.SOURCE) record.event.sourceAccountRef else record.event.destinationAccountRef
                // A built-in link, like a card link, fills only a missing account; a taught link always supplies it.
                if (pattern.roleProfile != null && observed != reference) continue
                val account = accounts?.get(reference) ?: return false
                if (account.bank != reference.bank || account.maskedNumber != binding.last4 || account.ownership != com.baraa.masroof.domain.model.OwnershipStatus.OWNED) return false
                if (observed != reference) return false
            }
            true
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { false } // Corrupt / unsupported definitions cannot grant a financial write.
    }

    companion object {
        fun isPatternDerived(record: ParsedEventRecord) = PatternInterpretationStamp.isPatternDerived(record.event.confidence.reasons)
    }
}
