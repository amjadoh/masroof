package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.dashboard.producer.DashboardPeriods
import com.baraa.masroof.lab.finance.dashboard.producer.DashboardProduction
import com.baraa.masroof.lab.finance.value.Currency
import com.baraa.masroof.lab.finance.value.Environment
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId

/** Salary display scope follows the configured/default cycle; statement dates still need evidence. */
class BetaDashboardWorkflow(
    private val read: suspend (DashboardQuery, DashboardPeriods) -> DashboardProduction,
    private val clock: Clock = Clock.systemUTC(),
    val zone: ZoneId = ZoneId.of("Asia/Riyadh"),
    val periodStartDay: Int = 27,
) {
    fun withPeriodStartDay(day: Int): BetaDashboardWorkflow {
        require(day in 1..31)
        return BetaDashboardWorkflow(read,clock,zone,day)
    }
    fun currentMonth(): YearMonth = BetaSalaryPeriodPolicy.monthContaining(java.time.LocalDate.now(clock.withZone(zone)),periodStartDay)

    fun query(month: YearMonth): DashboardQuery = DashboardQuery(Environment.BETA,
        BetaSalaryPeriodPolicy.start(month,periodStartDay).atStartOfDay(zone).toInstant(),
        BetaSalaryPeriodPolicy.start(month.plusMonths(1),periodStartDay).atStartOfDay(zone).toInstant(), zone, Currency.SAR)

    suspend fun load(month: YearMonth): DashboardProduction {
        val query = query(month)
        val calendar=java.time.YearMonth.from(query.until.minusNanos(1).atZone(zone))
        val result = read(query, DashboardPeriods(salary = DisplayPeriod(query.from,query.until,query.zone,PeriodKind.SALARY,null), calendar = DisplayPeriod(calendar.atDay(1).atStartOfDay(zone).toInstant(),calendar.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
            query.zone, PeriodKind.CALENDAR_MONTH, null)))
        check(result.snapshot.query == query) { "Dashboard scope changed during read" }
        return result
    }
}
