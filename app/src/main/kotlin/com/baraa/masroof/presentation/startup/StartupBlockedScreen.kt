package com.baraa.masroof.presentation.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.baraa.masroof.presentation.common.MasroofText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofButton
import com.baraa.masroof.presentation.common.MasroofOutlinedButton
import com.baraa.masroof.ui.theme.MasroofSpacing

/**
 * Shown while blocking maintenance has not certified the data. It exposes no financial
 * content; restoring a backup stays reachable so a failed restore cannot trap the user.
 */
@Composable
fun StartupBlockedScreen(onRetry: () -> Unit, onRestoreBackup: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MasroofSpacing.screenPaddingLarge),
        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.startup_maintenance_blocked),
            style = MaterialTheme.typography.bodyLarge,
        )
        MasroofButton(onRetry, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dashboard_retry))
        }
        MasroofOutlinedButton(onRestoreBackup, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reference_restore_title))
        }
    }
}
