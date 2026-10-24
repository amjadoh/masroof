package com.baraa.masroof.application.interpretation

import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.ReviewStatus
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.domain.rules.InformationalMessagePolicy
import com.baraa.masroof.lab.bank.interpretation.FamilyTextPreparation
import com.baraa.masroof.lab.ml.family.FamilyInputCleaner
import com.baraa.masroof.parsing.repository.ParsedEventRepository

data class MessageReviewExample(val reviewId: String, val receivedAt: Long, val unsupportedWording: Boolean)
internal data class MessageReviewCandidate(val example: MessageReviewExample, val bank: String, val template: String)

/** Optional, ephemeral review suggestions. Decisions still use the existing review workflow. */
class MessageReviewWorkflow(private val reviews: ReviewRepository, private val raw: RawSmsRepository,
    private val parsed: ParsedEventRepository) {
    suspend fun load(): List<MessageReviewExample> {
        val required = reviews.listRequired().filter { it.status == ReviewStatus.REQUIRED && it.kind == ReviewKind.NEEDS_REVIEW }
        val ids = required.map { it.rawSmsId }
        val messages = raw.getByIds(ids).associateBy { it.id }
        val events = parsed.listByRawSmsIds(ids).associateBy { it.event.rawSmsId }
        val candidates = required.mapNotNull { review ->
            val message = messages[review.rawSmsId] ?: return@mapNotNull null
            val event = events[review.rawSmsId]?.event
            if (InformationalMessagePolicy.shouldAutoIgnore(event?.messageFamily)) return@mapNotNull null
            val cleaned = LabFamilyAssist.clean(message.body)
            val bank = event?.bank?.id?.takeIf { it != "UNKNOWN" } ?: "unrouted:${FamilyInputCleaner.sha256(message.sender)}"
            val template = cleaned.features?.template ?: FamilyInputCleaner.sha256(FamilyTextPreparation.prepare(message.body).title)
            MessageReviewCandidate(MessageReviewExample(review.id, message.receivedAt.toEpochMilli(), cleaned.features == null), bank, template)
        }
        return select(candidates)
    }

    companion object {
        internal fun select(candidates: List<MessageReviewCandidate>): List<MessageReviewExample> = candidates
            .sortedWith(compareByDescending<MessageReviewCandidate> { it.example.unsupportedWording }
                .thenByDescending { it.example.receivedAt }.thenBy { it.example.reviewId })
            .distinctBy { it.bank to it.template }.take(3).map { it.example }
    }
}
