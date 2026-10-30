package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionListWorkflowTest {
    private val workflow = TransactionListWorkflow()

    @Test fun filteredForeignRowsDoNotInventAPrimaryCurrencyTotal() {
        val rows = listOf(row("sar", "local", "10"), row("usd", "foreign", "20", Currency.USD))
        val projection = workflow.project(rows, TransactionListQuery(searchQuery = "foreign"))
        assertEquals(listOf(1), projection.selection.indices)
        assertNull(projection.selection.totalAmount)
    }

    @Test fun selectionKeepsInputOrderAndDoesNotConfuseDuplicateDisplayIds() {
        val rows = listOf(row("same", "alpha", "10"), row("same", "beta", "20"))
        val projection = workflow.project(rows, TransactionListQuery(searchQuery = "beta"))
        assertEquals(listOf(1), projection.selection.indices)
        assertEquals(Money.of("20", Currency.SAR), projection.selection.totalAmount)
    }

    private fun row(id: String, title: String, amount: String, currency: Currency = Currency.SAR) =
        TransactionListRow(id, title, Money.of(amount, currency), FinancialTransactionType.EXPENSE,
            null, null, null, title)
}
