package com.baraa.masroof.lab.finance.fx

import com.baraa.masroof.application.dashboard.ForeignSarMarketRateProvider
import com.baraa.masroof.application.dashboard.TransactionSarEquivalentResolver
import com.baraa.masroof.domain.assembly.BankTransactionTimePolicy
import com.baraa.masroof.core.money.Currency as OldCurrency
import com.baraa.masroof.core.money.Money as OldMoney
import com.baraa.masroof.domain.model.*
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.*
import java.util.TimeZone
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Executes the entire unchanged hash-pinned original resolver/index. Providers only record inputs;
 * neither oracle nor candidate constructs the compiled original HTTP implementation.
 */
class FxEnrichmentParityTest {
    private val now = Instant.parse("2026-08-17T21:30:00Z")
    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val account = OwnedAsset(AssetId(Environment.BETA, BankId("BANK_ALJAZIRA"), "account"), AssetKind.ACCOUNT)
    private fun input(currency: Currency = Currency.USD, type: FinancialType = FinancialType.EXPENSE,
        fx: AppliedFx? = null, ids: Set<String> = setOf("own")) = FinancialClassificationInput(
        PostingKey(Environment.BETA, "tx"), PostingRevision(0, 1), type, Money.of("23", currency),
        TransactionTime(now, riyadh),
        if (type == FinancialType.REFUND) FinancialEndpoint.External else FinancialEndpoint.Owned(account.id),
        if (type == FinancialType.REFUND) FinancialEndpoint.Owned(account.id) else FinancialEndpoint.External,
        mapOf(account.id to account), ids, true, appliedFx = fx)
    private fun fact(id: String = "own", rate: String? = null, merchant: String? = null,
        currency: Currency? = Currency.USD, at: Instant? = now.minusSeconds(60),
        local: LocalDateTime? = null, fixedZone: ZoneId? = BankTransactionTimePolicy.fixedZone(Bank.BANK_ALJAZIRA),
        persistedZone: ZoneId? = null, fee: String? = null, labeled: Currency? = null, received: Instant? = null) =
        FxEvidenceFact(Environment.BETA, id, currency, labeled, merchant, rate?.let(::BigDecimal),
            fee?.let { Money.of(it, Currency.SAR) }, local, at, received, fixedZone, persistedZone)
    private fun request(facts: List<FxEvidenceFact> = listOf(fact()), merchant: String? = "SHOP",
        input: FinancialClassificationInput = input(), zone: String? = input.time.zone.id,
        ordered: List<String> = input.evidenceIds.toList(), stored: StoredFxColumns = StoredFxColumns(
            input.amount.currency, input.appliedFx?.rateToSar, input.appliedFx?.source)) =
        FxEnrichmentRequest(input.copy(time = input.time.copy(zone = zone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: input.time.zone)), ordered, facts.map { row -> if (row.id in ordered) row.copy(
            persistedTransactionZone = zone?.let { runCatching { ZoneId.of(it) }.getOrNull() }) else row }, merchant, stored, zone)

    private fun oldTransaction(request: FxEnrichmentRequest) = FinancialTransaction("tx",
        FinancialTransactionType.valueOf(request.input.storedType.name),
        OldMoney.of(request.input.amount.amount, OldCurrency.valueOf(request.input.amount.currency.name)), now,
        "account", null, request.merchant, null, null, request.orderedLinkedEvidenceIds,
        request.storedFx.rate, request.storedFx.source?.let(ExchangeRateSource::valueOf), request.storedZoneId)

    private fun oldRecord(fact: FxEvidenceFact): ParsedEventRecord {
        val event = ParsedEvent(fact.id, "raw-${fact.id}",
            if (fact.fixedBankZone == riyadh) Bank.BANK_ALJAZIRA else Bank("D360"), MessageFamily.PURCHASE,
            null, fact.amountCurrency?.let { OldMoney.of("1", OldCurrency.valueOf(it.name)) },
            null, null, null, null, fact.merchant, null, fact.occurredAt, null, Confidence(1.0), ParseStatus.SUCCESS)
        return ParsedEventRecord(event, ParsedEventDetails(occurredAtLocal = fact.occurredAtLocal,
            exchangeRate = fact.exchangeRate, internationalFee = fact.internationalFee?.let { OldMoney.of(it.amount, OldCurrency.SAR) },
            labeledForeignAmount = fact.labeledForeignCurrency?.let { OldMoney.of("1", OldCurrency.valueOf(it.name)) }))
    }

    private suspend fun cross(request: FxEnrichmentRequest, marketRate: String? = "3.75"): FxEnrichmentResult {
        val oldCalls = mutableListOf<Pair<String, LocalDate>>()
        val newCalls = mutableListOf<Pair<String, LocalDate>>()
        val oracle = TransactionSarEquivalentResolver(ForeignSarMarketRateProvider { currency, date ->
            oldCalls += currency.name to date; marketRate?.let(::BigDecimal) })
        // Additional already-stored transaction zones supply exactly the original index's zone map.
        val aux = request.evidence.filter { it.persistedTransactionZone != null && it.id !in request.orderedLinkedEvidenceIds }
            .map { oldTransaction(request).copy(id = "zone-${it.id}", amount = OldMoney.zero(OldCurrency.SAR),
                linkedParsedEventIds = listOf(it.id), appliedExchangeRate = null, exchangeRateSource = null,
                occurredAtZone = it.persistedTransactionZone!!.id) }
        val raw = request.evidence.mapNotNull { fact -> fact.receivedAt?.let {
            "raw-${fact.id}" to RawSms("raw-${fact.id}", "SYNTHETIC", "", it, null, "synthetic-hash") } }.toMap()
        val expected = oracle.resolve(listOf(oldTransaction(request)) + aux, request.evidence.map(::oldRecord), raw)["tx"]
        val actual = FxEnrichmentPolicy(FxMarketRateProvider { currency, date ->
            newCalls += currency.name to date; marketRate?.let(::BigDecimal) }).resolve(request)
        assertEquals("provider currency/date calls", oldCalls, newCalls)
        if (request.input.amount.currency == Currency.SAR) {
            assertNull(expected)
            assertEquals(request.input.amount, (actual as FxEnrichmentResult.Ready).projection.sarAmount)
            assertNull(actual.proposal)
        } else if (expected == null) {
            assertTrue("No original resolution must not become zero/implicit SAR", actual is FxEnrichmentResult.Unavailable)
        } else {
            actual as FxEnrichmentResult.Ready
            assertEquals(expected.source.name, actual.projection.native.input.appliedFx!!.source)
            assertEquals(expected.exchangeRate, actual.projection.native.input.appliedFx!!.rateToSar)
            assertEquals(0, expected.sarAmount.amount.compareTo(actual.projection.sarAmount.amount))
            assertEquals(request.input.amount, actual.projection.native.input.amount)
            assertEquals(request.input.time, actual.projection.native.input.time)
            assertEquals(request.input.authority, actual.projection.native.input.authority)
            assertEquals(request.input.revision, actual.projection.native.input.revision)
            if (request.storedFx.rate != null && request.storedFx.source != null) assertNull(actual.proposal)
            else {
                assertEquals(request.input.key, actual.proposal!!.key)
                assertEquals(request.input.revision, actual.proposal.revision)
                assertEquals(request.input.amount.currency, actual.proposal.nativeCurrency)
                assertEquals(request.input.evidenceIds, actual.proposal.evidenceIds)
                assertEquals(request.storedFx, actual.proposal.expectedColumns)
                assertEquals(actual.projection.native.input.appliedFx, actual.proposal.appliedFx)
            }
        }
        return actual
    }

    @Test fun completeStoredPairWinsEveryFallbackAndOnlySmsPurchaseIncludesFee() = runBlocking<Unit> {
        for (source in ExchangeRateSource.entries) for (type in listOf(FinancialType.EXPENSE, FinancialType.REFUND)) {
            val fx = AppliedFx(BigDecimal("3.7569570000001"), source.name)
            cross(request(listOf(fact(rate = "9.5", fee = "1.99"), fact("history", "8.4", "SHOP")),
                input = input(type = type, fx = fx)))
        }
        Unit
    }

    @Test fun partialRateOrSourceIsRepairedAsOneNewPairFromOwnSms() = runBlocking<Unit> {
        for (columns in listOf(StoredFxColumns(Currency.USD, BigDecimal("999"), null),
            StoredFxColumns(Currency.USD, null, "MARKET"))) {
            val result = cross(request(listOf(fact(rate = "3.756957", fee = "1.99")), stored = columns)) as FxEnrichmentResult.Ready
            assertEquals(AppliedFx(BigDecimal("3.756957"), "SMS"), result.proposal!!.appliedFx)
            cross(request(listOf(fact(), fact("history", "3.7500123456789", "SHOP")), stored = columns))
            cross(request(stored = columns), marketRate = "3.7500123456789")
        }
    }

    @Test fun firstAvailableLinkedRecordWinsEvenWithoutRateRatherThanBestRecord() = runBlocking<Unit> {
        val rows = listOf(fact("first", merchant = "A"), fact("second", "7", "B"))
        cross(request(rows, input = input(ids = setOf("missing", "first", "second")), ordered = listOf("missing", "first", "second")))
    }

    @Test fun firstMissingIdCanBeSkippedAndIdenticalDuplicatesRemainIdempotent() = runBlocking<Unit> {
        val current = input(ids = setOf("missing", "own"))
        cross(request(listOf(fact(rate = "3.756957", fee = "1.99"), fact(rate = "3.756957", fee = "1.99")), input = current,
            ordered = listOf("missing", "own")))
    }

    @Test fun historicalUsesExactMerchantNormalizationForeignCurrencyAndInclusiveAge() = runBlocking<Unit> {
        val rows = listOf(fact(), fact("old", "3.1", "  SHOP  , X ", at = now.minus(Duration.ofDays(30))),
            fact("too-old", "9", "shop,x", at = now.minus(Duration.ofDays(30)).minusNanos(1)),
            fact("future", "8", "shop,x", at = now.plusNanos(1)), fact("alias", "7", "shop,x PAY"),
            fact("wrong-currency", "6", "shop,x", Currency.EUR), fact("native", "5", "shop,x", Currency.SAR))
        val result = cross(request(rows, merchant = "shop,x")) as FxEnrichmentResult.Ready
        assertEquals(BigDecimal("3.1"), result.proposal!!.appliedFx.rateToSar)
    }

    @Test fun historicalLatestInstantThenLexicographicallyGreatestIdAndFirstDuplicatePerGroup() = runBlocking<Unit> {
        val rows = listOf(fact(), fact("a", "3.1", "SHOP", at = now.minusSeconds(2)),
            fact("z", "3.2", "SHOP", at = now.minusSeconds(1)), fact("b", "9", "SHOP", at = now.minusSeconds(1)),
            fact("z", "3.2", "SHOP", at = now.minusSeconds(1)))
        val result = cross(request(rows)) as FxEnrichmentResult.Ready
        assertEquals(BigDecimal("3.2"), result.proposal!!.appliedFx.rateToSar)
    }

    @Test fun historicalCurrencyPrefersForeignEventAmountThenLabeledForeignAmount() = runBlocking<Unit> {
        val rows = listOf(fact(), fact("native-labeled", "3.8", "SHOP", Currency.SAR, labeled = Currency.USD),
            fact("foreign-other", "8", "SHOP", Currency.EUR, at = now, labeled = Currency.USD))
        val result = cross(request(rows)) as FxEnrichmentResult.Ready
        assertEquals(BigDecimal("3.8"), result.proposal!!.appliedFx.rateToSar)
    }

    @Test fun historicalLocalClockPrefersFixedBankZoneOverPersistedZoneAndInbox() = runBlocking<Unit> {
        val rows = listOf(fact(), fact("history", "3.8", "SHOP", at = now.plus(Duration.ofDays(10)),
            local = LocalDateTime.ofInstant(now.minusSeconds(1), riyadh), persistedZone = ZoneId.of("Pacific/Honolulu"),
            received = now.plus(Duration.ofDays(20))))
        val result = cross(request(rows)) as FxEnrichmentResult.Ready
        assertEquals("HISTORICAL_MERCHANT", result.proposal!!.appliedFx.source)
    }

    @Test fun historicalUnknownBankUsesPersistedZoneAndUnzonedLocalNeverFallsBackToEventOrInbox() = runBlocking<Unit> {
        val local = LocalDateTime.ofInstant(now.minusSeconds(1), ZoneId.of("UTC"))
        cross(request(listOf(fact(), fact("zoned", "3.7", "SHOP", local = local,
            fixedZone = null, persistedZone = ZoneId.of("UTC")))))
        val result = cross(request(listOf(fact(), fact("unzoned", "9", "SHOP", at = now.minusSeconds(1),
            local = local, fixedZone = null, received = now.minusSeconds(1))))) as FxEnrichmentResult.Ready
        assertEquals("MARKET", result.proposal!!.appliedFx.source)
    }

    @Test fun historicalFallbackUsesEventInstantThenReceivedInstantOnlyWithoutLocalClock() = runBlocking<Unit> {
        cross(request(listOf(fact(), fact("event", "3.8", "SHOP", at = now.minusSeconds(1), received = now.plusSeconds(1)))))
        cross(request(listOf(fact(), fact("received", "3.9", "SHOP", at = null, received = now.minusSeconds(1)))))
        cross(request(listOf(fact(), fact("none", "9", "SHOP", at = null))), marketRate = null)
    }

    @Test fun nullMerchantUsesLinkedFallbackButBlankDoesNotAndSimilarMerchantIsNotAlias() = runBlocking<Unit> {
        val rows = listOf(fact(merchant = "SHOP"), fact("history", "3.8", "SHOP"))
        assertEquals("HISTORICAL_MERCHANT", (cross(request(rows, merchant = null)) as FxEnrichmentResult.Ready).proposal!!.appliedFx.source)
        assertEquals("MARKET", (cross(request(rows, merchant = "")) as FxEnrichmentResult.Ready).proposal!!.appliedFx.source)
        assertEquals("MARKET", (cross(request(rows, merchant = "SHOP PAY")) as FxEnrichmentResult.Ready).proposal!!.appliedFx.source)
    }

    @Test fun marketDateUsesStoredZoneThenLinkedFixedZoneNeverHandsetZone() = runBlocking<Unit> {
        val previous = TimeZone.getDefault()
        try {
            for (handset in listOf("Pacific/Honolulu", "Asia/Tokyo")) {
                TimeZone.setDefault(TimeZone.getTimeZone(handset))
                cross(request(zone = "America/Los_Angeles"))
                cross(request(zone = "not/a/zone"))
                cross(request(zone = null))
                cross(request(listOf(fact(fixedZone = null)), zone = null), marketRate = "3.75")
            }
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun allCurrenciesRefundsMissingRatesAndNativeSarRunActualWholeResolver() = runBlocking<Unit> {
        for (currency in Currency.entries) for (type in listOf(FinancialType.EXPENSE, FinancialType.REFUND)) {
            cross(request(listOf(fact(rate = "3.756957", fee = "1.99", currency = currency)), input = input(currency, type)))
        }
        cross(request(), marketRate = null)
        cross(request(), marketRate = "3.7500123456789")
        val output = Path.of(System.getProperty("m8.fx.report"))
        Files.createDirectories(output.parent)
        Files.writeString(output, "ACTUAL_FX_RESOLVER_CROSS_RUN: unchanged hash-pinned TransactionSarEquivalentResolver + HistoricalExchangeRateIndex\n" +
            "Synthetic authored cases only; recording providers, no original HTTP client construction/network\n" +
            "JUnit assertions cover stored/SMS/historical/market precedence; partial pairs; exact merchant/currency/30day/time/tie rules; linked ordering; fee/refund/native rules; civil date and missing rates\n" +
            "Scope: source/date selection and SAR conversion; no persistence, full dashboard dues/balances, real holdout or performance acceptance\n")
    }

    @Test fun intentionalGuardsRejectBadCurrencyUnknownStoredSourceInvalidRateAndGateWithoutFallback() = runBlocking<Unit> {
        var calls = 0
        val policy = FxEnrichmentPolicy(FxMarketRateProvider { _, _ -> calls++; BigDecimal.ONE })
        fun reason(result: FxEnrichmentResult) = (result as FxEnrichmentResult.Unavailable).reason
        assertEquals(FxEnrichmentBlocker.CURRENCY_CONFLICT, reason(policy.resolve(request(stored = StoredFxColumns(Currency.EUR, null, null)))))
        assertEquals(FxEnrichmentBlocker.UNKNOWN_STORED_SOURCE, reason(policy.resolve(request(input = input(fx = AppliedFx(BigDecimal.ONE, "unapproved"))))))
        assertEquals(FxEnrichmentBlocker.INVALID_RATE, reason(policy.resolve(request(listOf(fact(rate = "0"))))))
        assertEquals(FxEnrichmentBlocker.FINANCIAL_REVIEW, reason(policy.resolve(request(input = input().copy(validatedParse = false)))))
        assertEquals(FxEnrichmentBlocker.INPUT_CONFLICT, reason(policy.resolve(request(input = input(fx = AppliedFx(BigDecimal.ONE, "SMS")),
            stored = StoredFxColumns(Currency.USD, BigDecimal.TEN, "SMS")))))
        assertEquals(FxEnrichmentBlocker.INPUT_CONFLICT, reason(policy.resolve(request(ordered = listOf("other")))))
        assertEquals(FxEnrichmentBlocker.INPUT_CONFLICT, reason(policy.resolve(request(listOf(fact().copy(environment = Environment.MIGRATION_REHEARSAL))))))
        assertEquals(FxEnrichmentBlocker.INPUT_CONFLICT, reason(policy.resolve(request(listOf(fact(rate = "4"), fact(rate = "5"))))))
        assertEquals(0, calls)
    }

    @Test fun providerFailureIsUnavailableAndCancellationIsNotSwallowed() = runBlocking<Unit> {
        val result = FxEnrichmentPolicy(FxMarketRateProvider { _, _ -> error("synthetic failure") }).resolve(request())
        assertEquals(FxEnrichmentBlocker.MARKET_FAILURE, (result as FxEnrichmentResult.Unavailable).reason)
        try {
            FxEnrichmentPolicy(FxMarketRateProvider { _, _ -> throw CancellationException("synthetic cancellation") }).resolve(request())
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) { assertEquals("synthetic cancellation", expected.message) }
    }

    @Test fun ownSmsRateNeedsMatchingForeignCurrencyButCannotOverrideCompleteStoredPair() = runBlocking<Unit> {
        var calls = 0
        val policy = FxEnrichmentPolicy(FxMarketRateProvider { _, _ -> calls++; BigDecimal.ONE })
        val corrected = input().copy(authority = CorrectionAuthority.USER_FINANCIAL_TYPE,
            validatedParse = false, revision = PostingRevision(1, 2))
        for (row in listOf(fact(rate = "4.3", currency = Currency.EUR),
            fact(rate = "4.3", currency = null), fact(rate = "4.3", currency = Currency.SAR),
            fact(rate = "4.3", currency = Currency.EUR, labeled = Currency.USD))) {
            val result = policy.resolve(request(listOf(row), input = corrected)) as FxEnrichmentResult.Unavailable
            assertEquals(FxEnrichmentBlocker.CURRENCY_CONFLICT, result.reason)
        }
        assertEquals(0, calls)
        cross(request(listOf(fact(rate = "3.756957", currency = Currency.SAR, labeled = Currency.USD))))
        cross(request(listOf(fact(rate = "9.5", currency = Currency.EUR, fee = "1.99")),
            input = input(fx = AppliedFx(BigDecimal("3.756957"), "SMS"))))
    }

    @Test fun applicableForeignFeeIsUnavailableButRefundAndMarketIgnoreIt() = runBlocking<Unit> {
        val bad = fact(rate = "3.75").copy(internationalFee = Money.of("1", Currency.USD))
        val policy = FxEnrichmentPolicy(FxMarketRateProvider { _, _ -> BigDecimal("3.75") })
        assertEquals(FxEnrichmentBlocker.INVALID_FEE, (policy.resolve(request(listOf(bad))) as FxEnrichmentResult.Unavailable).reason)
        assertTrue(policy.resolve(request(listOf(bad), input = input(type = FinancialType.REFUND))) is FxEnrichmentResult.Ready)
        assertTrue(policy.resolve(request(listOf(bad.copy(exchangeRate = null)))) is FxEnrichmentResult.Ready)
    }

    @Test fun providerSuspensionCannotMutateCapturedRequestAndReturnedProposalEvidence() = runBlocking<Unit> {
        val mutableEvidence = mutableListOf(fact())
        val mutableIds = mutableListOf("own")
        val mutableAssets = input().assets.toMutableMap()
        val mutableInputIds = mutableSetOf("own")
        val current = input().copy(assets = mutableAssets, evidenceIds = mutableInputIds)
        val result = FxEnrichmentPolicy(FxMarketRateProvider { _, _ ->
            mutableEvidence.clear(); mutableIds.clear(); mutableAssets.clear(); mutableInputIds.clear(); BigDecimal("3.75")
        }).resolve(request(mutableEvidence, input = current).copy(orderedLinkedEvidenceIds = mutableIds, evidence = mutableEvidence)) as FxEnrichmentResult.Ready
        assertEquals(setOf("own"), result.proposal!!.evidenceIds)
        assertEquals(1, result.projection.native.input.assets.size)
        assertThrows(UnsupportedOperationException::class.java) { (result.proposal.evidenceIds as MutableSet).clear() }
    }
}
