package com.baraa.masroof.presentation.patterns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.PatternBankView
import com.baraa.masroof.application.patterns.PatternDefinitionView
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofSpacing

@Composable
internal fun PatternHistoryScreen(bank: PatternBankView, pattern: PatternDefinitionView, busy: Boolean, failed: Boolean,
    onBack: () -> Unit, onRestore: (Int) -> Unit) {
    BackHandler { onBack() }
    MasroofSecondaryScaffold(stringResource(R.string.pattern_approval_advanced), onBack, stringResource(R.string.settings_back)) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            MasroofCard {
                Text(patternOperationName(pattern.label), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.pattern_revision, patternOperationName(pattern.label), pattern.revision))
                Text(pattern.example)
                pattern.fields.forEach { Text(patternFieldName(it)) }
            }
            if (failed) Text(stringResource(R.string.review_action_failed), color = MaterialTheme.colorScheme.error)
            pattern.priorRevisions.forEach { revision ->
                MasroofOutlinedButton({ onRestore(revision) }, enabled = !busy, modifier = Modifier.fillMaxWidth()
                    .testTag("pattern-restore-${bank.bankId}-${pattern.id}-$revision")) {
                    Text(stringResource(R.string.pattern_restore_revision, revision))
                }
            }
            MasroofOutlinedButton(onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.teaching_back)) }
        }
    }
}
