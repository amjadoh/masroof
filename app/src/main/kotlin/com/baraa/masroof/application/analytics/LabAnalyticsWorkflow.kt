package com.baraa.masroof.application.analytics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.baraa.masroof.lab.analytics.*
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.commitment.CommitmentCreationResult
import com.baraa.masroof.application.dashboard.CommitmentPaymentStatus
import com.baraa.masroof.application.maintenance.StartupMaintenanceOutcome
import com.baraa.masroof.application.settings.SettingsCommitmentsWorkflow
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.analytics.*
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.MasroofApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

/** Read-only financial adapter; advisory writes target only AnalyticsDatabase. */
class LabAnalyticsWorkflow(private val container: AppContainer, private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone()) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val mutex = Mutex()
    private val storeDelegate = lazy { AnalyticsDatabase.open(context) }
    private val store by storeDelegate
    private val json = AnalyticsJson.format
    private val mutable = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = mutable.asStateFlow()
    private var started = false
    private val onboardingPrefs = context.getSharedPreferences(com.baraa.masroof.data.preferences.SharedPrefsOnboardingPreferencesRepository.PREFS_NAME, Context.MODE_PRIVATE)
    private val localePrefs = context.getSharedPreferences(com.baraa.masroof.data.preferences.SharedPrefsAppLocaleRepository.PREFS_NAME, Context.MODE_PRIVATE)
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> request() }
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private val observer = object : InvalidationTracker.Observer("financial_transaction", "financial_transaction_raw_sms_link",
        "parsed_event", "commitment", "account_registry", "card_registry", "loan_registry", "credit_facility", "processing_retry", "review_item") {
        override fun onInvalidated(tables: Set<String>) { request() }
    }
    @Synchronized fun start() {
        if (started) return
        started = true
        onboardingPrefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        localePrefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        scope.launch {
            while (container.awaitStartupMaintenance() != StartupMaintenanceOutcome.READY) delay(5_000)
            container.database.invalidationTracker.addObserver(observer)
            runCatching { loadCached() }
            request()
            for (ignored in changes) {
                delay(1200)
                while (changes.tryReceive().isSuccess) { /* coalesce committed batches */ }
                try { refresh() } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    container.appLogService.error("LAB_ANALYTICS", "Analysis failed (${e.javaClass.simpleName})")
                    mutable.update { it.copy(updating = false, unavailable = true) }
                }
            }
        }
        scope.launch {
            var tick = clock.millis() / 3600_000
            while (isActive) { delay(60_000); val current = clock.millis() / 3600_000; if (current != tick) { tick = current; request() } }
        }
        AnalyticsWorker.scheduleDaily(context)
        scope.launch { AnalyticsImportGate.completed.collect { request() } }
    }
    fun request() { generation.incrementAndGet(); mutable.update { it.copy(updating = true) }; changes.trySend(Unit) }

    private suspend fun loadCached() {
        val dao = store.analytics()
        dao.initializePreferences(AnalysisPreferencesRow(enabledAt = clock.millis()))
        val prefs = requireNotNull(dao.preferences())
        val cached = dao.latest()?.let { runCatching { json.decodeFromString<AnalysisSnapshot>(it.payload) }.getOrNull() }
        val history = dao.recentSnapshots().mapNotNull { runCatching { json.decodeFromString<AnalysisSnapshot>(it.payload) }.getOrNull() }
        mutable.value = AnalysisUiState(cached, true, false, dao.alerts().filterNot { it.resolved }, prefs, history)
    }

    private data class Source(val clean: CleanData, val start: Long, val end: Long, val today: Long,
        val completed: Int, val dues: List<AnalysisDue>, val committed: Set<String>, val reviews: Int,
        val blocked: Boolean, val fingerprint: String)

    private suspend fun source(): Source {
            val zone = clock.zone; val today = LocalDate.now(clock); val todayDay = today.toEpochDay()
            val period = FinancialPeriodPolicy.periodContaining(today)
            val start = today.minusYears(1)
            val read = FinancialAnalysisReader.capture(container, start.atStartOfDay(zone).toInstant(), period.endDateExclusive.atStartOfDay(zone).toInstant(), zone, clock)
            val historical = read.service.loadOverview(FinancialPeriod(start, period.endDateExclusive))
            val current = read.service.loadOverview(period)
            val included = historical.spendingTrends?.purchases?.transactionIdsByDate?.values?.flatten()?.toSet().orEmpty()
            val raw = read.rows.associateBy { it.id }
            val facts = historical.transactions.map { tx ->
                val original = raw[tx.id] ?: tx
                val amount = runCatching {
                    val decimal = if (original.amount.currency == Currency.SAR) original.amount.amount else {
                        if (tx.appliedExchangeRate == null || tx.exchangeRateSource == null || tx.appliedExchangeRate.signum() <= 0) null
                        else historical.labSarEquivalents[tx.id]?.amount
                    }
                    decimal?.setScale(2, RoundingMode.HALF_EVEN)?.movePointRight(2)?.longValueExact()
                }.getOrNull()
                val effective = historical.transactionFacts[tx.id]?.effectiveType ?: tx.type
                FinancialFact(tx.id, tx.occurredAt.atZone(zone).toLocalDate().toEpochDay(), amount,
                    tx.type == FinancialTransactionType.REFUND, tx.id in included, tx.merchant ?: tx.counterparty,
                    effective in setOf(FinancialTransactionType.EXPENSE, FinancialTransactionType.FEE, FinancialTransactionType.BILL_PAYMENT))
            }
            val onboarding = container.onboardingPreferencesRepository
            val importedFrom = if (onboarding.isHistoricalImportCompleted()) onboarding.getHistoricalImportStartEpochMillis()
                ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() } else null
            val firstKnownDay = facts.filter { it.day <= todayDay }.minOfOrNull { it.day }
            val covered = importedFrom?.let { from -> firstKnownDay?.let { maxOf(from, it) } }
            val retry = read.retry
            val noPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
            val importBusy = AnalyticsImportGate.active()
            val blocked = retry || importBusy || !onboarding.isHistoricalImportCompleted() || noPermission
            store.analytics().initializePreferences(AnalysisPreferencesRow(enabledAt = clock.millis()))
            val prefs = requireNotNull(store.analytics().preferences())
            val gaps = prefs.gapDays.split(',').mapNotNull { it.toLongOrNull() }.toMutableSet()
            if (noPermission || retry) gaps += todayDay
            if (gaps.joinToString(",") != prefs.gapDays) store.analytics().preferences(prefs.copy(gapDays = gaps.sorted().joinToString(",")))
            val clean = FinancialCleaner.clean(facts, start.toEpochDay(), todayDay, covered, gaps) { day ->
                val date = LocalDate.ofEpochDay(day); (day - FinancialPeriodPolicy.periodContaining(date).startDate.toEpochDay()).toInt()
            }
            var candidatePeriod = FinancialPeriodPolicy.periodContaining(start)
            var completed = 0
            while (!candidatePeriod.endDateExclusive.isAfter(today)) {
                if (covered != null && candidatePeriod.startDate.toEpochDay() >= covered &&
                    clean.days.filter { it.day in candidatePeriod.startDate.toEpochDay() until candidatePeriod.endDateExclusive.toEpochDay() }.all { it.covered }) completed++
                candidatePeriod = FinancialPeriodPolicy.next(candidatePeriod)
            }
            val dues = current.commitmentsOverview.rows.filter { it.status == CommitmentPaymentStatus.UNPAID }.mapNotNull {
                if (it.amount.currency != Currency.SAR) null else AnalysisDue(it.key, it.displayName,
                    it.amount.amount.movePointRight(2).longValueExact(), it.expectedDate?.toEpochDay(), true)
            }
            val committed = read.committed
            val decisions = store.analytics().candidates().filter { it.status != "SUGGESTED" }.sortedBy { it.id }.map { it.id to it.status }
            val canonical = "presentation-v2|${AnalyticsVersion.MODEL}|${AnalyticsVersion.FEATURES}|${clean.fingerprint}|$dues|${committed.sorted()}|$blocked|${current.summary.reviewRequiredCount}|${period.startDate}|$prefs|$decisions|${clock.millis() / (6 * 3600_000L)}"
            return Source(clean, period.startDate.toEpochDay(), period.endDateExclusive.toEpochDay(), todayDay, completed,
                dues, committed, current.summary.reviewRequiredCount, blocked, hash(canonical))
    }

    suspend fun refresh() = withContext(Dispatchers.Default) { refreshOnWorker() }
    private suspend fun refreshOnWorker() = mutex.withLock {
        if (container.awaitStartupMaintenance() != StartupMaintenanceOutcome.READY) return@withLock
        val version = generation.get()
        val input = source()
        if (AnalyticsImportGate.active()) return@withLock
        val dao = store.analytics(); val prefs = dao.preferences() ?: AnalysisPreferencesRow(enabledAt = clock.millis())
        val latest = dao.latest()
        if (latest?.sourceHash == input.fingerprint) { loadCached(); finishUi(input); return@withLock }
        val previous = dao.models().filterNot { it.id.startsWith("baseline:") }.mapNotNull { runCatching { json.decodeFromString<StoredHuber>(it.payload).takeIf { it.modelVersion == AnalyticsVersion.MODEL && it.cleaningVersion == AnalyticsVersion.CLEANING }?.model() }.getOrNull() }.maxByOrNull { it.trainedThrough }
        val result = PurchaseForecaster.forecast(input.clean, input.start, input.end, input.today, input.completed,
            { day -> (day - FinancialPeriodPolicy.periodContaining(LocalDate.ofEpochDay(day)).startDate.toEpochDay()).toInt() }, previous)
        val patterns = RecurringDetector.detect(input.clean.recurring, input.today)
        val priorCandidates = dao.candidates().associateBy { it.id }
        val candidates = patterns.map { pattern ->
            AnalysisCandidate(pattern.key, pattern.name, pattern.estimate, pattern.lower, pattern.upper, pattern.nextDay,
                pattern.cadence, pattern.sourceIds, input.clean.recurring.first { it.id == pattern.sourceIds.last() }.day,
                pattern.latestAmount, pattern.referenceAmount, pattern.amountIncreased)
        }
        val suggested = candidates.filter { item -> item.sourceIds.none { it in input.committed } && priorCandidates[item.key]?.status !in setOf("ACCEPTED", "REJECTED") }
        val estimates = candidates.filter { item -> item.nextDay in input.today until input.end && item.sourceIds.none { it in input.committed } && priorCandidates[item.key]?.status != "REJECTED" }
            .map { AnalysisDue(it.key, it.name, it.amount, it.nextDay, false) }
        val at = clock.millis()
        val quality = AnalysisQuality(input.clean.quality.used, input.clean.quality.excluded, input.clean.quality.unusualIds,
            input.clean.quality.coveredDays, input.clean.quality.coverageKnown, input.reviews,
            input.blocked || input.reviews > 0 || input.clean.quality.excluded.keys.any { it != "OUTSIDE_PURCHASE_SCOPE" && it != "DUPLICATE_ID" },
            input.clean.days.firstOrNull { it.covered }?.day, input.clean.days.lastOrNull { it.covered }?.day)
        val todayFacts = input.clean.days.first { it.day == input.today }
        val futureDays = result.points.map { point -> AnalysisDay(point.day, null,
            if (point.day == input.today) Math.addExact(todayFacts.net, (point.gross - todayFacts.gross).coerceAtLeast(0)) else point.gross) }
        val snapshot = AnalysisSnapshot(input.start, input.end, at, input.fingerprint, result.status, result.method,
            result.actual, result.remaining, result.total, result.lower, result.upper,
            input.clean.days.filter { it.day >= input.start }.map { AnalysisDay(it.day, it.net, null) } + futureDays,
            quality, suggested, input.dues + estimates, input.completed,
            trainingFrom = if (result.model?.accepted == true) result.model?.trainedFrom else result.baseline?.from,
            trainingThrough = if (result.model?.accepted == true) result.model?.trainedThrough else result.baseline?.through)
        // A committed source invalidation while fitting invalidates the entire result.
        if (generation.get() != version || input.fingerprint != source().fingerprint) { request(); return@withLock }
        val alerts = alerts(snapshot, candidates.filter { priorCandidates[it.key]?.status != "REJECTED" }, dao.alerts(), prefs)
        store.withTransaction {
            dao.snapshot(AnalysisSnapshotRow(hash("$at|${input.fingerprint}"), input.fingerprint, at, json.encodeToString(snapshot)))
            result.model?.let {
                val payload = json.encodeToString(StoredHuber.from(it))
                dao.model(AnalysisModelRow(hash(payload), payload))
            }
            result.baseline?.let {
                val payload = json.encodeToString(StoredBaseline(it.medians, it.from, it.through, it.errors))
                dao.model(AnalysisModelRow("baseline:${hash(payload)}", payload))
            }
            dao.candidates(candidates.map { c -> RecurringCandidateRow(c.key,
                priorCandidates[c.key]?.status ?: if (c.sourceIds.any { it in input.committed }) "ACCEPTED" else "SUGGESTED", json.encodeToString(c)) })
            dao.alerts(alerts)
        }
        if (generation.get() != version) { request(); return@withLock }
        loadCached(); finishUi(input)
        if (generation.get() == version) AnalyticsNotifications.publish(
            com.baraa.masroof.application.locale.AppLocaleContextFactory.wrap(context, container.appLocaleRepository.getLanguageTag()), store, snapshot, prefs, clock.millis())
    }
    private fun finishUi(input: Source) {
        val actuals = mutable.value.history.filter { it.periodEnd <= input.today }.distinctBy { it.periodStart }.mapNotNull { past ->
            val days = input.clean.days.filter { it.day in past.periodStart until past.periodEnd }
            if (days.size.toLong() != past.periodEnd - past.periodStart || days.any { !it.covered }) null
            else past.periodStart to days.fold(0L) { sum, day -> Math.addExact(sum, day.net) }
        }.toMap()
        mutable.update { it.copy(updating = false, unavailable = false, historyActuals = actuals, purchasePeriods = purchasePeriodHistory(input.clean, input.start)) }
    }

    private fun alerts(snapshot: AnalysisSnapshot, candidates: List<AnalysisCandidate>, previous: List<AnalysisAlertRow>, prefs: AnalysisPreferencesRow) =
        AnalysisAlertPolicy.evaluate(snapshot, candidates, previous, prefs, LocalDate.now(clock).toEpochDay())
    suspend fun updatePreferences(value: AnalysisPreferencesRow) {
        require(value.budget == null || value.budget > 0); require(value.reminderDays in 1..30)
        mutex.withLock {
            container.databaseAccessGate.withAccess {
                val old = store.analytics().preferences()
                store.analytics().preferences(value.copy(id = 1, enabledAt = old?.enabledAt?.takeIf { it > 0 } ?: clock.millis(), gapDays = old?.gapDays.orEmpty()))
            }
        }
        request()
    }
    suspend fun findAlert(id: String): AnalysisAlertRow? = withContext(Dispatchers.IO) {
        container.databaseAccessGate.withAccess { store.analytics().alerts().firstOrNull { it.id == id } }
    }
    suspend fun markAlert(id: String, snooze: Boolean = false) = mutex.withLock {
        container.databaseAccessGate.withAccess {
            val dao = store.analytics(); val row = dao.alerts().firstOrNull { it.id == id } ?: return@withAccess
            dao.alerts(listOf(row.copy(read = !snooze, readAt = if (!snooze && row.readAt == 0L) clock.millis() else row.readAt,
                snoozedUntil = if (snooze) clock.millis() + 24 * 3600_000 else row.snoozedUntil)))
            val alerts = dao.alerts().filterNot { row -> row.resolved }
            mutable.update { it.copy(alerts = alerts) }
            AnalyticsNotifications.cancel(context, id)
        }
    }
    suspend fun rejectCandidate(key: String) {
        mutex.withLock { container.databaseAccessGate.withAccess {
            val row = store.analytics().candidates().firstOrNull { it.id == key } ?: return@withAccess
            store.analytics().candidates(listOf(row.copy(status = "REJECTED")))
        } }; request()
    }
    suspend fun acceptCandidate(key: String, draft: SettingsCommitmentsWorkflow.CommitmentEditorDraft): Boolean {
        val c = mutable.value.snapshot?.candidates?.firstOrNull { it.key == key } ?: return false
        require(draft.name.isNotBlank() && draft.amount.amount.signum() > 0)
        mutex.withLock { container.databaseAccessGate.withAccess {
            container.database.withTransaction {
                val source = container.financialTransactionRepository.getById(c.sourceIds.last()) ?: error("Source changed")
                val existing = container.commitmentRepository.getBySourceTransactionId(source.id)
                if (existing == null) {
                    check(container.dashboardCommitmentsWorkflow.createFromTransaction(source.id) is CommitmentCreationResult.Success)
                    val created = requireNotNull(container.commitmentRepository.getBySourceTransactionId(source.id))
                    container.settingsCommitmentsWorkflow.update(created.id, draft)
                }
            }
            val row = store.analytics().candidates().firstOrNull { it.id == key }
            if (row != null) store.analytics().candidates(listOf(row.copy(status = "ACCEPTED")))
        } }
        request(); return true
    }

    /** Bounded, live read; projection and evidence enrichment happen outside the financial read transaction. */
    suspend fun evidence(ids: List<String>): List<AnalysisEvidence> = withContext(Dispatchers.IO) {
        require(ids.size <= 20)
        container.databaseAccessGate.withAccess {
            val rows = ids.distinct().mapNotNull { container.financialTransactionRepository.getById(it) }
            val facts = if (rows.isEmpty()) emptyMap() else {
                val from = rows.minOf { it.occurredAt }.atZone(clock.zone).toLocalDate()
                val through = rows.maxOf { it.occurredAt }.atZone(clock.zone).toLocalDate().plusDays(1)
                FinancialAnalysisReader.capture(container, from.atStartOfDay(clock.zone).toInstant(), through.atStartOfDay(clock.zone).toInstant(), clock.zone, clock)
                    .service.loadOverview(FinancialPeriod(from, through)).transactionFacts
            }
            val byId = rows.associateBy { it.id }
            val cards = container.cardRegistryRepository.listAll().filter { it.ownership == com.baraa.masroof.domain.model.OwnershipStatus.OWNED }
            ids.distinct().sortedWith(compareByDescending<String> { byId[it]?.occurredAt }.thenBy { it })
                .map { AnalysisEvidence(it, byId[it], facts[it], cards) }
        }
    }
    suspend fun sortEvidenceIds(ids: List<String>): List<String> = withContext(Dispatchers.IO) {
        container.databaseAccessGate.withAccess {
            val dates = mutableMapOf<String, Long>()
            // Only read ordering metadata for the referenced IDs, in bounded SQLite batches.
            ids.distinct().chunked(400).forEach { batch ->
                val placeholders = batch.joinToString(",") { "?" }
                container.database.openHelper.readableDatabase.query(
                    "SELECT id, occurredAtEpochMillis FROM financial_transaction WHERE id IN ($placeholders)", batch.toTypedArray()
                ).use { cursor -> while (cursor.moveToNext()) dates[cursor.getString(0)] = cursor.getLong(1) }
            }
            ids.distinct().sortedWith(compareByDescending<String> { dates[it] }.thenBy { it })
        }
    }
    fun money(cents: Long): Money = Money(BigDecimal.valueOf(cents, 2), Currency.SAR)
    fun budgetStatus(): String {
        val s = state.value.snapshot ?: return "NONE"
        val budget = state.value.preferences.budget ?: return "NONE"
        return when {
            s.actual > budget -> "ACTUAL"
            s.lower != null && s.lower > budget -> "EXPECTED"
            s.actual.toDouble() >= budget * .8 || (s.total ?: 0) >= budget -> "NEAR"
            else -> "WITHIN"
        }
    }
    fun editorDraft(candidate: AnalysisCandidate) = SettingsCommitmentsWorkflow.CommitmentEditorDraft(
        candidate.name, money(candidate.amount), LocalDate.ofEpochDay(candidate.latestDay),
        com.baraa.masroof.domain.model.CommitmentRecurrence.valueOf(candidate.cadence), LocalDate.ofEpochDay(candidate.nextDay))
    fun parseBudget(value: String): Long? = value.trim().takeIf { it.isNotEmpty() }?.let { BigDecimal(normalizeAnalysisNumber(it)).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact().also { require(it > 0) } }
    override fun close() {
        val job = scope.coroutineContext[Job]!!
        job.cancel()
        runBlocking { job.join() }
        onboardingPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        localePrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (started) container.database.invalidationTracker.removeObserver(observer)
        if (storeDelegate.isInitialized()) store.close()
    }
    companion object { fun from(context: Context): LabAnalyticsWorkflow = (context.applicationContext as MasroofApplication).container.labAnalytics }
}

/** Scanner hook is process-local; persisted retry/import-completion gates cover restart. */
object AnalyticsImportGate {
    private val count = java.util.concurrent.atomic.AtomicInteger()
    private val revision = MutableStateFlow(0L)
    val completed = revision.asStateFlow()
    fun active() = count.get() > 0
    suspend fun <T> during(block: suspend () -> T): T { count.incrementAndGet(); return try { block() } finally { if (count.decrementAndGet() == 0) revision.update { it + 1 } } }
}
