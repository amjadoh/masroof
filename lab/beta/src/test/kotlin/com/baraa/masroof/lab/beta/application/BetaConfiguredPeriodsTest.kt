package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.value.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class BetaConfiguredPeriodsTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private fun query(month: String): DashboardQuery {
        val at = YearMonth.parse(month)
        return DashboardQuery(Environment.BETA, at.atDay(1).atStartOfDay(zone).toInstant(), at.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(), zone, Currency.SAR)
    }
    private fun period(month: String, today: String, day: Int = 27): DisplayPeriod = BetaConfiguredPeriods.periods(
        BetaDashboardConfigurationDraft(salary = BetaSalaryCycle(day)), query(month), LocalDate.parse(today).atTime(12, 0).atZone(zone).toInstant()).salary!!
    private fun assertDates(start: String, end: String, actual: DisplayPeriod) {
        assertEquals(LocalDate.parse(start), actual.from.atZone(zone).toLocalDate())
        assertEquals(LocalDate.parse(end), actual.until.atZone(zone).toLocalDate())
    }
    @Test fun currentAndNavigatedCycles() {
        assertDates("2026-10-27", "2026-11-26", period("2026-10", "2026-10-28"))
        assertDates("2026-09-27", "2026-10-27", period("2026-09", "2026-10-28"))
        assertDates("2026-11-26", "2026-12-27", period("2026-11", "2026-10-28"))
        assertDates("2026-09-27", "2026-10-27", period("2026-10", "2026-10-26"))
        assertDates("2026-10-27", "2026-11-26", period("2026-10", "2026-10-27"))
    }
    @Test fun weekendAndShortMonth() {
        // February27 is Friday; June27 is Saturday. Only configured day27 shifts weekends.
        assertDates("2026-02-26", "2026-03-26", period("2026-02", "2026-02-26"))
        assertDates("2026-05-27", "2026-06-28", period("2026-06", "2026-06-27"))
        assertDates("2026-06-28", "2026-07-27", period("2026-06", "2026-06-28"))
        assertDates("2026-02-28", "2026-03-31", period("2026-02", "2026-02-28", 31))
        assertDates("2024-02-29", "2024-03-31", period("2024-02", "2024-02-29", 31))
        assertDates("2026-02-27", "2026-03-27", run {
            // Statement day27 stays Friday, unlike salary day27.
            val facility = com.baraa.masroof.lab.finance.asset.AssetId(Environment.BETA, BankId("BANK_ALJAZIRA"), "facility")
            BetaConfiguredPeriods.periods(BetaDashboardConfigurationDraft(statements = listOf(BetaFacilityCycle(facility, 27))),
                query("2026-02"), Instant.parse("2026-02-27T12:00:00Z")).statements.getValue(facility)
        })
    }
    @Test fun occurrencesUseHalfOpenDueMonthAndExplicitPaidDecisions() {
        val salary = period("2026-02", "2026-02-28", 31)
        val draft = BetaDashboardConfigurationDraft(commitments = listOf(
            BetaRecurringCommitment("rent", "Rent", Money.of("2500", Currency.SAR), 31, YearMonth.of(2026, 2), paidMonths = setOf(YearMonth.of(2026, 2))),
            BetaRecurringCommitment("closed", "Closed", Money.of("10", Currency.SAR), 1, YearMonth.of(2025, 1), YearMonth.of(2026, 1))))
        val rows = BetaConfiguredPeriods.occurrences(draft, salary)
        assertEquals(1, rows.size)
        assertEquals(LocalDate.of(2026, 2, 28), rows.single().expectedDate)
        assertEquals(PaymentStatus.PAID, rows.single().status)
        assertEquals("user:rent:2026-02", rows.single().occurrenceId)
        val next = BetaConfiguredPeriods.occurrences(draft, period("2026-03", "2026-02-28", 31))
        assertEquals(PaymentStatus.UNPAID, next.single().status)
        assertEquals(LocalDate.of(2026, 3, 31), next.single().expectedDate)
        assertTrue(BetaConfiguredPeriods.occurrences(draft, null).isEmpty())
    }
}
