package com.baraa.masroof.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.application.logging.AppLogService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One launch shape for screen actions: cancellation propagates; any other failure reaches the app log, then the screen. */
internal fun ViewModel.launchLogged(
    log: AppLogService?,
    category: String,
    action: String,
    onError: suspend (Exception) -> Unit = {},
    onFinally: () -> Unit = {},
    block: suspend CoroutineScope.() -> Unit,
): Job = viewModelScope.launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log?.failure(category, action, e)
        onError(e)
    } finally {
        onFinally()
    }
}
