package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.dashboard.producer.DashboardPeriods
import com.baraa.masroof.lab.finance.dashboard.producer.DashboardProduction
import com.baraa.masroof.lab.finance.value.Currency
import com.baraa.masroof.lab.finance.value.Environment
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId

/** Display scope only. Salary/statement dates require configured authoritative periods. */
class BetaDashboardWorkflow(
    private val read: suspend (DashboardQuery, DashboardPeriods) -> DashboardProduction,
    private val clock: Clock = Clock.systemUTC(),
    val zone: ZoneId = ZoneId.of("Asia/Riyadh"),
) {
    fun currentMonth(): YearMonth = YearMonth.now(clock.withZone(zone))

    fun query(month: YearMonth): DashboardQuery = DashboardQuery(Environment.BETA,
        month.atDay(1).atStartOfDay(zone).toInstant(),
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(), zone, Currency.SAR)

    suspend fun load(month: YearMonth): DashboardProduction {
        val query = query(month)
        val result = read(query, DashboardPeriods(calendar = DisplayPeriod(query.from, query.until,
            query.zone, PeriodKind.CALENDAR_MONTH, null)))
        check(result.snapshot.query == query) { "Dashboard scope changed during read" }
        return result
    }
}
