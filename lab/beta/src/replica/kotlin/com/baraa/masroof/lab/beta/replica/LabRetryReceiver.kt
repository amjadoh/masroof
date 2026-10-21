package com.baraa.masroof.lab.beta.replica

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.baraa.masroof.lab.replica.application.maintenance.StartupMaintenanceOutcome
import kotlinx.coroutines.*

/** Boot only enqueues durable work; financial recovery never runs in the broadcast lifetime. */
class LabRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED) || !LabRuntime.liveEnabled(context)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WorkManager.getInstance(LabRuntime.host(context)).enqueueUniqueWork(
                    LabRecoveryWorker.NAME, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<LabRecoveryWorker>().addTag(LabRecoveryWorker.NAME).build(),
                ).result.get()
            } finally { pending.finish() }
        }
    }
}
class LabRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val container = LabRuntime.container(applicationContext)
        if (container.awaitStartupMaintenance() != StartupMaintenanceOutcome.READY) Result.retry() else {
            container.liveSmsIntake.schedulePendingProcessing()
            Result.success()
        }
    } catch (e: CancellationException) { throw e }
      catch (_: Exception) { Result.retry() }
    companion object { const val NAME = "lab-replica:startup-recovery" }
}
