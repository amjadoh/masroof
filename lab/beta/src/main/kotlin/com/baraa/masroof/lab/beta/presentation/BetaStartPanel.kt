package com.baraa.masroof.lab.beta.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.application.BetaStartState
import com.baraa.masroof.lab.beta.presentation.theme.BetaSpacing

@Composable
internal fun BetaStartPanel(state: BetaStartState, busy: Boolean, failed: Boolean, onContinue: () -> Unit) {
    val existing = state == BetaStartState.EXISTING
    Column(Modifier.testTag(if (existing) "beta-start-existing" else "beta-start-new"),
        verticalArrangement = Arrangement.spacedBy(BetaSpacing.section)) {
        Text(stringResource(if (existing) R.string.beta_start_existing_title else R.string.beta_start_new_title))
        Text(stringResource(if (existing) R.string.beta_start_existing_body else R.string.beta_start_new_body))
        if (failed) Text(stringResource(R.string.beta_action_failed))
        Button(onClick = onContinue, enabled = !busy, modifier = Modifier.testTag("beta-start-continue")) {
            Text(stringResource(if (existing) R.string.beta_start_resume else R.string.beta_start_begin))
        }
    }
}
