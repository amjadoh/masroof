package com.baraa.masroof.application.settings

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.AccountType
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.LoanReference
import com.baraa.masroof.domain.model.LoanRegistryEntry
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.LoanRegistryRepository

/**
 * Settings-screen registry reads, metadata edits, and ownership confirmation.
 */
class SettingsRegistryWorkflow(
    private val cardRegistryRepository: CardRegistryRepository,
    private val accountRegistryRepository: AccountRegistryRepository,
    private val loanRegistryRepository: LoanRegistryRepository,
    private val ownershipConfirmationService: OwnershipConfirmationService,
    private val parsedEvents: com.baraa.masroof.parsing.repository.ParsedEventRepository? = null,
    private val inTransaction: suspend (suspend () -> Unit) -> Unit = { it() },
    private val accountEvidenceScope: com.baraa.masroof.application.dashboard.DashboardEvidenceScope? = null,
    private val effectiveEvents: com.baraa.masroof.application.review.EffectiveParsedEventProvider? = null,
    private val reviewRepository: com.baraa.masroof.domain.repository.ReviewRepository? = null,
) {
    suspend fun externalAccountSpending(account: AccountReference): com.baraa.masroof.application.dashboard.ExternalAccountSpending {
        val entry = requireNotNull(accountRegistryRepository.get(account))
        require(entry.ownership == com.baraa.masroof.domain.model.OwnershipStatus.EXTERNAL)
        val cards = cardRegistryRepository.listAll()
        val evidence = requireNotNull(accountEvidenceScope).loadAccount(account, cards)
        val records = requireNotNull(effectiveEvents).listEffectiveByRawSmsIds(evidence.parsedRecords.map { it.event.rawSmsId })
        val ids = records.map { it.event.rawSmsId }.toSet()
        val decisions = requireNotNull(reviewRepository).listAll().filter { it.rawSmsId in ids &&
            it.status == com.baraa.masroof.domain.model.ReviewStatus.RESOLVED }
        val excluded = decisions.filter { it.resolutionKind == com.baraa.masroof.domain.model.ReviewResolutionKind.USER_NON_FINANCIAL }
            .map { it.rawSmsId }.toSet()
        val manual = decisions.filter { it.resolutionKind in setOf(
            com.baraa.masroof.domain.model.ReviewResolutionKind.USER_FINANCIAL_TYPE,
            com.baraa.masroof.domain.model.ReviewResolutionKind.USER_EXTERNAL_TRANSFER,
            com.baraa.masroof.domain.model.ReviewResolutionKind.USER_SELF_TRANSFER_PAIR,
        ) }.map { it.rawSmsId }.toSet()
        return com.baraa.masroof.application.dashboard.ExternalAccountSpendingCalculator.calculate(entry, cards, records, excluded, manual)
    }

    suspend fun addAccount(bankId: String, last4: String, name: String?): AccountReference {
        val ref = AccountReference(Bank(bankId), canonicalSuffix(last4))
        require(ref.bank != Bank.UNKNOWN)
        inTransaction {
            require(accountRegistryRepository.get(ref) == null) { "Account already exists" }
            ownershipConfirmationService.confirmAccountOwned(ref)
            accountRegistryRepository.updateDisplayName(ref, name?.trim()?.ifEmpty { null })
        }
        return ref
    }

    suspend fun addCard(bankId: String, last4: String, name: String?, configuration: com.baraa.masroof.domain.model.CardConfiguration): CardReference {
        val ref = CardReference(Bank(bankId), canonicalSuffix(last4))
        require(ref.bank != Bank.UNKNOWN)
        configuration.validate(ref.bank, requireNotNull(ref.last4))
        inTransaction {
            require(cardRegistryRepository.get(ref) == null) { "Card already exists" }
            ownershipConfirmationService.confirmCardOwned(ref)
            cardRegistryRepository.configure(ref, configuration)
            cardRegistryRepository.updateDisplayName(ref, name?.trim()?.ifEmpty { null })
        }
        return ref
    }

    private fun canonicalSuffix(value: String): String {
        val suffix = com.baraa.masroof.parsing.normalizer.MessageNormalizer().normalize(value).comparisonBody.trim()
        require(suffix.matches(Regex("[0-9]{4}")))
        return suffix
    }

    suspend fun configureCard(card: CardReference, configuration: com.baraa.masroof.domain.model.CardConfiguration) {
        cardRegistryRepository.configure(card, configuration)
    }

    /** A suggestion only. Conflicting evidence stays unknown; existing user choices are never overwritten. */
    suspend fun suggestedCardType(card: CardReference): com.baraa.masroof.domain.model.CardType? {
        val repository = parsedEvents ?: return null
        val signals = repository.listByRawSmsIds(repository.listRawSmsIdsReferencingCard(card))
            .filter { it.event.cardRef == card && it.event.bank == card.bank && it.event.parseStatus == com.baraa.masroof.domain.model.ParseStatus.SUCCESS }
            .mapNotNull { record -> when (record.details.cardSmsChannel) {
                com.baraa.masroof.parsing.model.CardSmsChannel.CREDIT,
                com.baraa.masroof.parsing.model.CardSmsChannel.STATEMENT -> com.baraa.masroof.domain.model.CardType.CREDIT
                com.baraa.masroof.parsing.model.CardSmsChannel.DEBIT -> com.baraa.masroof.domain.model.CardType.DEBIT
                null -> null
            } }.toSet()
        return signals.singleOrNull()
    }

    data class Snapshot(
        val cards: List<CardRegistryEntry>,
        val accounts: List<AccountRegistryEntry>,
        val loans: List<LoanRegistryEntry>,
    )

    suspend fun loadSnapshot(): Snapshot =
        Snapshot(
            cards = cardRegistryRepository.listAll(),
            accounts = accountRegistryRepository.listAll(),
            loans = loanRegistryRepository.listAll(),
        )

    suspend fun updateCardDisplayName(card: CardReference, displayName: String?) {
        cardRegistryRepository.updateDisplayName(card, displayName)
    }

    suspend fun updateAccountDisplayName(account: AccountReference, displayName: String?) {
        accountRegistryRepository.updateDisplayName(account, displayName)
    }

    suspend fun linkDebitToAccount(card: CardReference, account: AccountReference) {
        cardRegistryRepository.linkDebitToAccount(card, account)
    }

    suspend fun markCardAsDebit(card: CardReference) {
        cardRegistryRepository.markAsDebit(card)
    }

    suspend fun updateAccountType(account: AccountReference, accountType: AccountType) {
        accountRegistryRepository.updateAccountType(account, accountType)
    }

    suspend fun confirmCardOwned(card: CardReference) {
        ownershipConfirmationService.confirmCardOwned(card)
    }

    suspend fun markCardExternal(card: CardReference) {
        ownershipConfirmationService.markCardExternal(card)
    }

    suspend fun confirmAccountOwned(account: AccountReference) {
        ownershipConfirmationService.confirmAccountOwned(account)
    }

    suspend fun markAccountExternal(account: AccountReference) {
        ownershipConfirmationService.markAccountExternal(account)
    }

    suspend fun confirmLoanOwned(loan: LoanReference) {
        ownershipConfirmationService.confirmLoanOwned(loan)
    }

    suspend fun markLoanExternal(loan: LoanReference) {
        ownershipConfirmationService.markLoanExternal(loan)
    }
}
