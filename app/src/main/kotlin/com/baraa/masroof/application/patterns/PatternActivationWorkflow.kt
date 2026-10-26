package com.baraa.masroof.application.patterns

import com.baraa.masroof.bank.InterpretationDraftMapper
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.engine.bank.MessageInput
import com.baraa.masroof.engine.bank.MessageFamily
import com.baraa.masroof.engine.bank.MoneyDirection
import com.baraa.masroof.engine.bank.patterns.*
import com.baraa.masroof.finance.value.BankId
import com.baraa.masroof.finance.value.Currency
import com.baraa.masroof.parsing.model.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

data class PatternAccountOption(val id: String, val name: String?, val last4: String)
class PatternFinancialPreview internal constructor(internal val generation: Long, internal val priorFingerprint: String,
    internal val pattern: SavedMessagePattern, internal val accountId: String,
    val amount: Money, val occurredAt: String, val reference: String?)

/** Explicit approval writes configuration only. Account identity and ownership stay in the existing registry. */
class PatternActivationWorkflow(private val store: MessagePatternStore, private val gate: DatabaseAccessGate,
    private val accounts: AccountRegistryRepository, private val ownership: OwnershipConfirmationService) {

    suspend fun accounts(bankId: String): List<PatternAccountOption> = withContext(Dispatchers.IO) {
        gate.withAccess { ownedAccounts(bankId).map { PatternAccountOption(it.id, it.displayName, it.maskedNumber) } }
    }
    private suspend fun ownedAccounts(bankId: String) = accounts.listAll().filter {
        it.bank.id == bankId && it.ownership == OwnershipStatus.OWNED && it.maskedNumber.matches(Regex("[0-9]{4}"))
    }

    suspend fun confirmAccount(bankId: String, last4: String): PatternAccountOption = withContext(Dispatchers.IO) {
        gate.withAccess {
            require(store.load().banks.any { it.bankId == bankId })
            val canonical = com.baraa.masroof.parsing.normalizer.MessageNormalizer().normalize(last4).comparisonBody.trim()
            require(canonical.matches(Regex("[0-9]{4}")))
            ownership.confirmAccountOwned(AccountReference(Bank(bankId), canonical))
            val account = ownedAccounts(bankId).single { it.maskedNumber == canonical }
            PatternAccountOption(account.id, account.displayName, account.maskedNumber)
        }
    }

    suspend fun preview(generation: Long, bankId: String, patternId: String, operation: PatternOperation, accountId: String,
        body: String, referenceField: String?, contextReferences: Set<String>, currency: Currency?): PatternFinancialPreview = withContext(Dispatchers.IO) {
        gate.withAccess {
            val current = store.load()
            check(current.generation == generation) { "Pattern configuration changed" }
            val bank = current.banks.single { it.bankId == bankId }
            val pattern = bank.currentPatterns.single { it.id == patternId }
            require(pattern.enabled)
            val account = ownedAccounts(bankId).single { it.id == accountId }
            val fields = requireNotNull(pattern.preview(bankId, pattern.sender, body)) { "Message does not match" }.fields
            val incoming = operation in setOf(PatternOperation.TRANSFER_IN, PatternOperation.REFUND)
            val role = if (incoming) PatternAccountRole.DESTINATION else PatternAccountRole.SOURCE
            val observed = referenceField?.let { field -> requireNotNull(fields[field]).raw }
            // A three-digit key must be mapped explicitly; a full suffix must already identify the chosen account.
            val key = observed?.let { value -> com.baraa.masroof.parsing.normalizer.MessageNormalizer().normalize(value).comparisonBody.trim() }
            val contract = PatternFactContract(MessageFamily.valueOf(operation.name),
                if (incoming) MoneyDirection.INCOMING else MoneyDirection.OUTGOING, currency,
                version = 2, accountBindings = listOf(PatternAccountBinding(role, BankId(bankId), account.maskedNumber, referenceField, key)),
                contextReferences = contextReferences)
            val next = pattern.copy(revision = Math.addExact(pattern.revision, 1), factContract = contract)
            val proposed = current.copy(generation = Math.addExact(generation, 1), banks = current.banks.map {
                if (it.bankId == bankId) it.copy(patterns = it.patterns + next) else it
            })
            val input = MessageInput("financial-pattern-preview", pattern.sender, body, Instant.now())
            val result = InterpretationDraftMapper().map(input, PatternFactInterpreter(proposed).interpretRouted(input, BankId(bankId)), Bank(bankId))
            require(result is ParseResult.Success) { "Financial facts need review" }
            PatternFinancialPreview(generation, PatternFactInterpreter(current).fingerprint, next, accountId,
                requireNotNull(result.event.amount), result.details.occurredAtLocal?.format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(
                    java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)).orEmpty(), observed)
        }
    }

    suspend fun save(preview: PatternFinancialPreview) = withContext(Dispatchers.IO) {
        gate.withExclusiveAccess {
            val current = store.load()
            check(current.generation == preview.generation && PatternFactInterpreter(current).fingerprint == preview.priorFingerprint) { "Preview expired" }
            val account = ownedAccounts(preview.pattern.bankId).single { it.id == preview.accountId }
            require(preview.pattern.factContract!!.accountBindings.single().last4 == account.maskedNumber)
            val bank = current.banks.single { it.bankId == preview.pattern.bankId }
            store.saveBank(current.generation, bank.copy(patterns = listOf(preview.pattern)))
        }
    }
}
