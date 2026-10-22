package com.baraa.masroof.presentation.interpretation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.interpretation.InterpretationOrigin
import com.baraa.masroof.application.interpretation.InterpretationProvenance
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.review.toUiLabelRes
import com.baraa.masroof.ui.theme.MasroofSpacing

/** One read-only evidence component in the original detail layouts. Never adds approval actions. */
@Composable
fun InterpretationProvenanceCard(provenance: InterpretationProvenance?) {
    provenance ?: return
    val familyLabel = provenance.family.toUiLabelRes()
    MasroofCard(modifier = Modifier.testTag("interpretation_provenance")) {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
            val title = when (provenance.origin) {
                InterpretationOrigin.RULES -> R.string.lab_rules_source
                InterpretationOrigin.ML -> R.string.lab_ml_source
                InterpretationOrigin.HUMAN_TEMPLATE -> R.string.lab_template_source
                InterpretationOrigin.MANUAL -> R.string.lab_manual_source
                InterpretationOrigin.UNRESOLVED -> R.string.lab_unresolved_source
                InterpretationOrigin.UNAVAILABLE -> R.string.lab_unavailable_source
            }
            Text(stringResource(title),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (familyLabel != null && provenance.origin != InterpretationOrigin.MANUAL) Text(stringResource(R.string.lab_inferred_family, stringResource(familyLabel)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(when (provenance.origin) {
                InterpretationOrigin.ML, InterpretationOrigin.HUMAN_TEMPLATE -> R.string.lab_family_assist_role
                InterpretationOrigin.RULES -> R.string.lab_rules_role
                InterpretationOrigin.MANUAL -> R.string.lab_manual_role
                InterpretationOrigin.UNRESOLVED -> R.string.lab_unresolved_role
                InterpretationOrigin.UNAVAILABLE -> R.string.lab_unavailable_role
            }), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (provenance.pendingReview) Text(stringResource(if (provenance.origin in setOf(InterpretationOrigin.ML, InterpretationOrigin.HUMAN_TEMPLATE)) R.string.lab_inference_pending else R.string.lab_understanding_pending),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            provenance.feedback?.let { LearningFeedbackExplanation(it) }
        }
    }
}

@Composable
fun LearningFeedbackExplanation(status: com.baraa.masroof.application.interpretation.LearningFeedbackStatus) {
    Text(stringResource(when(status.state) {
        com.baraa.masroof.application.interpretation.LearningFeedbackState.LEARNED -> R.string.learning_used
        com.baraa.masroof.application.interpretation.LearningFeedbackState.BLOCKED -> R.string.learning_blocked
        com.baraa.masroof.application.interpretation.LearningFeedbackState.EXCLUDED -> R.string.learning_excluded
        com.baraa.masroof.application.interpretation.LearningFeedbackState.PENDING -> R.string.learning_pending
    }), style = MaterialTheme.typography.bodySmall)
    status.reason?.let { Text(stringResource(learningReasonResource(it)), style = MaterialTheme.typography.bodySmall) }
}

fun learningReasonResource(reason: String): Int = when {
    reason == "FIELDS_ONLY" -> R.string.learning_reason_fields
    reason == "CONTEXT_ONLY" -> R.string.learning_reason_context
    reason == "UNSUPPORTED_BANK" -> R.string.learning_reason_bank
    reason == "CONTRADICTED_FAMILY" || reason == "CONFLICTING_TEMPLATE" -> R.string.learning_reason_conflict
    reason == "MISSING_EVIDENCE" || reason == "DECISION_PENDING" || reason == "FEEDBACK_PENDING" -> R.string.learning_reason_pending
    reason.startsWith("CLEANING_") -> R.string.learning_reason_cleaning
    else -> R.string.learning_reason_ambiguous
}
