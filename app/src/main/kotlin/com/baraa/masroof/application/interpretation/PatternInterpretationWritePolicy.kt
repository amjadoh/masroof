package com.baraa.masroof.application.interpretation

import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import kotlinx.coroutines.CancellationException

/** Called under the financial transaction's access lease, which excludes configuration edits / restores. */
class PatternInterpretationWritePolicy(private val store: MessagePatternStore,
    private val accounts: com.baraa.masroof.domain.repository.AccountRegistryRepository? = null) {
    suspend fun isCurrent(record: ParsedEventRecord): Boolean {
        if (!isPatternDerived(record)) return true
        val reasons = record.event.confidence.reasons
        if (reasons.count { it == "engine:message-pattern-facts-v1" } != 1) return false
        val expected = reasons.filter { it.startsWith("pattern_config:") }.singleOrNull()?.removePrefix("pattern_config:") ?: return false
        val identity = reasons.filter { it.startsWith("pattern_revision:") }.singleOrNull()?.split(':') ?: return false
        if (!expected.matches(Regex("[0-9a-f]{64}")) || identity.size != 3) return false
        if (reasons.count { it == "rules:$expected" } != 1) return false
        return try {
            val configuration = store.load()
            val pattern = configuration.banks.singleOrNull { it.bankId == record.event.bank.id }?.currentPatterns
                ?.singleOrNull { it.id == identity[1] } ?: return false
            val contract = pattern.factContract ?: return false
            if (!pattern.enabled || pattern.revision.toString() != identity[2] ||
                PatternFactInterpreter(configuration).fingerprint != expected) return false
            for (binding in contract.accountBindings) {
                val reference = com.baraa.masroof.domain.model.AccountReference(com.baraa.masroof.domain.model.Bank(binding.bank.value), binding.last4)
                val account = accounts?.get(reference) ?: return false
                if (account.bank != reference.bank || account.maskedNumber != binding.last4 || account.ownership != com.baraa.masroof.domain.model.OwnershipStatus.OWNED) return false
                val observed = if (binding.role == com.baraa.masroof.engine.bank.patterns.PatternAccountRole.SOURCE) record.event.sourceAccountRef else record.event.destinationAccountRef
                if (observed != reference) return false
            }
            true
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { false } // Corrupt / unsupported definitions cannot grant a financial write.
    }

    companion object {
        fun isPatternDerived(record: ParsedEventRecord) = record.event.confidence.reasons.any {
            it == "engine:message-pattern-facts-v1" || it.startsWith("pattern_")
        }
    }
}
