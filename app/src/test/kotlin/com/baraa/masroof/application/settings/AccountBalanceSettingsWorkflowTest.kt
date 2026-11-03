package com.baraa.masroof.application.settings

import com.baraa.masroof.application.dashboard.AccountOpeningBalance
import com.baraa.masroof.application.dashboard.AccountOpeningBalanceStore
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.repository.RawSmsInsertResult
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.parsing.model.CardSmsChannel
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import com.baraa.masroof.testsupport.NoOpCardRegistryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Fictional accounts and messages: openings are set in settings only, never inferred. */
class AccountBalanceSettingsWorkflowTest {
    private val zone: ZoneId = ZoneId.of("Asia/Riyadh")
    private val now: Instant = Instant.parse("2026-10-20T12:30:15.123456Z")
    private val account = AccountReference(Bank("BANK_SNB"), "4321")
    private val other = AccountReference(Bank("BANK_SNB"), "8765")

    @Test fun settingNowStoresTheBalanceAtThisInstantAndMovementStartsAfterIt() {
        val store = MemoryStore()
        workflow(store).setNow(account, BigDecimal("-120.5"))
        val saved = store.load().single()
        assertEquals("-120.50", saved.amount)
        assertEquals("2026-10-20", saved.asOfDate)
        assertEquals("2026-10-20T12:30:15.123Z", saved.setAt)
        assertEquals(Instant.parse("2026-10-20T12:30:15.124Z"), saved.movementStart(zone))
        assertEquals(saved, workflow(store).opening(account))
    }

    @Test fun aNewBalanceReplacesTheOldOneAndClearRemovesOnlyThatAccount() {
        val store = MemoryStore()
        val flow = workflow(store)
        flow.setNow(account, BigDecimal("10"))
        flow.setNow(other, BigDecimal("20"))
        flow.setNow(account, BigDecimal("30"))
        assertEquals(listOf("20.00", "30.00"), store.load().map { it.amount }.sorted())
        flow.clear(account)
        assertEquals(listOf(other.maskedNumber), store.load().map { it.maskedNumber })
    }

    @Test fun latestStatedBalanceIsTheNewestMessageOnThisAccountOrItsDebitCard() = runBlocking {
        val records = listOf(
            record("old", MoneyDirection.OUTGOING, source = account, balance = "900.00"),
            record("card", MoneyDirection.OUTGOING, card = CardReference(Bank("BANK_SNB"), "1111"), balance = "750.25",
                channel = CardSmsChannel.DEBIT),
            record("credit", MoneyDirection.OUTGOING, source = account, balance = "9999.00", channel = CardSmsChannel.CREDIT),
            record("someoneElse", MoneyDirection.INCOMING, source = account, destination = other, balance = "5.00"),
        )
        val received = mapOf("old" to "2026-10-01T08:00:00Z", "card" to "2026-10-05T08:00:00Z",
            "credit" to "2026-10-09T08:00:00Z", "someoneElse" to "2026-10-10T08:00:00Z")
        val debit = CardRegistryEntry.forTest(Bank("BANK_SNB"), "1111", linkedAccountBankId = "BANK_SNB", linkedAccountMaskedNumber = "4321")
        val stated = workflow(MemoryStore(), records, received, listOf(debit)).latestStatedBalance(account)
        assertEquals(StatedAccountBalance(signed("750.25"), Instant.parse("2026-10-05T08:00:00Z")), stated)
    }

    @Test fun aNegativeStatedBalanceIsAdoptedAtThatMessageAndNotBefore() = runBlocking {
        val records = listOf(record("neg", MoneyDirection.OUTGOING, source = account, negative = "40.00",
            occurredLocal = java.time.LocalDateTime.of(2026, 10, 6, 11, 5)))
        // The SMS states a local time a minute after it reached the phone; the cut-off follows the later one.
        val store = MemoryStore()
        val flow = workflow(store, records, mapOf("neg" to "2026-10-06T08:04:30Z"))
        val stated = requireNotNull(flow.latestStatedBalance(account))
        assertEquals(signed("-40.00"), stated.amount)
        assertEquals(Instant.parse("2026-10-06T08:05:00Z"), stated.at)
        flow.adopt(account, stated)
        assertEquals(Instant.parse("2026-10-06T08:05:00.001Z"), store.load().single().movementStart(zone))
    }

    @Test fun noMessageWithThisAccountsBalanceStatesNothing() = runBlocking {
        val records = listOf(
            record("noBalance", MoneyDirection.OUTGOING, source = account),
            record("review", MoneyDirection.OUTGOING, source = account, balance = "1.00", status = ParseStatus.REVIEW_REQUIRED),
        )
        assertNull(workflow(MemoryStore(), records, mapOf("noBalance" to "2026-10-01T00:00:00Z",
            "review" to "2026-10-02T00:00:00Z")).latestStatedBalance(account))
    }

    @Test fun typedBalancesAcceptArabicDigitsAndAMinus() {
        assertEquals(BigDecimal("1500.50"), AccountBalanceInput.parse("١٬٥٠٠٫٥"))
        assertEquals(BigDecimal("-200.00"), AccountBalanceInput.parse(" −200 "))
        assertEquals(BigDecimal("1234.56"), AccountBalanceInput.parse("1,234.56"))
        assertTrue(listOf("", "abc", "1.234", "--1", "1.").all { AccountBalanceInput.parse(it) == null })
    }

    private fun signed(amount: String) = SignedMoneyAmount(BigDecimal(amount), Currency.SAR)

    @Test fun aMatchingCheckBecomesTheNextStartingPoint() = runBlocking {
        val store = MemoryStore()
        workflow(store).setNow(account, BigDecimal("500"))
        val result = check(store, appBalance = "380.00", typed = "380")
        assertEquals(BalanceCheckResult.Matched, result)
        assertEquals("380.00", store.load().single().amount)
    }

    @Test fun aDifferenceIsReportedWithHintsAndNothingIsChanged() = runBlocking {
        val store = MemoryStore()
        workflow(store).setNow(account, BigDecimal("500"))
        val hints = BalanceCheckHints(unconfirmedOtpCount = 1, unconfirmedOtpTotal = BigDecimal("84.00"))
        val result = check(store, appBalance = "4069.96", typed = "3886.01", hints = hints) as BalanceCheckResult.Differs
        assertEquals(BigDecimal("183.95"), result.difference)
        assertEquals(hints, result.hints)
        assertEquals("500.00", store.load().single().amount)
    }

    @Test fun anAccountWithoutABalanceStartsFromTheCheck() = runBlocking {
        val store = MemoryStore()
        val result = AccountBalanceCheckWorkflow(workflow(store), { _, _ -> BalanceCheckHints() }, zone)
            .check(account, current = null, bankBalance = BigDecimal("0.71"))
        assertEquals(BalanceCheckResult.Started, result)
        assertEquals("0.71", store.load().single().amount)
    }

    private suspend fun check(
        store: MemoryStore,
        appBalance: String,
        typed: String,
        hints: BalanceCheckHints = BalanceCheckHints(),
    ): BalanceCheckResult {
        val opening = workflow(store).opening(account)!!
        val current = com.baraa.masroof.application.dashboard.AccountBalance(
            account.bank, account.maskedNumber!!, opening.opening, opening.date,
            SignedMoneyAmount(BigDecimal(appBalance) - opening.opening.amount, Currency.SAR),
        )
        return AccountBalanceCheckWorkflow(workflow(store), { _, _ -> hints }, zone).check(account, current, BigDecimal(typed))
    }

    private fun workflow(
        store: MemoryStore,
        records: List<ParsedEventRecord> = emptyList(),
        received: Map<String, String> = emptyMap(),
        cards: List<CardRegistryEntry> = emptyList(),
    ) = AccountBalanceSettingsWorkflow(
        store = store,
        parsedEvents = Parsed(records),
        rawSms = Raw(received.mapValues { Instant.parse(it.value) }),
        cards = NoOpCardRegistryRepository(cards),
        clock = Clock.fixed(now, ZoneOffset.UTC),
        zoneId = zone,
    )

    private fun record(
        id: String,
        direction: MoneyDirection,
        source: AccountReference? = null,
        destination: AccountReference? = null,
        card: CardReference? = null,
        balance: String? = null,
        negative: String? = null,
        channel: CardSmsChannel? = null,
        status: ParseStatus = ParseStatus.SUCCESS,
        occurredLocal: java.time.LocalDateTime? = null,
    ) = ParsedEventRecord(
        ParsedEvent(id, id, Bank("BANK_SNB"), MessageFamily.PURCHASE, direction, Money.of("10.00", Currency.SAR), null,
            source, destination, card, null, null, null, null, Confidence(1.0), status),
        ParsedEventDetails(
            availableBalance = balance?.let { Money.of(it, Currency.SAR) },
            negativeAvailableBalance = negative?.let { Money.of(it, Currency.SAR) },
            cardSmsChannel = channel,
            occurredAtLocal = occurredLocal,
        ),
    )

    private class MemoryStore : AccountOpeningBalanceStore {
        private val balances = mutableListOf<AccountOpeningBalance>()
        override fun load(): List<AccountOpeningBalance> = balances.toList()
        override fun save(balance: AccountOpeningBalance) {
            balances.removeAll { it.key == balance.key }
            balances += balance
        }
        override fun remove(bankId: String, maskedNumber: String) {
            balances.removeAll { it.bankId == bankId && it.maskedNumber == maskedNumber }
        }
    }

    private class Parsed(private val records: List<ParsedEventRecord>) : ParsedEventRepository {
        override suspend fun save(event: ParsedEvent, details: ParsedEventDetails) = Unit
        override suspend fun getById(id: String): ParsedEventRecord? = records.find { it.event.id == id }
        override suspend fun findByRawSmsId(rawSmsId: String): ParsedEventRecord? = records.find { it.event.rawSmsId == rawSmsId }
        override suspend fun deleteByRawSmsId(rawSmsId: String) = Unit
        override suspend fun listAll(): List<ParsedEventRecord> = records
        override suspend fun listReceivedBetween(startInclusive: Instant, endExclusive: Instant): List<ParsedEventRecord> = records
    }

    private class Raw(private val receivedAt: Map<String, Instant>) : RawSmsRepository {
        private fun sms(id: String) = receivedAt[id]?.let { RawSms(id, "BANK", "body", it, null, id) }
        override suspend fun insertIfAbsent(rawSms: RawSms): RawSmsInsertResult = RawSmsInsertResult.Inserted
        override suspend fun getById(id: String): RawSms? = sms(id)
        override suspend fun existsById(id: String): Boolean = id in receivedAt
        override suspend fun findByDeviceMessageId(deviceMessageId: String): RawSms? = null
        override suspend fun listIdsByReceivedAt(): List<String> = receivedAt.keys.toList()
        override suspend fun findCrossSourceNearDuplicate(
            sender: String,
            bodyHash: String,
            fromInclusive: Instant,
            toInclusive: Instant,
            lookingForLiveRow: Boolean,
        ): RawSms? = null
    }
}
