package com.baraa.masroof.application.interpretation

import android.content.Context
import androidx.work.*
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.application.ingestion.SmsIngestionResult
import kotlinx.coroutines.CancellationException

/** Refresh unresolved interpretations after a rule/model change in one parse batch, then one production derived pass. */
class ModelRefreshWorker(context: Context,params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
      return try {
        val container=(applicationContext as MasroofApplication).container
        if (container.awaitStartupMaintenance()!=com.baraa.masroof.application.maintenance.StartupMaintenanceOutcome.READY) return Result.retry()
        val service=container.labInterpretationService?:return Result.success()
        // Rebuild from durable decisions before comparing versions; a Room invalidation
        // may have suspended personal assistance while this work was being queued.
        service.synchronizeFeedback()
        val expected = inputData.getString("interpretationVersion")
        if (expected != null && expected != service.refreshVersion) return Result.success()
        if (expected == null && inputData.getString("modelVersion") != service.modelVersion) return Result.success()
        val pending=container.reviewRepository.listRequired().filter {
            service.mayRefresh(it.rawSmsId) &&
                container.parsedEventRepository.findByRawSmsId(it.rawSmsId)?.let(service::needsRefresh) == true
        }
        var failed=false
        for (review in pending) {
            // Recheck under the same financial transaction as the parse write: a concurrent
            // posting or manual decision must win over this advisory refresh.
            container.withDatabaseTransaction {
                if (service.mayRefresh(review.rawSmsId)) {
                    val raw=container.rawSmsRepository.getById(review.rawSmsId)
                    if (raw != null && container.processStoredSmsUseCase.reparseAndStore(raw) is SmsIngestionResult.Failed) failed=true
                }
            }
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
        const val TAG="masroof:model-refresh"
        fun enqueue(context: Context,version: String) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("$TAG:$version",ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ModelRefreshWorker>().setInputData(workDataOf("interpretationVersion" to version)).addTag(TAG).build()).result.get()
        }
    }
}
