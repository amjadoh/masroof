package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.parsing.model.CardSmsChannel
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Fictional SNB wallet card: its SMS ("لك*6118") carries no debit marker, but the registry
 * says it is an owned debit card linked to the account. Its purchases leave that account.
 */
class RegistryDebitCardSpendTest {
    private val bank = Bank("BANK_SNB")
    private val account = AccountRegistryEntry.forTest(bank, "0100")
    private val card = CardRegistryEntry.forTest(bank, "6118", cardType = CardType.DEBIT,
        linkedAccountBankId = "BANK_SNB", linkedAccountMaskedNumber = "0100")

    @Test fun anUnmarkedPurchaseOnARegistryDebitCardLeavesItsLinkedAccount() {
        assertEquals(BigDecimal("-35.00"), movement(record("p1", channel = null)))
    }

    @Test fun aDebitMarkedPurchaseStillCounts() {
        assertEquals(BigDecimal("-35.00"), movement(record("p1", channel = CardSmsChannel.DEBIT)))
    }

    @Test fun aCreditMarkedMessageOnTheSameCardNeverLeavesTheAccount() {
        assertEquals(BigDecimal("0.00"), movement(record("p1", channel = CardSmsChannel.CREDIT)))
    }

    @Test fun anUnmarkedCardWithoutARegistryLinkStaysOutOfTheAccount() {
        val unlinked = CardRegistryEntry.forTest(bank, "6118", cardType = CardType.CREDIT)
        assertEquals(BigDecimal("0.00"), movement(record("p1", channel = null), unlinked))
    }

    private fun movement(record: ParsedEventRecord, registryCard: CardRegistryEntry = card): BigDecimal {
        val tx = FinancialTransaction("tx", FinancialTransactionType.EXPENSE, Money.of("35.00", Currency.SAR),
            Instant.parse("2026-09-05T11:44:15Z"), "card:BANK_SNB:6118", null, "Andalusia", null, null, listOf(record.event.id))
        val scope = DebitCardScopeFactory.fromRegistry(listOf(registryCard), listOf(record), listOf(account))
        return OwnedAccountPeriodSummaryCalculator.summarize(listOf(account), listOf(tx), listOf(record), Currency.SAR,
            emptyMap(), scope).single().summary.cashPosition().remaining.amount
    }

    private fun record(id: String, channel: CardSmsChannel?) = ParsedEventRecord(
        ParsedEvent(id, "raw-$id", bank, MessageFamily.PURCHASE, MoneyDirection.OUTGOING, Money.of("35.00", Currency.SAR),
            null, null, null, CardReference(bank, "6118"), "Andalusia", null, null, null, Confidence(1.0), ParseStatus.SUCCESS),
        ParsedEventDetails(cardSmsChannel = channel),
    )
}
