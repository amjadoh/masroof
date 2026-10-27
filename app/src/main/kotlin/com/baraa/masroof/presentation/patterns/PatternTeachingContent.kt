package com.baraa.masroof.presentation.patterns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.PatternTeachingPreview
import com.baraa.masroof.application.patterns.PatternText
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofIconSizes
import com.baraa.masroof.ui.theme.MasroofSpacing
import com.baraa.masroof.ui.theme.MasroofTeachingTokens

@Composable
internal fun PatternTeachingHeader(step: Int, question: String, fieldIndex: Int, fieldCount: Int, required: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
        listOf(R.string.teaching_step_kind, R.string.teaching_step_fields, R.string.teaching_step_confirm).forEachIndexed { index, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
                LinearProgressIndicator(progress = { if (index <= step.coerceAtMost(2)) 1f else 0f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.smallGap)) {
        if (step == 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.teaching_field_progress, fieldIndex + 1, fieldCount), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(if (required) R.string.teaching_required else R.string.teaching_optional), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(question, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().testTag("pattern-question").semantics { heading() })
    }
}

@Composable
internal fun TeachingMessage(body: TextFieldValue, readOnly: Boolean, enabled: Boolean, onChange: (TextFieldValue) -> Unit) {
    OutlinedTextField(body, onChange, readOnly = readOnly, enabled = enabled, minLines = 4, maxLines = 8,
        label = { Text(stringResource(R.string.pattern_message)) }, modifier = Modifier.fillMaxWidth().testTag("pattern-body"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PatternTeachingValues(body: TextFieldValue, candidates: List<PatternText>, enabled: Boolean,
    onBody: (TextFieldValue) -> Unit, onCandidate: (PatternText) -> Unit, canIgnore: Boolean, onIgnore: () -> Unit) {
    @Composable fun message() {
        TeachingMessage(body, true, enabled, onBody)
        MasroofTextButton(onIgnore, enabled = canIgnore) {
            Text(stringResource(R.string.teaching_ignore_value))
        }
    }
    @Composable fun values() {
        MasroofSectionHeader(stringResource(R.string.teaching_values))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap),
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) {
            candidates.forEach { candidate ->
                MasroofChoice(candidate.text, body.selection.min == candidate.start && body.selection.max == candidate.end,
                    { onCandidate(candidate) }, enabled = enabled,
                    modifier = Modifier.testTag("pattern-candidate-${candidate.start}-${candidate.end}"))
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= MasroofTeachingTokens.twoColumnMinWidth) Row(Modifier.fillMaxSize().testTag("pattern-field-columns"),
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) { message() }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("pattern-candidates"),
                verticalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap)) { values() }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("pattern-candidates"),
            verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) { message(); values() }
    }
}

@Composable
internal fun PatternTeachingReview(preview: PatternTeachingPreview, onRemove: (String) -> Unit) {
    MasroofCard {
        Text(patternOperationName(preview.operation), style = MaterialTheme.typography.titleMedium)
        preview.fields.forEach { (key, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${patternFieldName(key)}: ${value.text}", Modifier.weight(1f))
                OutlinedIconButton({ onRemove(key) }) {
                    Icon(MasroofIcons.delete, stringResource(R.string.pattern_remove_binding), Modifier.size(MasroofIconSizes.md))
                }
            }
        }
    }
}

@Composable
internal fun PatternTeachingAction(actionLabel: Int, onAction: () -> Unit, enabled: Boolean) {
    MasroofButton(onAction, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(actionLabel))
    }
}
