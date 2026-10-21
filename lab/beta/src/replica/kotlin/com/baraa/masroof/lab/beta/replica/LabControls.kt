package com.baraa.masroof.lab.beta.replica

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.R
import kotlinx.coroutines.launch

@Composable
fun LabControls() {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var resetFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    TextButton(onClick = { menu = true }, enabled = !busy) { Text("LAB") }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.lab_return)) }, onClick = { menu = false; LabRuntime.activity(context)?.finish() })
        DropdownMenuItem(text = { Text(stringResource(R.string.lab_reset)) }, onClick = { menu = false; confirm = true })
    }
    if (confirm) AlertDialog(onDismissRequest = { if (!busy) confirm = false },
        title = { Text(stringResource(R.string.lab_reset)) },
        text = { Text(stringResource(if (resetFailed) R.string.lab_reset_failed else R.string.lab_reset_explanation)) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    resetFailed = false
                    LabResetWorkflow.reset(context)
                    confirm = false
                    LabRuntime.restart(context)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                  catch (_: Exception) { resetFailed = true }
                  finally { busy = false }
            }
        }) { Text(stringResource(R.string.lab_reset)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirm = false }) { Text(stringResource(android.R.string.cancel)) } })
}
