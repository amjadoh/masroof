package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.*
import com.baraa.masroof.domain.period.FinancialPeriod
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class PrepaidCardOverviewTest {
    @Test fun fundingPurchaseAndRefundRemainSeparateWithoutInventingOpeningBalance() {
        val card = CardRegistryEntry.forTest(Bank.BANK_ALJAZIRA, "7271", cardType = CardType.PREPAID)
        val accountId = "account:BANK_ALJAZIRA:3001"
        val cardId = "card:BANK_ALJAZIRA:7271"
        val instant = Instant.parse("2026-07-20T07:00:00Z")
        fun row(id: String, type: FinancialTransactionType, amount: String, currency: Currency, incoming: Boolean) =
            FinancialTransaction(id, type, Money.of(amount, currency), instant,
                if (incoming) accountId else cardId, if (incoming) cardId else null,
                null, null, null, emptyList())
        val purchase = row("purchase", FinancialTransactionType.EXPENSE, "30", Currency.SAR, false)
        val rows = listOf(
            row("fund", FinancialTransactionType.SELF_TRANSFER, "100", Currency.SAR, true),
            purchase, purchase,
            row("refund", FinancialTransactionType.REFUND, "5", Currency.SAR, true),
            row("usd", FinancialTransactionType.SELF_TRANSFER, "20", Currency.USD, true),
        )
        val context = DashboardProjectionContext(
            FinancialPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)),
            Instant.parse("2026-08-01T00:00:00Z"), LocalDate.of(2026, 7, 20),
            DashboardEvidence.EMPTY, rows, emptyMap(), 0, emptyList(), setOf(accountId), setOf("3001"),
            listOf(card), emptyList(), DebitCardScopeFactory.fromRegistry(listOf(card)),
            Locale.ENGLISH, ZoneId.of("Asia/Riyadh"), Currency.SAR,
        )
        val overview = PrepaidCardOverviewBuilder.build(context).single()
        assertEquals(1, overview.unconvertedFundingCount)
        val sar = overview.currencies.single { it.currency == Currency.SAR }
        assertEquals(Money.of("100", Currency.SAR), sar.topUps)
        assertEquals(Money.of("30", Currency.SAR), sar.spending)
        assertEquals(Money.of("5", Currency.SAR), sar.refunds)
        assertEquals(SignedMoneyAmount.of(Money.of("75", Currency.SAR)), sar.netMovement)
        assertNull(sar.observedBalance)
        assertNull(sar.balanceReceivedAt)
        assertEquals(Money.of("20", Currency.USD), overview.currencies.single { it.currency == Currency.USD }.topUps)
        assertTrue(PrepaidCardOverviewBuilder.build(context.copy(cardRegistry = listOf(card.copy(ownership = OwnershipStatus.EXTERNAL)))).isEmpty())
    }
}
