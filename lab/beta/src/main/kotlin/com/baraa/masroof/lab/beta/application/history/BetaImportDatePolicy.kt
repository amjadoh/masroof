package com.baraa.masroof.lab.beta.application.history

import java.time.LocalDate
import java.time.ZoneId

enum class BetaImportDateOption { CURRENT_MONTH_START, LAST_30_DAYS, LAST_27TH, CUSTOM }
object BetaImportDatePolicy {
    val zone: ZoneId = ZoneId.of("Asia/Riyadh")
    fun date(option: BetaImportDateOption, today: LocalDate, custom: LocalDate? = null): LocalDate = when(option) {
        BetaImportDateOption.CURRENT_MONTH_START -> today.withDayOfMonth(1)
        BetaImportDateOption.LAST_30_DAYS -> today.minusDays(30)
        BetaImportDateOption.LAST_27TH -> if(today.dayOfMonth >= 27) today.withDayOfMonth(27) else today.minusMonths(1).withDayOfMonth(27)
        BetaImportDateOption.CUSTOM -> requireNotNull(custom)
    }
}
