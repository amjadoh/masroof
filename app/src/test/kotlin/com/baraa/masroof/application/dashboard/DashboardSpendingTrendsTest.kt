package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.period.FinancialPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardSpendingTrendsTest {
    private val bank = Bank.BANK_ALJAZIRA
    private val otherBank = Bank("OTHER")
    private val date = LocalDate.parse("2026-07-20")
    private val period = FinancialPeriod(date, date.plusDays(3))

    @Test
    fun accountAndCardScopes_areBankQualified_andAggregateDistinctTransactions() {
        val accounts = listOf(AccountRegistryEntry.forTest(bank, "1111"), AccountRegistryEntry.forTest(otherBank, "1111"))
        val purchase = transaction("purchase", "100", FinancialContainerIdFactory.cardId(bank, "2222"))
        val otherPurchase = transaction("other", "50", FinancialContainerIdFactory.cardId(otherBank, "2222"))
        val accountExpense = transaction("account", "30", FinancialContainerIdFactory.accountId(bank, "1111"))
        val context = context(accounts, listOf(purchase, otherPurchase, accountExpense))
        val accountSection = AccountsDashboardProjection.build(context).copy(
            transactionAccountInvolvement = mapOf(purchase.id to setOf(FinancialContainerIdFactory.accountId(bank, "1111"))),
        )
        val primary = row(bank, "2222")
        val supplementary = row(bank, "3333")
        val firstFacility = facility(primary, listOf(supplementary))
        val secondFacility = facility(row(otherBank, "2222"))
        val cards = CardsDashboardSection(
            CreditFacilitiesOverview(listOf(firstFacility, secondFacility), emptyList(), Currency.SAR),
            // A shared evidence row must count once in the aggregate, even if two card scopes include it.
            mapOf(purchase.id to setOf("${bank.id}:2222", "${bank.id}:3333")), emptyMap(),
        )
        val result = DashboardSpendingTrendsBuilder.build(context, accountSection, cards)
        assertEquals("180.00", result.overall.totalSpending.amount.toPlainString())
        assertEquals("130.00", result.accounts.totalSpending.amount.toPlainString())
        assertEquals("150.00", result.creditCards.totalSpending.amount.toPlainString())
        assertEquals("130.00", result.byAccount.getValue("${bank.id}:1111").totalSpending.amount.toPlainString())
        assertEquals("0.00", result.byAccount.getValue("${otherBank.id}:1111").totalSpending.amount.toPlainString())
        assertEquals("100.00", result.byCard.getValue("${bank.id}:3333").totalSpending.amount.toPlainString())
        assertEquals("50.00", result.byCard.getValue("${otherBank.id}:2222").totalSpending.amount.toPlainString())
    }

    @Test
    fun debitScope_usesPersistedAttributionWithoutRequiringAnAccountInTheSms() {
        val purchase = transaction("googlepay", "120", null)
        val context = context(emptyList(), listOf(purchase))
        val debit = DebitCardOverview(bank, "2210", "Mada", null, null, null, SignedMoneyAmount.zero(Currency.SAR), null)
        val cards = CardsDashboardSection(CreditFacilitiesOverview(emptyList(), listOf(debit), Currency.SAR),
            emptyMap(), mapOf(purchase.id to setOf("${bank.id}:2210")))
        val result = DashboardSpendingTrendsBuilder.build(context, AccountsDashboardProjection.build(context), cards)
        assertEquals("120.00", result.byCard.getValue("${bank.id}:2210").totalSpending.amount.toPlainString())
        assertEquals("0.00", result.creditCards.totalSpending.amount.toPlainString())
    }

    private fun context(accounts: List<AccountRegistryEntry>, transactions: List<FinancialTransaction>) = DashboardProjectionContext(
        period, Instant.parse("2026-07-23T00:00:00Z"), date.plusDays(2), DashboardEvidence.EMPTY,
        transactions, emptyMap(), 0, accounts,
        accounts.map { FinancialContainerIdFactory.accountId(it.bank, it.maskedNumber) }.toSet(),
        accounts.map { it.maskedNumber }.toSet(), emptyList(), emptyList(),
        DebitCardScopeFacts(emptySet(), emptyMap()), Locale.ENGLISH, ZoneId.of("Asia/Riyadh"), Currency.SAR,
    )

    private fun transaction(id: String, amount: String, source: String?) = FinancialTransaction(
        id, FinancialTransactionType.EXPENSE, Money.of(amount, Currency.SAR), Instant.parse("2026-07-20T07:00:00Z"),
        source, null, null, null, null, emptyList(),
    )

    private fun row(bank: Bank, last4: String) = CreditCardDashboardRow(bank, last4,
        SignedMoneyAmount.zero(Currency.SAR), SignedMoneyAmount.zero(Currency.SAR), SignedMoneyAmount.zero(Currency.SAR), null, null)

    private fun facility(primary: CreditCardDashboardRow, supplementaries: List<CreditCardDashboardRow> = emptyList()) =
        CreditFacilityOverview(primary.bank, primary, supplementaries, null,
            SignedMoneyAmount.zero(Currency.SAR), SignedMoneyAmount.zero(Currency.SAR), null, null, Currency.SAR)
}
