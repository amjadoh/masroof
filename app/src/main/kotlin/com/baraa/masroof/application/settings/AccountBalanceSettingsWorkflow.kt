package com.baraa.masroof.application.settings

import com.baraa.masroof.application.dashboard.AccountOpeningBalance
import com.baraa.masroof.application.dashboard.AccountOpeningBalanceStore
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.assembly.TransactionTiming
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.parsing.model.isCreditCardSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** The newest balance a bank SMS stated for one account, and when it was true. */
data class StatedAccountBalance(
    val amount: SignedMoneyAmount,
    val at: Instant,
)

/**
 * Settings-only opening balances: the user sets where an account starts, or adopts the balance
 * a bank SMS stated. The dashboard only reads the result. Nothing here is inferred automatically.
 */
class AccountBalanceSettingsWorkflow(
    private val store: AccountOpeningBalanceStore,
    private val parsedEvents: ParsedEventRepository,
    private val rawSms: RawSmsRepository,
    private val cards: CardRegistryRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val currency: Currency = Currency.SAR,
) {
    fun opening(account: AccountReference): AccountOpeningBalance? {
        val masked = account.maskedNumber ?: return null
        return store.load().find { it.bankId == account.bank.id && it.maskedNumber == masked }
    }

    /** Balance as of now: the account's bank balance the user read just now. */
    fun setNow(account: AccountReference, amount: BigDecimal) {
        save(account, SignedMoneyAmount(amount.setScale(Money.SCALE, RoundingMode.HALF_EVEN), currency), clock.instant())
    }

    /** Adopts a stated balance; movement after that SMS moves it. */
    fun adopt(account: AccountReference, stated: StatedAccountBalance) {
        require(stated.amount.currency == currency)
        save(account, stated.amount, stated.at)
    }

    fun clear(account: AccountReference) {
        store.remove(account.bank.id, requireNotNull(account.maskedNumber))
    }

    /**
     * The newest SMS that states this account's own balance: a message on the account, or on an
     * owned debit card linked to it. Credit and statement card messages state a card limit, not
     * this account's money, so they never count.
     */
    suspend fun latestStatedBalance(account: AccountReference): StatedAccountBalance? {
        val masked = account.maskedNumber ?: return null
        val reference = AccountReference(account.bank, masked)
        val debitCards = cards.listAll()
            .filter { it.ownership == OwnershipStatus.OWNED && it.linkedAccount == reference }
            .map { CardReference(it.bank, it.last4) }
            .toSet()
        val rawSmsIds = parsedEvents.listRawSmsIdsReferencingAccount(reference) +
            debitCards.flatMap { parsedEvents.listRawSmsIdsReferencingCard(it) }
        val records = parsedEvents.listByRawSmsIds(rawSmsIds)
            .filter { statesBalanceOf(it, reference, debitCards) }
        if (records.isEmpty()) return null
        val receivedAt = rawSms.getByIds(records.map { it.event.rawSmsId }).associate { it.id to it.receivedAt }
        return records.mapNotNull { record ->
            val received = receivedAt[record.event.rawSmsId] ?: return@mapNotNull null
            // The stated balance already includes this message's own transaction, so the
            // cut-off is never earlier than the time that transaction is stored at.
            val occurred = TransactionTiming.effectiveOccurredAt(record.event, record.details.occurredAtLocal, received, zoneId)
            val at = maxOf(received, occurred)
            Triple(record, at, signedBalance(record))
        }.maxWithOrNull(compareBy<Triple<ParsedEventRecord, Instant, SignedMoneyAmount>> { it.second }.thenBy { it.first.event.id })
            ?.let { (_, at, amount) -> StatedAccountBalance(amount, at) }
    }

    private fun statesBalanceOf(record: ParsedEventRecord, account: AccountReference, debitCards: Set<CardReference>): Boolean {
        val event = record.event
        val details = record.details
        if (event.parseStatus != ParseStatus.SUCCESS && event.parseStatus != ParseStatus.NON_FINANCIAL) return false
        if (details.isCreditCardSms()) return false
        val balance = details.availableBalance ?: details.negativeAvailableBalance ?: return false
        if (balance.currency != currency) return false
        val source = event.sourceAccountRef
        val destination = event.destinationAccountRef
        val viaCard = event.cardRef != null && event.cardRef in debitCards
        return when (event.direction) {
            MoneyDirection.OUTGOING -> source == account || (viaCard && source == null)
            MoneyDirection.INCOMING -> destination == account || (viaCard && destination == null)
            // Without a direction the balance is this account's only when no other account is named.
            else -> listOfNotNull(source, destination).all { it == account } &&
                (source == account || destination == account || viaCard)
        }
    }

    private fun signedBalance(record: ParsedEventRecord): SignedMoneyAmount {
        val details = record.details
        details.availableBalance?.let { return SignedMoneyAmount(it.amount.setScale(Money.SCALE, RoundingMode.HALF_EVEN), it.currency) }
        val negative = requireNotNull(details.negativeAvailableBalance)
        return SignedMoneyAmount(negative.amount.negate().setScale(Money.SCALE, RoundingMode.HALF_EVEN), negative.currency)
    }

    private fun save(account: AccountReference, amount: SignedMoneyAmount, at: Instant) {
        store.save(
            AccountOpeningBalanceStore.of(
                bankId = account.bank.id,
                maskedNumber = requireNotNull(account.maskedNumber),
                amount = amount,
                setAt = at.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                zoneId = zoneId,
            ),
        )
    }
}

/** Reads a typed balance: Arabic or Latin digits, an optional minus, at most two decimals. */
object AccountBalanceInput {
    fun parse(text: String): BigDecimal? {
        val normalized = buildString {
            text.trim().forEach { char ->
                when (char) {
                    in '٠'..'٩' -> append('0' + (char - '٠'))
                    in '۰'..'۹' -> append('0' + (char - '۰'))
                    '٫' -> append('.')
                    '−', '–' -> append('-')
                    ',', '٬', ' ', ' ' -> Unit
                    else -> append(char)
                }
            }
        }
        if (!Regex("-?\\d+(\\.\\d{1,2})?").matches(normalized)) return null
        return BigDecimal(normalized).setScale(Money.SCALE, RoundingMode.HALF_EVEN)
    }
}
