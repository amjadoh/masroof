package com.baraa.masroof.lab.beta.application

import java.time.*

/** Same approved salary cycle as production: 27th, Friday early, Saturday late. */
object BetaSalaryPeriodPolicy {
    fun start(month: YearMonth,day: Int=27): LocalDate {
        require(day in 1..31)
        val nominal=month.atDay(minOf(day,month.lengthOfMonth()))
        return if(day!=27) nominal else when(nominal.dayOfWeek) {
            DayOfWeek.FRIDAY -> nominal.minusDays(1)
            DayOfWeek.SATURDAY -> nominal.plusDays(1)
            else -> nominal
        }
    }
    fun monthContaining(date: LocalDate,day: Int=27): YearMonth {
        val month=YearMonth.from(date)
        return if(date<start(month,day)) month.minusMonths(1) else month
    }
}
