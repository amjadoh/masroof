package com.baraa.masroof.lab.beta

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

/** One FIFO queue for Beta UI work and container lifetime, including Activity recreation. */
internal object BetaIo {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "masroof-beta-io").apply { isDaemon = true }
    }
    val dispatcher = executor.asCoroutineDispatcher()
    fun execute(action: () -> Unit) { executor.execute(action) }
}
