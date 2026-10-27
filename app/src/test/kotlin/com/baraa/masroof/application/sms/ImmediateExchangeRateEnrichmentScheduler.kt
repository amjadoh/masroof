package com.baraa.masroof.application.sms

import kotlinx.coroutines.CancellationException

/**
 * Runs enrichment inline for JVM tests without WorkManager.
 */
class ImmediateExchangeRateEnrichmentScheduler(
    private val enricher: PendingExchangeRateEnricher,
) : ExchangeRateEnrichmentScheduler {
    override fun schedule() {
        try {
            kotlinx.coroutines.runBlocking { enricher.enrichPending() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort, same as the live processing boundary.
        }
    }
}
