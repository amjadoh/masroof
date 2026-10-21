package com.baraa.masroof.lab.replica.presentation.interpretation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.replica.application.interpretation.InterpretationOrigin
import com.baraa.masroof.lab.replica.application.interpretation.InterpretationProvenance
import com.baraa.masroof.lab.replica.presentation.common.MasroofCard
import com.baraa.masroof.lab.replica.presentation.review.toUiLabelRes
import com.baraa.masroof.ui.theme.MasroofSpacing

/** One read-only evidence component in the original detail layouts. Never adds approval actions. */
@Composable
fun InterpretationProvenanceCard(provenance: InterpretationProvenance?) {
    provenance ?: return
    val familyLabel = provenance.family.toUiLabelRes() ?: return
    MasroofCard(modifier = Modifier.testTag("interpretation_provenance")) {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
            Text(stringResource(if (provenance.origin == InterpretationOrigin.ML) R.string.lab_ml_source else R.string.lab_template_source),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.lab_inferred_family, stringResource(familyLabel)),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.lab_family_assist_role), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (provenance.pendingReview) Text(stringResource(R.string.lab_inference_pending),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
