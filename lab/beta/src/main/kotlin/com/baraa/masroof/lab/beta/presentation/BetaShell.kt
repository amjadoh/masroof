package com.baraa.masroof.lab.beta.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.lab.beta.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetaShell(
    markerCount: Int,
    restoreMissing: Boolean,
    onReturn: () -> Unit,
    onReset: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    BackHandler(onBack = onReturn)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.beta_title)) },
                actions = {
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("beta-badge"),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            stringResource(R.string.beta_badge),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beta-badge"),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    stringResource(R.string.beta_badge),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                stringResource(R.string.beta_empty_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.beta_marker_count, markerCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (restoreMissing) {
                Text(
                    stringResource(R.string.beta_restore_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onReturn,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beta-return"),
            ) {
                Text(stringResource(R.string.beta_return))
            }
            OutlinedButton(
                onClick = onBackup,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beta-backup"),
            ) {
                Text(stringResource(R.string.beta_backup))
            }
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beta-restore"),
            ) {
                Text(stringResource(R.string.beta_restore))
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("beta-reset"),
            ) {
                Text(stringResource(R.string.beta_reset))
            }
        }
    }
}
