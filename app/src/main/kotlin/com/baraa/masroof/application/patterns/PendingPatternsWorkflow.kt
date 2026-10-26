package com.baraa.masroof.application.patterns

import com.baraa.masroof.bank.BankRoutingResult
import com.baraa.masroof.bank.BankSmsRegistry
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.ExplicitBankSelection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.domain.repository.UserCorrectionRepository
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.baraa.masroof.application.ingestion.SmsIngestionResult
import com.baraa.masroof.application.sms.HistoricalSmsBatchProcessor
import com.baraa.masroof.application.sms.HistoricalBatchDerivedResult
import com.baraa.masroof.domain.model.ReviewStatus

enum class PendingPatternStatus { MATCHED, NO_MATCH, PATTERN_CONFLICT, BANK_CONFLICT }
data class PendingPatternRow(val reviewId: String, val status: PendingPatternStatus, val matches: List<PatternMatch>)
data class PendingPatternReport(val generation: Long, val bankId: String, val rows: List<PendingPatternRow>, val limited: Boolean)
data class PendingPatternApplyReport(val processed: Int, val posted: Int, val failed: Int?, val incomplete: Boolean, val limited: Boolean)

/** Read-only preview; explicit apply rechecks eligibility and delegates to the existing batch pipeline. */
class PendingPatternsWorkflow(
    private val store: MessagePatternStore,
    private val accessGate: DatabaseAccessGate,
    private val reviews: ReviewRepository,
    private val rawMessages: RawSmsRepository,
    private val parsedEvents: ParsedEventRepository,
    private val corrections: UserCorrectionRepository,
    private val transactions: FinancialTransactionRepository,
    private val registry: BankSmsRegistry,
    private val batches: HistoricalSmsBatchProcessor? = null,
    private val inTransaction: (suspend (suspend () -> SmsIngestionResult) -> SmsIngestionResult)? = null,
    private val markParseFailed: (suspend (String) -> Unit)? = null,
    private val onChanged: suspend () -> Unit = {},
) {
    val canApply: Boolean get() = batches != null && inTransaction != null

    suspend fun apply(bankId: String, generation: Long, reviewId: String? = null): PendingPatternApplyReport = withContext(Dispatchers.IO) {
        accessGate.withAccess {
            val report = preview(bankId, generation, reviewId)
            val current = store.load().banks.single { it.bankId == bankId }
            val approved = current.currentPatterns.filter { it.enabled && it.factContract != null }.map { it.id }.toSet()
            val batch = requireNotNull(batches).startBatch(Bank(bankId))
            val processed = mutableListOf<String>()
            var failed = 0
            for (row in report.rows.filter { it.status == PendingPatternStatus.MATCHED && it.matches.single().patternId in approved }) {
                requireNotNull(inTransaction).invoke {
                    val review = reviews.getById(row.reviewId)
                    if (review == null || review.status != ReviewStatus.REQUIRED || review.resolutionKind != null ||
                        corrections.listForRawSmsId(review.rawSmsId).isNotEmpty() || transactions.isRawSmsLinked(review.rawSmsId)) {
                        return@invoke SmsIngestionResult.Duplicate
                    }
                    val event = parsedEvents.findByRawSmsId(review.rawSmsId)?.event
                    if (event?.parseStatus == ParseStatus.SUCCESS) return@invoke SmsIngestionResult.Duplicate
                    val raw = rawMessages.getById(review.rawSmsId) ?: return@invoke SmsIngestionResult.Duplicate
                    val selected = ExplicitBankSelection.selectedBankId(review.reasons) ?: event?.bank?.takeUnless { it == Bank.UNKNOWN }?.id
                    if (selected != null && selected != bankId) return@invoke SmsIngestionResult.Duplicate
                    if (selected == null) {
                        val route = registry.route(raw.sender, raw.body)
                        if (route !is BankRoutingResult.Matched || route.adapter.bank.id != bankId) return@invoke SmsIngestionResult.Duplicate
                    }
                    batch.reprocess(raw).also {
                        if (it is SmsIngestionResult.Failed) { failed++; markParseFailed?.invoke(raw.id) }
                        else if (it !is SmsIngestionResult.Duplicate && it !is SmsIngestionResult.NotRelevant) processed += raw.id
                    }
                }
            }
            val derived = batch.finish()
            var posted = 0
            for (id in processed) if (transactions.isRawSmsLinked(id)) posted++
            onChanged()
            val financialFailed = batch.reconciliationSummary?.failed
            val failureCount = if (derived is HistoricalBatchDerivedResult.Incomplete && (financialFailed ?: 0) == 0) null
                else failed + (financialFailed ?: 0)
            PendingPatternApplyReport(processed.size, posted, failureCount,
                failed > 0 || derived is HistoricalBatchDerivedResult.Incomplete, report.limited)
        }
    }
    suspend fun preview(bankId: String, generation: Long, reviewId: String? = null): PendingPatternReport = withContext(Dispatchers.IO) {
        accessGate.withAccess {
            val configuration = store.load()
            check(configuration.generation == generation) { "Pattern configuration changed" }
            val bank = configuration.banks.single { it.bankId == bankId }
            val patterns = bank.currentPatterns
            val required = if (reviewId == null) reviews.listRequired()
                else listOfNotNull(reviews.getById(reviewId)?.takeIf { it.status == ReviewStatus.REQUIRED })
            val rows = mutableListOf<PendingPatternRow>()
            // ponytail: bounded interactive preview; add repository pagination if queues exceed 1,000 rows routinely.
            for (batch in required.take(1_000).chunked(200)) {
                val ids = batch.map { it.rawSmsId }
                val protected = corrections.listForRawSmsIds(ids).mapTo(hashSetOf()) { it.targetRawSmsId }
                val parsed = parsedEvents.listByRawSmsIds(ids).associateBy { it.event.rawSmsId }
                val raw = rawMessages.getByIds(ids).associateBy { it.id }
                for (review in batch) {
                    if (review.resolutionKind != null || review.rawSmsId in protected || transactions.isRawSmsLinked(review.rawSmsId)) continue
                    val event = parsed[review.rawSmsId]?.event
                    if (event?.parseStatus == ParseStatus.SUCCESS) continue
                    val message = raw[review.rawSmsId] ?: continue
                    val storedBank = ExplicitBankSelection.selectedBankId(review.reasons)
                        ?: event?.bank?.takeUnless { it == Bank.UNKNOWN }?.id
                    val candidates = if (storedBank != null) setOf(storedBank) else {
                        val routed = when (val route = registry.route(message.sender, message.body)) {
                            is BankRoutingResult.Matched -> setOf(route.adapter.bank.id)
                            is BankRoutingResult.Ambiguous -> route.banks.mapTo(hashSetOf()) { it.id }
                            else -> emptySet()
                        }
                        routed + configuration.banks.filter { message.sender in it.senders }.map { it.bankId }
                    }
                    if (bankId !in candidates) continue
                    if (candidates.size != 1) {
                        rows += PendingPatternRow(review.id, PendingPatternStatus.BANK_CONFLICT, emptyList())
                        continue
                    }
                    val matches = patterns.mapNotNull { pattern ->
                        pattern.preview(bankId, pattern.sender, message.body)?.let {
                            PatternMatch(pattern.id, pattern.revision, pattern.label, it.fields.displayFields())
                        }
                    }
                    val status = when (matches.size) {
                        0 -> PendingPatternStatus.NO_MATCH
                        1 -> PendingPatternStatus.MATCHED
                        else -> PendingPatternStatus.PATTERN_CONFLICT
                    }
                    rows += PendingPatternRow(review.id, status, matches)
                }
            }
            PendingPatternReport(generation, bankId, rows, required.size > 1_000)
        }
    }
}
