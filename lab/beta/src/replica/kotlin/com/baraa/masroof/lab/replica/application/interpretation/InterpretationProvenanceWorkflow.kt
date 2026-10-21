package com.baraa.masroof.lab.replica.application.interpretation

import com.baraa.masroof.lab.replica.application.review.EffectiveParsedEventProvider
import com.baraa.masroof.lab.replica.domain.model.MessageFamily
import com.baraa.masroof.lab.replica.domain.model.ParseStatus
import com.baraa.masroof.lab.replica.domain.model.ReviewResolutionKind
import com.baraa.masroof.lab.replica.domain.model.ReviewStatus
import com.baraa.masroof.lab.replica.domain.repository.ReviewRepository
import com.baraa.masroof.lab.replica.parsing.repository.ParsedEventRecord
import kotlinx.coroutines.CancellationException

enum class InterpretationOrigin { ML, HUMAN_TEMPLATE }

/** Current family inference only. Model availability and asynchronous audit history are not evidence. */
data class InterpretationProvenance(
    val origin: InterpretationOrigin,
    val family: MessageFamily,
    val pendingReview: Boolean,
)

class InterpretationProvenanceWorkflow(
    private val effectiveEvents: EffectiveParsedEventProvider,
    private val reviews: ReviewRepository,
) {
    suspend fun load(rawSmsId: String): InterpretationProvenance? {
        return try {
            val record = effectiveEvents.findEffectiveByRawSmsId(rawSmsId) ?: return null
            val review = reviews.findByRawSmsId(rawSmsId)
            project(record, review?.resolutionKind, review?.status == ReviewStatus.REQUIRED)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Optional explanatory metadata must never hide the original SMS evidence.
            null
        }
    }

    companion object {
        fun project(
            record: ParsedEventRecord,
            resolution: ReviewResolutionKind? = null,
            requiredReview: Boolean = false,
        ): InterpretationProvenance? {
            // A manual family/amount decision supersedes inference. Merchant edits do not lift the parse gate.
            if (record.automationConfirmed || resolution in setOf(
                    ReviewResolutionKind.USER_CORRECTION, ReviewResolutionKind.USER_FINANCIAL_TYPE,
                    ReviewResolutionKind.USER_NON_FINANCIAL,
                )) return null
            val reasons = record.event.confidence.reasons
            val origins = reasons.filter { it.startsWith("family_origin:") }
            val models = reasons.filter { it.startsWith("model:") }
            if (origins.size != 1 || models.size != 1 || models.single().removePrefix("model:").isBlank()) return null
            val origin = InterpretationOrigin.entries.singleOrNull { "family_origin:${it.name}" == origins.single() } ?: return null
            val family = record.event.messageFamily
            if (family !in setOf(MessageFamily.PURCHASE, MessageFamily.REFUND, MessageFamily.WITHDRAWAL,
                    MessageFamily.FEE, MessageFamily.CARD_PAYMENT, MessageFamily.BILL_PAYMENT,
                    MessageFamily.FINANCING_INSTALLMENT, MessageFamily.TRANSFER_IN, MessageFamily.TRANSFER_OUT)) return null
            return InterpretationProvenance(origin, family, requiredReview || record.event.parseStatus != ParseStatus.SUCCESS)
        }
    }
}
