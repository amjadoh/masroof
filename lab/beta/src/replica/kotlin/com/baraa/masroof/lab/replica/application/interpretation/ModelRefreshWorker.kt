package com.baraa.masroof.lab.replica.application.interpretation

import android.content.Context
import androidx.work.*
import com.baraa.masroof.lab.beta.replica.LabRuntime
import com.baraa.masroof.lab.replica.application.ingestion.SmsIngestionResult
import kotlinx.coroutines.CancellationException

/** Refresh unresolved unknowns in one parse batch, then one production derived pass. */
class ModelRefreshWorker(context: Context,params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
      return try {
        val container=LabRuntime.container(applicationContext)
        val service=container.labInterpretationService?:return Result.success()
        if (inputData.getString("modelVersion")!=service.modelVersion) return Result.success()
        val pending=container.reviewRepository.listRequired().filter {
            container.parsedEventRepository.findByRawSmsId(it.rawSmsId)?.event?.messageFamily?.name=="UNKNOWN"
        }
        var failed=false
        for (review in pending) {
            val raw=container.rawSmsRepository.getById(review.rawSmsId)?:continue
            if (container.processStoredSmsUseCase.reparseAndStore(raw) is SmsIngestionResult.Failed) failed=true
        }
        if (pending.isNotEmpty()) {
            container.discoverFromStoredEvents()
            // refreshReviewQueue owns the single reconciliation completion policy and durable retry set.
            container.refreshReviewQueue()
            container.maintenanceCompletionSignal.notifyCompleted()
        }
        if (failed) Result.retry() else Result.success()
    } catch (e: CancellationException) {throw e}
      catch (_: Exception) {Result.retry()}
    }
    companion object {
        const val TAG="lab-replica:model-refresh"
        fun enqueue(context: Context,version: String) {
            WorkManager.getInstance(LabRuntime.host(context)).enqueueUniqueWork("$TAG:$version",ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ModelRefreshWorker>().setInputData(workDataOf("modelVersion" to version)).addTag(TAG).build()).result.get()
        }
    }
}
