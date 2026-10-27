package com.baraa.masroof.application.sms

import com.baraa.masroof.application.onboarding.toHistoricalImportResult
import com.baraa.masroof.domain.model.Bank
import java.time.Clock
import java.time.LocalDate

/** Explicit bank/date selection uses the same capture, deduplication and batch reconciliation path. */
class BankSmsImportWorkflow(
    private val scanner: HistoricalSmsScanner,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val onChanged: () -> Unit = {},
) {
    suspend fun import(bankIds: Set<String>, from: LocalDate, through: LocalDate) = run {
        require(bankIds.isNotEmpty() && bankIds.all { it.isNotBlank() && it != Bank.UNKNOWN.id })
        require(!from.isAfter(through) && !through.isAfter(LocalDate.now(clock)))
        try { scanner.scan(
            receivedAfter = from.atStartOfDay(clock.zone).toInstant(),
            receivedBefore = through.plusDays(1).atStartOfDay(clock.zone).toInstant(),
            bankScopes = bankIds.map(::Bank).toSet(),
        ).toHistoricalImportResult() } finally { onChanged() }
    }
}
