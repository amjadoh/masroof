package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.LoanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoanTotalsCompletenessTest {
    @Test
    fun missingBalance_doesNotDisplayPartialTotalAsComplete() {
        assertNull(overview(Money.of("1000", Currency.SAR), null).aggregateRemainingBalance())
    }

    @Test
    fun currenciesCannotBeSilentlyCombined() {
        assertNull(overview(Money.of("1000", Currency.SAR), Money.of("100", Currency.USD)).aggregateRemainingBalance())
        assertEquals(Money.of("1500", Currency.SAR), overview(Money.of("1000", Currency.SAR), Money.of("500", Currency.SAR)).aggregateRemainingBalance())
    }

    private fun overview(first: Money?, second: Money?) = LoansOverview(listOf(loan(first), loan(second)), null, Currency.SAR)
    private fun loan(balance: Money?) = LoanOverview(Bank.BANK_ALJAZIRA, LoanType.PERSONAL, "Loan", balance, null,
        salaryPeriodPayment = SignedMoneyAmount.zero(Currency.SAR), salaryPeriodLabel = null)
}
