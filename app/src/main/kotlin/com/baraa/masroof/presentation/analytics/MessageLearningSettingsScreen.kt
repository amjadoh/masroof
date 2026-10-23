package com.baraa.masroof.presentation.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.analytics.LabAnalyticsWorkflow
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofSpacing

@Composable
fun MessageLearningSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val workflow = LocalLabAnalyticsWorkflow.current ?: remember(context) { LabAnalyticsWorkflow.from(context) }
    val state by workflow.state.collectAsState()
    MasroofSecondaryScaffold(stringResource(R.string.analysis_learning_settings), onBack, stringResource(R.string.settings_back)) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            Text(stringResource(R.string.analysis_learning_settings_hint), style = MaterialTheme.typography.bodyMedium)
            MessageLearningInsights(state)
        }
    }
}
