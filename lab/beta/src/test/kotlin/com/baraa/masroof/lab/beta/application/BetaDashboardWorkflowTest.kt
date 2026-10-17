package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.dashboard.producer.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class BetaDashboardWorkflowTest {
    @Test fun riyadhMonthIsHalfOpenAndDoesNotInventSalaryOrStatementPeriods() = runBlocking {
        var calls = 0
        val workflow = BetaDashboardWorkflow({ query, periods ->
            calls++; assertEquals(Instant.parse("2026-09-30T21:00:00Z"), query.from)
            assertEquals(Instant.parse("2026-10-31T21:00:00Z"), query.until)
            assertEquals(PeriodKind.CALENDAR_MONTH, periods.calendar!!.kind)
            assertNull(periods.salary); assertTrue(periods.statements.isEmpty())
            DashboardProduction(DashboardSnapshot(query, emptyList(), emptyList(), emptyList()), emptySet(), null)
        }, Clock.fixed(Instant.parse("2026-09-30T22:00:00Z"), ZoneOffset.UTC))
        assertEquals(YearMonth.of(2026, 10), workflow.currentMonth())
        workflow.load(workflow.currentMonth()); assertEquals(1, calls)
    }

    @Test fun calendarScopeRespectsLeapYearAndDstInsteadOfFixedSeconds() {
        val workflow = BetaDashboardWorkflow({ _, _ -> error("not read") }, zone = ZoneId.of("America/New_York"))
        val march = workflow.query(YearMonth.of(2024, 3))
        assertEquals(Duration.ofHours(31 * 24L - 1), Duration.between(march.from, march.until))
        val leap = workflow.query(YearMonth.of(2024, 2))
        assertEquals(Duration.ofDays(29), Duration.between(leap.from, leap.until))
    }

    @Test fun scopeMismatchAndReadFailuresNeverReturnAnOldOrEmptySuccess() = runBlocking {
        val mismatched = BetaDashboardWorkflow({ query, _ ->
            DashboardProduction(DashboardSnapshot(query.copy(until = query.until.plusSeconds(1)), emptyList(), emptyList(), emptyList()), emptySet(), null)
        })
        try { mismatched.load(YearMonth.of(2026, 10)); fail("Different scope must fail closed") } catch (_: IllegalStateException) { }
        val failed = BetaDashboardWorkflow({ _, _ -> throw IllegalArgumentException("synthetic read failure") })
        try { failed.load(YearMonth.of(2026, 10)); fail("Read failure must propagate") } catch (_: IllegalArgumentException) { }
    }
}
