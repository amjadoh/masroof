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
import com.baraa.masroof.domain.repository.CardRegistryRepository
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
    internal val pattern: SavedMessagePattern, internal val accountId: String?,
    val amount: Money, val occurredAt: String, val reference: String?, internal val cardId: String? = null)

/** Explicit approval writes configuration only. Account identity and ownership stay in the existing registry. */
class PatternApprovalWorkflow(private val store: MessagePatternStore, private val gate: DatabaseAccessGate,
    private val accounts: AccountRegistryRepository, private val ownership: OwnershipConfirmationService,
    private val cards: CardRegistryRepository? = null) {

    suspend fun accounts(bankId: String): List<PatternAccountOption> = withContext(Dispatchers.IO) {
        gate.withAccess { ownedAccounts(bankId).map { PatternAccountOption(it.id, it.displayName, it.maskedNumber) } }
    }
    private suspend fun ownedAccounts(bankId: String) = accounts.listAll().filter {
        it.bank.id == bankId && it.ownership == OwnershipStatus.OWNED && it.maskedNumber.matches(Regex("[0-9]{4}"))
    }

    suspend fun cards(bankId: String): List<PatternAccountOption> = withContext(Dispatchers.IO) {
        gate.withAccess { ownedCards(bankId).map { PatternAccountOption(it.id, it.displayName, it.last4) } }
    }
    private suspend fun ownedCards(bankId: String) = cards?.listAll().orEmpty().filter {
        it.bank.id == bankId && it.ownership == OwnershipStatus.OWNED && it.cardType != null && it.last4.matches(Regex("[0-9]{4}"))
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

    suspend fun preview(generation: Long, bankId: String, patternId: String, operation: PatternOperation, accountId: String?,
        body: String, referenceField: String?, contextReferences: Set<String>, currency: Currency?, cardId: String? = null): PatternFinancialPreview = withContext(Dispatchers.IO) {
        gate.withAccess {
            val current = store.load()
            if (current.generation != generation) throw PatternApprovalException(PatternApprovalProblem.CONFIGURATION_CHANGED)
            val bank = current.banks.single { it.bankId == bankId }
            val pattern = bank.currentPatterns.single { it.id == patternId }
            require(pattern.parts.filterIsInstance<FieldSlot>().filterNot { it.ignored }.all { it.field in operation.fields }) {
                "Pattern fields do not belong to operation"
            }
            val account = accountId?.let { id -> ownedAccounts(bankId).single { it.id == id } }
            val card = cardId?.let { id -> ownedCards(bankId).single { it.id == id } }
            if (account == null && card == null) throw PatternApprovalException(PatternApprovalProblem.ACCOUNT_REQUIRED)
            // A pattern may be approved while off; it is read as switched on only for this check.
            val fields = pattern.copy(enabled = true).preview(bankId, pattern.sender, body)?.fields
                ?: throw PatternApprovalException(PatternApprovalProblem.MESSAGE_MISMATCH)
            val cardField = fields["card"]
            if (cardField != null) {
                if (card == null) throw PatternApprovalException(PatternApprovalProblem.CARD_MISMATCH)
                val suffix = com.baraa.masroof.engine.bank.patterns.unmaskedReference(
                    com.baraa.masroof.parsing.normalizer.MessageNormalizer().normalize(cardField.raw).comparisonBody)
                if (suffix != card.last4) throw PatternApprovalException(PatternApprovalProblem.CARD_MISMATCH)
            } else require(card == null) { "No explicit card reference" }
            require(referenceField == null || account != null)
            val incoming = operation in setOf(PatternOperation.TRANSFER_IN, PatternOperation.REFUND)
            val role = if (incoming) PatternAccountRole.DESTINATION else PatternAccountRole.SOURCE
            val observed = referenceField?.let { field -> fields[field]?.raw
                ?: throw PatternApprovalException(PatternApprovalProblem.REFERENCE_MISMATCH) }
            // A three-digit key must be mapped explicitly; a full suffix must already identify the chosen account.
            val key = observed?.let { value -> com.baraa.masroof.engine.bank.patterns.unmaskedReference(
                com.baraa.masroof.parsing.normalizer.MessageNormalizer().normalize(value).comparisonBody) }
            if (key != null && (!key.matches(Regex("[0-9]{1,4}")) || key.length == 4 && key != account?.maskedNumber))
                throw PatternApprovalException(PatternApprovalProblem.REFERENCE_MISMATCH)
            val contract = PatternFactContract(MessageFamily.valueOf(operation.name),
                if (incoming) MoneyDirection.INCOMING else MoneyDirection.OUTGOING, currency,
                version = 2, cardBank = card?.let { BankId(bankId) },
                accountBindings = account?.let { listOf(PatternAccountBinding(role, BankId(bankId), it.maskedNumber, referenceField, key)) }.orEmpty(),
                contextReferences = contextReferences)
            val next = pattern.copy(revision = Math.addExact(pattern.revision, 1), factContract = contract)
            val proposed = current.copy(generation = Math.addExact(generation, 1), banks = current.banks.map {
                if (it.bankId == bankId) it.copy(patterns = it.patterns + next.copy(enabled = true)) else it
            })
            val input = MessageInput("financial-pattern-preview", pattern.sender, body, Instant.now())
            val interpretation = PatternFactInterpreter(proposed).interpretRouted(input, BankId(bankId))
            val result = InterpretationDraftMapper().map(input, interpretation, Bank(bankId))
            if (result !is ParseResult.Success) {
                val codes = interpretation.diagnostics.map { it.code }.toSet()
                val problem = when {
                    interpretation.facts.family == MessageFamily.OTP -> PatternApprovalProblem.NON_FINANCIAL
                    "PATTERN_CONFLICT" in codes -> PatternApprovalProblem.PATTERN_CONFLICT
                    // The bank's built-in rules read contradicting wording; no second pattern is involved.
                    "PATTERN_PROFILE_CONTEXT_CONFLICT" in codes -> PatternApprovalProblem.MESSAGE_CONTEXT_CONFLICT
                    "PATTERN_REFERENCE_ROLE_UNRESOLVED" in codes || "PATTERN_ACCOUNT_REFERENCE_UNRESOLVED" in codes -> PatternApprovalProblem.REFERENCE_ROLE
                    "PATTERN_ACCOUNT_BINDING_MISMATCH" in codes -> PatternApprovalProblem.REFERENCE_MISMATCH
                    "PATTERN_CURRENCY_CONFLICT" in codes -> PatternApprovalProblem.CURRENCY_CONFLICT
                    "PATTERN_MONEY_INCOMPLETE" in codes -> PatternApprovalProblem.CURRENCY_REQUIRED
                    "PATTERN_TIME_INCOMPLETE" in codes -> PatternApprovalProblem.INVALID_TIME
                    else -> when {
                        (result as? ParseResult.ReviewRequired)?.findings?.any { it.code == "V-015" } == true -> PatternApprovalProblem.INVALID_TIME
                        // An early pattern rejection has no amount; only blame the amount when the pattern itself ran.
                        interpretation.patternProvenance != null && interpretation.facts.amount?.amount?.signum() != 1 -> PatternApprovalProblem.INVALID_AMOUNT
                        else -> PatternApprovalProblem.VALIDATION
                    }
                }
                throw PatternApprovalException(problem)
            }
            PatternFinancialPreview(generation, PatternFactInterpreter(current).fingerprint, next, accountId,
                requireNotNull(result.event.amount), result.details.occurredAtLocal?.format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(
                    java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)).orEmpty(), observed, cardId)
        }
    }

    suspend fun save(preview: PatternFinancialPreview) = withContext(Dispatchers.IO) {
        gate.withExclusiveAccess {
            val current = store.load()
            check(current.generation == preview.generation && PatternFactInterpreter(current).fingerprint == preview.priorFingerprint) { "Preview expired" }
            preview.accountId?.let { id ->
                val account = ownedAccounts(preview.pattern.bankId).single { it.id == id }
                require(preview.pattern.factContract!!.accountBindings.single().last4 == account.maskedNumber)
            }
            preview.cardId?.let { id ->
                val card = ownedCards(preview.pattern.bankId).single { it.id == id }
                require(preview.pattern.factContract!!.cardBank?.value == card.bank.id)
            }
            val bank = current.banks.single { it.bankId == preview.pattern.bankId }
            store.saveBank(current.generation, bank.copy(patterns = listOf(preview.pattern)))
        }
    }
}
