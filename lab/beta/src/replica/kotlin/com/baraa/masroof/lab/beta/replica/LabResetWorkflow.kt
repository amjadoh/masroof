package com.baraa.masroof.lab.beta.replica

import android.content.Context
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LabResetWorkflow {
    suspend fun reset(context: Context) = withContext(Dispatchers.IO) {
        val host = LabRuntime.host(context)
        val container = LabRuntime.beginReset()
        try {
            val work = WorkManager.getInstance(host)
            work.cancelAllWorkByTag("lab-replica:live-sms-processing").result.get()
            work.cancelAllWorkByTag("lab-replica:model-refresh").result.get()
            work.cancelAllWorkByTag("lab-replica:exchange-rate-enrichment").result.get()
            listOf(LabRecoveryWorker.NAME, "lab-replica:historical-derived-recovery", "lab-replica:parsed-event-facts-backfill", "lab-replica:masroof_update_check").forEach { work.cancelUniqueWork(it).result.get() }
            suspend fun erase() {
                LabRuntime.close()
                val scoped = LabStorageContext(host)
                scoped.databaseList().forEach { scoped.deleteDatabase(it) }
                val index = host.getSharedPreferences(LabStorageContext.INDEX, Context.MODE_PRIVATE)
                index.getStringSet("names", emptySet())!!.toSet().forEach { check(host.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()) }
                check(index.edit().clear().commit())
                listOf(scoped.filesDir, scoped.cacheDir, scoped.noBackupFilesDir).forEach { check(it.deleteRecursively()) }
                if (java.security.Security.getProvider("AndroidKeyStore")!=null) java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let {
                    if (it.containsAlias("lab_replica.github_update_token")) it.deleteEntry("lab_replica.github_update_token")
                }
            }
            // Drain existing repository leases before retiring Room; new opens are blocked.
            if (container == null) erase() else container.databaseAccessGate.withRestore { erase() }
        } finally { LabRuntime.endReset() }
    }
}
