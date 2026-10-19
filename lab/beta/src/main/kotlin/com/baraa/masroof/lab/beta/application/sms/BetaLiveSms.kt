package com.baraa.masroof.lab.beta.application.sms

import android.content.Context
import androidx.work.*
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaIo
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import java.util.concurrent.TimeUnit

/** Registered only by the isolated lab host. No application or receiver hooks in the library manifest. */
object BetaLiveSms {
    const val HOST_PACKAGE="com.baraa.masroof.lab.standalone"
    val changes=MutableSharedFlow<Unit>(extraBufferCapacity=1,onBufferOverflow=BufferOverflow.DROP_OLDEST)
    fun schedule(context: Context,id: String,generation: String) {
        require(context.packageName==HOST_PACKAGE) { "Live intake requires the isolated lab host" }
        val request=OneTimeWorkRequestBuilder<BetaSmsWorker>().setInputData(workDataOf("rawId" to id,"generation" to generation))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag("masroof-beta-sms").build()
        WorkManager.getInstance(context).enqueueUniqueWork("beta-live:$generation:$id",ExistingWorkPolicy.KEEP,request)
    }
    fun resume(context: Context) {
        if(context.packageName!=HOST_PACKAGE) return
        BetaIo.execute {
            try { BetaContainer(context).use { beta ->
                val generation=beta.storageGeneration() ?: return@use
                beta.pendingEvidenceIds().forEach { schedule(context,it,generation) }
            } } catch (_: Exception) { /* Durable rows remain for the next explicit retry or start. */ }
        }
    }
}

class BetaSmsWorker(context: Context,parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        if(applicationContext.packageName!=BetaLiveSms.HOST_PACKAGE) return Result.failure()
        val id=inputData.getString("rawId") ?: return Result.failure()
        val generation=inputData.getString("generation") ?: return Result.failure()
        return try {
            val complete=withContext(BetaIo.dispatcher) { BetaContainer(applicationContext).use { it.processLive(id,generation) } }
            BetaLiveSms.changes.tryEmit(Unit)
            if(complete) Result.success() else incomplete(id)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { incomplete(id) }
    }
    private fun incomplete(id: String): Result {
        val retry=runAttemptCount<5
        android.util.Log.w("BetaLiveSms","stage=PROCESSING failures=1 id=${id.take(8)} retry=$retry")
        return if(retry) Result.retry() else Result.failure()
    }
}
