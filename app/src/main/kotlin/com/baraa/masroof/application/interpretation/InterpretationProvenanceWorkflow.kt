package com.baraa.masroof.application.interpretation

import com.baraa.masroof.application.review.EffectiveParsedEventProvider
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ReviewResolutionKind
import com.baraa.masroof.domain.model.ReviewStatus
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import kotlinx.coroutines.CancellationException

enum class InterpretationOrigin { RULES, ML, HUMAN_TEMPLATE, MANUAL, UNRESOLVED, UNAVAILABLE }

/** Explains the current persisted understanding; model availability is never participation evidence. */
data class InterpretationProvenance(
    val origin: InterpretationOrigin,
    val family: MessageFamily,
    val pendingReview: Boolean,
    val feedback: LearningFeedbackStatus? = null,
)

class InterpretationProvenanceWorkflow(
    private val effectiveEvents: EffectiveParsedEventProvider,
    private val reviews: ReviewRepository,
    private val feedback: suspend (String) -> LearningFeedbackStatus? = { null },
) {
    suspend fun load(rawSmsId: String): InterpretationProvenance {
        return try {
            val record = effectiveEvents.findEffectiveByRawSmsId(rawSmsId) ?: return unavailable()
            val review = reviews.findByRawSmsId(rawSmsId)
            val status = try { feedback(rawSmsId) } catch(e: CancellationException) { throw e }
                catch(_: Exception) { LearningFeedbackStatus(LearningFeedbackState.PENDING,"FEEDBACK_PENDING") }
            project(record, review?.resolutionKind, review?.status == ReviewStatus.REQUIRED).copy(feedback = status)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Preserve the SMS and explicitly explain that its source could not be loaded.
            unavailable()
        }
    }

    companion object {
        private fun unavailable() = InterpretationProvenance(InterpretationOrigin.UNAVAILABLE, MessageFamily.UNKNOWN, false)

        fun project(
            record: ParsedEventRecord,
            resolution: ReviewResolutionKind? = null,
            requiredReview: Boolean = false,
        ): InterpretationProvenance {
            // A manual family/amount decision supersedes inference. Merchant edits do not lift the parse gate.
            if (record.automationConfirmed || resolution in setOf(
                    ReviewResolutionKind.USER_FINANCIAL_TYPE,
                    ReviewResolutionKind.USER_EXTERNAL_TRANSFER,
                    ReviewResolutionKind.USER_NON_FINANCIAL,
                )) return InterpretationProvenance(InterpretationOrigin.MANUAL, record.event.messageFamily, requiredReview)
            val reasons = record.event.confidence.reasons
            val origins = reasons.filter { it.startsWith("family_origin:") }
            val models = reasons.filter { it.startsWith("model:") }
            val family = record.event.messageFamily
            val pending = requiredReview || record.event.parseStatus !in setOf(ParseStatus.SUCCESS, ParseStatus.NON_FINANCIAL)
            if (family == MessageFamily.UNKNOWN) return InterpretationProvenance(InterpretationOrigin.UNRESOLVED, family, true)
            val origin = if (origins.isEmpty() && models.isEmpty() && reasons.any { it.startsWith("engine:") }) {
                InterpretationOrigin.RULES
            } else if (origins.size == 1 && models.size == 1 && models.single().removePrefix("model:").isNotBlank()) {
                setOf(InterpretationOrigin.ML, InterpretationOrigin.HUMAN_TEMPLATE).singleOrNull { "family_origin:${it.name}" == origins.single() }
            } else null
            if (origin == null) return InterpretationProvenance(InterpretationOrigin.UNAVAILABLE, family, pending)
            return InterpretationProvenance(origin, family, pending)
        }
    }
}
