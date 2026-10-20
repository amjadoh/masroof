package com.baraa.masroof.lab.beta.replica

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.replica.MainActivity
import com.baraa.masroof.ui.theme.MasroofTheme
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opening failures preserve files; recovery/reset remains reachable outside Room. */
class LabLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var attempt by remember { mutableIntStateOf(0) }
            var failed by remember { mutableStateOf(false) }
            LaunchedEffect(attempt) {
                failed = false
                val opened = withContext(Dispatchers.IO) { runCatching { LabRuntime.container(this@LabLaunchActivity) }.isSuccess }
                if (opened) {
                    LabRuntime.enableLive(this@LabLaunchActivity)
                    startActivity(Intent(this@LabLaunchActivity, MainActivity::class.java))
                    finish()
                } else failed = true
            }
            MasroofTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(MasroofSpacing.screenPaddingLarge),
                        verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!failed) CircularProgressIndicator() else {
                            Text(stringResource(R.string.lab_open_failed))
                            Button(onClick = { attempt++ }) { Text(stringResource(R.string.lab_replica_dashboard_retry)) }
                            LabControls()
                            TextButton(onClick = { finish() }) { Text(stringResource(R.string.lab_return)) }
                        }
                    }
                }
            }
        }
    }
}
