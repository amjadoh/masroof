package com.baraa.masroof.lab.beta.replica

import android.content.Context
import androidx.work.WorkManager

/** Cancel only this composition root before replacing/deleting its storage. */
internal object LabWork {
    fun cancel(context: Context) {
        com.baraa.masroof.lab.replica.application.analytics.AnalyticsNotifications.clear(context)
        val work=WorkManager.getInstance(LabRuntime.host(context))
        listOf("lab-replica:live-sms-processing","lab-replica:model-refresh","lab-replica:exchange-rate-enrichment","lab-replica:analytics")
            .forEach {work.cancelAllWorkByTag(it).result.get()}
        listOf(LabRecoveryWorker.NAME,"lab-replica:historical-derived-recovery","lab-replica:parsed-event-facts-backfill","lab-replica:masroof_update_check")
            .forEach {work.cancelUniqueWork(it).result.get()}
    }
}
