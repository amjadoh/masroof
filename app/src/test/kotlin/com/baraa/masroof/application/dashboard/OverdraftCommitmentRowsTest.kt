package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Synthetic accounts. */
class OverdraftCommitmentRowsTest {
    private fun balance(masked: String, opening: String, movement: String) = AccountBalance(
        bank = Bank("BANK_SNB"),
        maskedNumber = masked,
        opening = SignedMoneyAmount(BigDecimal(opening).setScale(2), Currency.SAR),
        asOfDate = LocalDate.parse("2026-11-01"),
        movementSinceOpening = SignedMoneyAmount(BigDecimal(movement).setScale(2), Currency.SAR),
    )

    @Test
    fun onlyOverdrawnAccounts_becomeUnpaidCommitmentsForTheirShortfall() {
        val rows = CommitmentsOverviewBuilder.buildOverdraftRows(
            listOf(balance("1234", "2000", "-10"), balance("5678", "100", "-440.25"), balance("9012", "0", "0")),
            Currency.SAR,
        )

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(CommitmentDashboardSource.ACCOUNT_OVERDRAFT, row.source)
        assertEquals(CommitmentPaymentStatus.UNPAID, row.status)
        assertEquals("5678", row.displayName)
        assertEquals(BigDecimal("340.25"), row.amount.amount)
        assertEquals("BANK_SNB:5678", row.accountKey)
    }
}
