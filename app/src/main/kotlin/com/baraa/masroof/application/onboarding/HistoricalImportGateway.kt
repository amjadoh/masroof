package com.baraa.masroof.application.onboarding

import java.time.LocalDate

data class OnboardingImportRequest(val bankIds: Set<String>, val from: LocalDate, val through: LocalDate)

fun interface HistoricalImportGateway {
    suspend fun scan(request: OnboardingImportRequest): HistoricalImportResult
}
