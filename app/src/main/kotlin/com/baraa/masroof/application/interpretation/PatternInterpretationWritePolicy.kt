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
    suspend fun isCurrent(record: ParsedEventRecord): Boolean {
        if (!isPatternDerived(record)) return true
        val stamp = PatternInterpretationStamp.decode(record.event.confidence.reasons) ?: return false
        return try {
            val configuration = store.load()
            val pattern = configuration.banks.singleOrNull { it.bankId == record.event.bank.id }?.currentPatterns
                ?.singleOrNull { it.id == stamp.patternId } ?: return false
            val contract = pattern.factContract ?: return false
            if (stamp.roleProfile != (pattern.roleProfile != null)) return false
            if (!pattern.enabled || pattern.revision != stamp.revision ||
                PatternFactInterpreter(configuration).fingerprint != stamp.fingerprint) return false
            for (binding in contract.accountBindings) {
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
                val reference = com.baraa.masroof.domain.model.AccountReference(com.baraa.masroof.domain.model.Bank(binding.bank.value), binding.last4)
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
