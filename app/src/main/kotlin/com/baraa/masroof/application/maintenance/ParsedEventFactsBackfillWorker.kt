package com.baraa.masroof.application.maintenance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Retired work identity: already queued requests finish without opening or reparsing financial data. */
class ParsedEventFactsBackfillWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = Result.success()

    companion object { const val UNIQUE_WORK_NAME = "parsed-event-facts-backfill" }
}
