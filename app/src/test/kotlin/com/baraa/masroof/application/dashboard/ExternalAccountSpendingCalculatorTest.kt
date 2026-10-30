package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.*
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ExternalAccountSpendingCalculatorTest {
    private val account = AccountRegistryEntry.forTest(Bank("BANK_SNB"), "2345", OwnershipStatus.EXTERNAL, "Family member")
    private val ref = AccountReference(account.bank, account.maskedNumber)
    private val card = CardRegistryEntry.forTest(account.bank, "7654", OwnershipStatus.EXTERNAL,
        cardType = CardType.DEBIT, linkedAccountBankId = account.bank.id, linkedAccountMaskedNumber = account.maskedNumber)

    @Test fun linkedDebitSpendingIsSeparateAndRefundsCanExceedPurchases() {
        val purchase = row("purchase", MessageFamily.PURCHASE, "12.50")
        val refund = row("refund", MessageFamily.REFUND, "20.00")
        val usd = row("usd", MessageFamily.PURCHASE, "5.00", Currency.USD)
        val report = ExternalAccountSpendingCalculator.calculate(account, listOf(card), listOf(purchase, purchase, refund, usd,
            row("incoming", MessageFamily.TRANSFER_IN, "100.00"),
            row("outgoing", MessageFamily.TRANSFER_OUT, "100.00"),
            row("loan", MessageFamily.FINANCING_INSTALLMENT, "100.00"),
            row("repayment", MessageFamily.CARD_PAYMENT, "100.00"),
            row("cash", MessageFamily.WITHDRAWAL, "100.00")))
        assertEquals(listOf("purchase", "refund", "usd"), report.rows.map { it.rawSmsId }.sorted())
        val sar = report.totals.single { it.net.currency == Currency.SAR }
        assertEquals(Money.of("12.50", Currency.SAR), sar.purchasesAndFees)
        assertEquals(Money.of("20.00", Currency.SAR), sar.refunds)
        assertEquals("-7.50", sar.net.amount.toPlainString())
        assertEquals(2, report.totals.size)
        assertEquals(0, report.unconfirmedCount)
        assertEquals(OwnershipStatus.EXTERNAL, account.ownership)
    }

    @Test fun conflictsAndUncertainEvidenceNeverEnterTotals() {
        val uncertain = row("uncertain", MessageFamily.PURCHASE, "12.50").let { it.copy(event = it.event.copy(parseStatus = ParseStatus.REVIEW_REQUIRED)) }
        val conflict = row("conflict", MessageFamily.PURCHASE, "12.50").let {
            it.copy(event = it.event.copy(sourceAccountRef = AccountReference(account.bank, "6789"))) }
        val wrongBank = row("other-bank", MessageFamily.PURCHASE, "12.50").let {
            it.copy(event = it.event.copy(bank = Bank.BANK_ALJAZIRA, cardRef = CardReference(Bank.BANK_ALJAZIRA, "7654"))) }
        val ownCard = row("own-card", MessageFamily.PURCHASE, "12.50").let {
            it.copy(event = it.event.copy(cardRef = CardReference(account.bank, "4321"), sourceAccountRef = ref)) }
        val cards = listOf(card, CardRegistryEntry.forTest(account.bank, "4321", OwnershipStatus.OWNED))
        val report = ExternalAccountSpendingCalculator.calculate(account, cards,
            listOf(uncertain, conflict, wrongBank, ownCard, row("ignored", MessageFamily.PURCHASE, "12.50"),
                row("manual", MessageFamily.PURCHASE, "12.50")), setOf("ignored"), setOf("manual"))
        assertTrue(report.rows.isEmpty())
        assertTrue(report.totals.isEmpty())
        assertEquals(4, report.unconfirmedCount)
    }

    @Test fun renamingPreservesIdentityAndExternalStatus() {
        val records = listOf(row("bill", MessageFamily.BILL_PAYMENT, "8.00"), row("fee", MessageFamily.FEE, "2.00"))
        val original = ExternalAccountSpendingCalculator.calculate(account, listOf(card), records)
        val renamed = account.copy(displayName = "اسم اختياري")
        assertEquals(original, ExternalAccountSpendingCalculator.calculate(renamed, listOf(card), records))
        assertEquals(account.id, renamed.id)
        assertEquals(Money.of("10.00", Currency.SAR), original.totals.single().purchasesAndFees)
        assertThrows(IllegalArgumentException::class.java) {
            ExternalAccountSpendingCalculator.calculate(account.copy(ownership = OwnershipStatus.OWNED), listOf(card), records)
        }
        assertTrue(ExternalAccountSpendingCalculator.calculate(account, emptyList(), records).rows.isEmpty())
    }

    private fun row(id: String, family: MessageFamily, amount: String, currency: Currency = Currency.SAR) = ParsedEventRecord(
        ParsedEvent(id, id, account.bank, family, null, Money.of(amount, currency), null, null, null,
            CardReference(card.bank, card.last4), "TEST MERCHANT", null, null, null, Confidence(0.99), ParseStatus.SUCCESS),
        ParsedEventDetails(occurredAtLocal = LocalDateTime.parse("2026-10-10T12:30:00")),
    )
}
