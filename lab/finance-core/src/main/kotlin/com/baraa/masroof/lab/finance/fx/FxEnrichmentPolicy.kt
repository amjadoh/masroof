package com.baraa.masroof.lab.finance.fx

import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.projection.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.text.Normalizer
import java.time.*
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CancellationException

/** Exact stored columns, including a half-written pair. Currency belongs to the persisted amount. */
data class StoredFxColumns(val currency: Currency, val rate: BigDecimal?, val source: String?)

/** Persisted typed facts only: no message body, parsing, ownership resolution or bank-zone map.
 * Trusted adapters supply the bank policy's fixed zone and the transaction zone linked to this event.
 */
data class FxEvidenceFact(
    val environment: Environment,
    val id: String,
    val amountCurrency: Currency? = null,
    val labeledForeignCurrency: Currency? = null,
    val merchant: String? = null,
    val exchangeRate: BigDecimal? = null,
    val internationalFee: Money? = null,
    val occurredAtLocal: LocalDateTime? = null,
    val occurredAt: Instant? = null,
    val receivedAt: Instant? = null,
    val fixedBankZone: ZoneId? = null,
    val persistedTransactionZone: ZoneId? = null,
)

data class FxEnrichmentRequest(
    val input: FinancialClassificationInput,
    val orderedLinkedEvidenceIds: List<String>,
    val evidence: List<FxEvidenceFact>,
    val merchant: String? = null,
    val storedFx: StoredFxColumns = StoredFxColumns(input.amount.currency, input.appliedFx?.rateToSar, input.appliedFx?.source),
    /** Exact persisted column; null/invalid can use only the linked bank's explicitly supplied zone. */
    val storedZoneId: String? = input.time.zone.id,
)

fun interface FxMarketRateProvider {
    suspend fun rateFor(currency: Currency, date: LocalDate): BigDecimal?
}

/** A bound atomic pair proposal, not a write, revision increment, or financial authorization. */
data class FxPairProposal(
    val key: PostingKey,
    val revision: PostingRevision,
    val nativeCurrency: Currency,
    val evidenceIds: Set<String>,
    val expectedColumns: StoredFxColumns,
    val appliedFx: AppliedFx,
)

enum class FxEnrichmentBlocker {
    FINANCIAL_REVIEW, INPUT_CONFLICT, CURRENCY_CONFLICT, UNKNOWN_STORED_SOURCE,
    INVALID_RATE, MISSING_MARKET_ZONE, MISSING_RATE, MARKET_FAILURE, INVALID_FEE,
}

sealed interface FxEnrichmentResult {
    data class Unavailable(val key: PostingKey, val reason: FxEnrichmentBlocker) : FxEnrichmentResult
    data class Ready(val projection: SarRowProjection.Available, val proposal: FxPairProposal?) : FxEnrichmentResult
}

/** Offline selection policy; the injected provider may be a reviewed cache/fake. This module never
 * constructs a network client, writes a ledger, upgrades parse authority or consults handset time.
 */
class FxEnrichmentPolicy(private val market: FxMarketRateProvider) {
    suspend fun resolve(request: FxEnrichmentRequest): FxEnrichmentResult {
        val input = freeze(request.input)
        val linkedIds = request.orderedLinkedEvidenceIds.toList()
        val evidence = request.evidence.toList()
        fun unavailable(reason: FxEnrichmentBlocker) = FxEnrichmentResult.Unavailable(input.key, reason)
        if (linkedIds.toSet() != input.evidenceIds || linkedIds.distinct().size != linkedIds.size ||
            evidence.any { it.environment != input.key.environment || it.id.isBlank() } ||
            evidence.groupBy { it.id }.values.any { it.distinct().size > 1 }) {
            return unavailable(FxEnrichmentBlocker.INPUT_CONFLICT)
        }
        if (request.storedFx.currency != input.amount.currency) return unavailable(FxEnrichmentBlocker.CURRENCY_CONFLICT)
        if (input.appliedFx != null && (request.storedFx.rate != input.appliedFx.rateToSar || request.storedFx.source != input.appliedFx.source)) {
            return unavailable(FxEnrichmentBlocker.INPUT_CONFLICT)
        }
        if (FinancialImpactClassifier.classify(input) !is FinancialClassification.Classified) {
            return unavailable(FxEnrichmentBlocker.FINANCIAL_REVIEW)
        }
        if (input.amount.currency == Currency.SAR) {
            return FxEnrichmentResult.Ready(PersistedSarProjection.project(input) as SarRowProjection.Available, null)
        }
        val byId = evidence.associateBy { it.id }
        // Preserve original linked-record ordering, including first-record-without-rate behavior.
        val linked = linkedIds.firstNotNullOfOrNull(byId::get)
        val complete = request.storedFx.rate != null && request.storedFx.source != null
        val pair: AppliedFx
        if (complete) {
            val source = requireNotNull(request.storedFx.source)
            if (source !in sources) return unavailable(FxEnrichmentBlocker.UNKNOWN_STORED_SOURCE)
            val rate = requireNotNull(request.storedFx.rate)
            if (rate.signum() <= 0) return unavailable(FxEnrichmentBlocker.INVALID_RATE)
            pair = AppliedFx(rate, source)
        } else if (linked?.exchangeRate != null) {
            if (linked.exchangeRate.signum() <= 0) return unavailable(FxEnrichmentBlocker.INVALID_RATE)
            pair = AppliedFx(linked.exchangeRate, "SMS")
        } else {
            // Null transaction merchant permits linked-event fallback; blank is deliberately not null.
            val merchant = request.merchant ?: linkedIds.firstNotNullOfOrNull { byId[it]?.merchant }
            val historical = historicalRate(merchant, input.amount.currency, input.time.instant, evidence)
            if (historical != null) {
                if (historical.signum() <= 0) return unavailable(FxEnrichmentBlocker.INVALID_RATE)
                pair = AppliedFx(historical, "HISTORICAL_MERCHANT")
            } else {
                val zone = request.storedZoneId?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                    ?: linked?.fixedBankZone ?: return unavailable(FxEnrichmentBlocker.MISSING_MARKET_ZONE)
                if (zone != input.time.zone) return unavailable(FxEnrichmentBlocker.INPUT_CONFLICT)
                val rate = try { market.rateFor(input.amount.currency, input.time.instant.atZone(zone).toLocalDate()) }
                catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    return unavailable(FxEnrichmentBlocker.MARKET_FAILURE)
                } ?: return unavailable(FxEnrichmentBlocker.MISSING_RATE)
                if (rate.signum() <= 0) return unavailable(FxEnrichmentBlocker.INVALID_RATE)
                pair = AppliedFx(rate, "MARKET")
            }
        }
        val derived = input.copy(appliedFx = pair)
        val fee = linked?.internationalFee?.let { LinkedInternationalFee(input.key, input.revision, linked.id, it) }
        val projected = PersistedSarProjection.project(derived, fee)
        if (projected !is SarRowProjection.Available) return unavailable(FxEnrichmentBlocker.INVALID_FEE)
        val proposal = if (complete) null else FxPairProposal(input.key, input.revision, input.amount.currency, input.evidenceIds, request.storedFx, pair)
        return FxEnrichmentResult.Ready(projected, proposal)
    }

    private data class HistoricalRow(val merchant: String, val currency: Currency, val id: String,
        val rate: BigDecimal, val at: Instant)

    private fun historicalRate(merchant: String?, currency: Currency, at: Instant, facts: List<FxEvidenceFact>): BigDecimal? {
        val key = merchant?.let(::merchantKey)?.takeIf { it.isNotBlank() } ?: return null
        val rows = facts.mapNotNull { fact ->
            val merchantKey = fact.merchant?.let(::merchantKey)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val foreign = fact.amountCurrency?.takeIf { it != Currency.SAR }
                ?: fact.labeledForeignCurrency?.takeIf { it != Currency.SAR } ?: return@mapNotNull null
            val rate = fact.exchangeRate ?: return@mapNotNull null
            val instant = if (fact.occurredAtLocal != null) {
                val zone = fact.fixedBankZone ?: fact.persistedTransactionZone ?: return@mapNotNull null
                fact.occurredAtLocal.atZone(zone).toInstant()
            } else fact.occurredAt ?: fact.receivedAt ?: return@mapNotNull null
            HistoricalRow(merchantKey, foreign, fact.id, rate, instant)
        }.distinctBy { Triple(it.merchant, it.currency, it.id) }
        return rows.filter { it.merchant == key && it.currency == currency && it.at <= at && it.at >= at.minus(Duration.ofDays(30)) }
            .maxWithOrNull(compareBy<HistoricalRow> { it.at }.thenBy { it.id })?.rate
    }

    private fun merchantKey(name: String) = Normalizer.normalize(name, Normalizer.Form.NFC).trim()
        .replace(Regex("\\s+"), " ").replace(Regex("\\s*([,،._-])\\s*"), "$1").lowercase(Locale.ROOT)

    private fun freeze(input: FinancialClassificationInput) = input.copy(
        assets = Collections.unmodifiableMap(LinkedHashMap(input.assets)),
        evidenceIds = Collections.unmodifiableSet(LinkedHashSet(input.evidenceIds)),
        unresolved = Collections.unmodifiableSet(LinkedHashSet(input.unresolved)))

    private val sources = setOf("SMS", "HISTORICAL_MERCHANT", "MARKET")
}
