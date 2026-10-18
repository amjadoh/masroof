package com.baraa.masroof.lab.beta.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** UI continuations stay on Android Main even when an injected/test effect context is unconfined. */
@Composable
internal fun BetaLaunchedEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    LaunchedEffect(*keys) { withContext(Dispatchers.Main.immediate, block) }
}

@Composable
internal fun rememberBetaUiScope(): CoroutineScope = rememberCoroutineScope { Dispatchers.Main.immediate }
