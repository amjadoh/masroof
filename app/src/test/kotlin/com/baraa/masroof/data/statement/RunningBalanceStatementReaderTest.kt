package com.baraa.masroof.data.statement

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.statement.StatementDirection
import com.baraa.masroof.domain.statement.StatementMatchPolicy
import com.baraa.masroof.domain.statement.StatementParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Fictional statements in the two SNB text layouts, newest movement first. */
class RunningBalanceStatementReaderTest {
    private val account = StatementMatchPolicy.QualifiedAccount(Bank("BANK_SNB"), "0100")

    @Test fun oneLinePerMovementChainsThroughTheBalances() {
        val statement = accepted(
            "Date Description Amount Balance",
            "02/11/2026 شراء عبر نقاط بيع -48.00 952.00",
            "VAT CHRG: 0.00 Charges: 0.00",
            "01/11/2026 حوالة واردة 500.00 1,000.00",
            "31/10/2026 سحب نقدي -100.00 500.00",
        )
        assertEquals(listOf("-100.00", "500.00", "-48.00"), statement.entries.map { signed(it.direction, it.amount.amount) })
        assertEquals(BigDecimal("600.00"), statement.balances.single().opening!!.amount)
        assertEquals(BigDecimal("952.00"), statement.balances.single().closing!!.amount)
    }

    @Test fun aDateWrappedAboveItsAmountsIsOneMovement() {
        val statement = accepted(
            "02/11/2026 تحويل داخلي صادر",
            "BEN ID:1234 -100.00 400.00",
            "01/11/2026 سحب نقدي - صراف آلي",
            "مدى:**MAKKAH 1111 -100.00 500.00",
            "31/10/2026 شراء -25.00 600.00",
        )
        assertEquals(3, statement.entries.size)
        assertEquals(BigDecimal("625.00"), statement.balances.single().opening!!.amount)
    }

    @Test fun aBrokenChainIsRejectedNotGuessed() {
        val result = RunningBalanceStatementReader.read(listOf(
            "02/11/2026 شراء -48.00 952.00",
            "01/11/2026 شراء -10.00 700.00",
        ), account)
        assertTrue(result is StatementParseResult.Rejected)
    }

    private fun accepted(vararg lines: String) =
        (RunningBalanceStatementReader.read(lines.toList(), account) as StatementParseResult.Accepted).statement

    private fun signed(direction: StatementDirection, amount: BigDecimal) =
        (if (direction == StatementDirection.DEBIT) amount.negate() else amount).toPlainString()
}
