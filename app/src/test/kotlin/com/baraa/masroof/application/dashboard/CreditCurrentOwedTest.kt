package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class CreditCurrentOwedTest {
    private val due = StatementDueSnapshot(
        amount = Money(BigDecimal("1000.00"), Currency.SAR),
        updatedAt = Instant.parse("2026-10-01T09:00:00Z"),
        dueDate = LocalDate.parse("2026-10-25"),
    )

    @Test
    fun owedNow_isDuePlusSpendingAfterStatementMinusPayments() {
        val owed = estimateCurrentOwed(
            due,
            listOf(row("1111", spending = "250.00", payments = "600.00"), row("2222", spending = "50.00", payments = "0.00")),
        )

        assertEquals(SignedMoneyAmount(BigDecimal("700.00"), Currency.SAR), owed)
    }

    @Test
    fun overpayment_isNegativeCreditBalance() {
        val owed = estimateCurrentOwed(due, listOf(row("1111", spending = "0.00", payments = "1200.00")))

        assertEquals(SignedMoneyAmount(BigDecimal("-200.00"), Currency.SAR), owed)
    }

    @Test
    fun noStatementDue_isUnknownNotGuessed() {
        assertNull(estimateCurrentOwed(null, listOf(row("1111", spending = "300.00", payments = "0.00"))))
    }

    private fun row(last4: String, spending: String, payments: String) = CreditCardDashboardRow(
        bank = Bank.fromId("BANK_SAB"),
        last4 = last4,
        statementSpendingNet = SignedMoneyAmount(BigDecimal(spending), Currency.SAR),
        salaryPeriodSpendingNet = SignedMoneyAmount(BigDecimal(spending), Currency.SAR),
        statementPeriodLabel = null,
        snapshot = null,
        paymentsSinceStatement = Money(BigDecimal(payments), Currency.SAR),
    )
}
