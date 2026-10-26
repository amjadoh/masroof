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

enum class PendingPatternStatus { MATCHED, NO_MATCH, PATTERN_CONFLICT, BANK_CONFLICT }
data class PendingPatternRow(val reviewId: String, val status: PendingPatternStatus, val matches: List<PatternMatch>)
data class PendingPatternReport(val generation: Long, val bankId: String, val rows: List<PendingPatternRow>, val limited: Boolean)

/** Read-only extraction preview, never a reparse, routing decision or financial eligibility grant. */
class PendingPatternPreviewWorkflow(
    private val store: MessagePatternStore,
    private val accessGate: DatabaseAccessGate,
    private val reviews: ReviewRepository,
    private val rawMessages: RawSmsRepository,
    private val parsedEvents: ParsedEventRepository,
    private val corrections: UserCorrectionRepository,
    private val transactions: FinancialTransactionRepository,
    private val registry: BankSmsRegistry,
) {
    suspend fun preview(bankId: String, generation: Long): PendingPatternReport = withContext(Dispatchers.IO) {
        accessGate.withAccess {
            val configuration = store.load()
            check(configuration.generation == generation) { "Pattern configuration changed" }
            val bank = configuration.banks.single { it.bankId == bankId }
            val patterns = bank.currentPatterns
            val required = reviews.listRequired()
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
                        pattern.preview(bankId, message.sender, message.body)?.let {
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
