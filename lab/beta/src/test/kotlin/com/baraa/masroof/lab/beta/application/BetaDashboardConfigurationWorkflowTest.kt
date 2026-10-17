package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.value.*
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class BetaDashboardConfigurationWorkflowTest {
    private val month = YearMonth.of(2026, 10)
    private val base = BetaDashboardConfigurationDraft(commitments = listOf(BetaRecurringCommitment("rent", "Rent", Money.of("1200", Currency.SAR), 31, month)))
    @Test fun qualifiedStatementChangesPreserveSameLocalIdAtAnotherBankAndExistingCommitments() {
        val a = AssetId(Environment.BETA, BankId("A"), "same")
        val b = AssetId(Environment.BETA, BankId("B"), "same")
        val first = BetaDashboardConfigurationWorkflow.statement(base, a, "31")
        val second = BetaDashboardConfigurationWorkflow.statement(first, b, "15")
        assertEquals(setOf(a, b), second.statements.map { it.facilityId }.toSet())
        val cleared = BetaDashboardConfigurationWorkflow.statement(second, a, "")
        assertEquals(listOf(BetaFacilityCycle(b, 15)), cleared.statements)
        assertEquals(base.commitments, cleared.commitments)
        assertNull(BetaDashboardConfigurationWorkflow.salary(base, "").salary)
    }
    @Test fun paidDecisionIsAnExplicitDueMonthAndRangeEditsCannotSilentlyDiscardIt() {
        val paid = BetaDashboardConfigurationWorkflow.paid(base, "rent", month, true)
        assertEquals(setOf(month), paid.commitments.single().paidMonths)
        assertEquals(emptySet<YearMonth>(), BetaDashboardConfigurationWorkflow.paid(paid, "rent", month, false).commitments.single().paidMonths)
        assertThrows(IllegalArgumentException::class.java) { BetaDashboardConfigurationWorkflow.commitment(paid, "rent", "Rent", "1300", "31", "2026-11", "") }
        val changed = BetaDashboardConfigurationWorkflow.commitment(paid, "rent", "New rent", "1300", "30", "2026-10", "2027-02")
        assertEquals(setOf(month), changed.commitments.single().paidMonths)
        assertEquals("rent", changed.commitments.single().id)
    }
    @Test fun invalidAmountsDaysAndReversedDatesNeverCreateAConfigDraft() {
        for (amount in listOf("0", "-1", "1.001", "NaN", "1e9", "")) assertThrows(IllegalArgumentException::class.java) {
            BetaDashboardConfigurationWorkflow.commitment(base, null, "Rent", amount, "1", "2026-10", "")
        }
        for (day in listOf("0", "32", "x")) assertThrows(IllegalArgumentException::class.java) { BetaDashboardConfigurationWorkflow.salary(base, day) }
        assertThrows(IllegalArgumentException::class.java) { BetaDashboardConfigurationWorkflow.commitment(base, null, "Rent", "1", "1", "2026-10", "2026-09") }
        assertEquals(1, base.commitments.size)
    }
}
