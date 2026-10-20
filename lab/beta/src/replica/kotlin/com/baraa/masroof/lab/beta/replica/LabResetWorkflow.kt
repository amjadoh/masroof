package com.baraa.masroof.lab.beta.replica

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LabResetWorkflow {
    suspend fun reset(context: Context) = withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
        val host = LabRuntime.host(context)
        val container = LabRuntime.beginReset()
        try {
            LabWork.cancel(host)
            suspend fun erase() {
                LabRuntime.close()
                val scoped = LabStorageContext(host)
                scoped.databaseList().forEach { scoped.deleteDatabase(it) }
                val index = host.getSharedPreferences(LabStorageContext.INDEX, Context.MODE_PRIVATE)
                index.getStringSet("names", emptySet())!!.toSet().forEach { check(host.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()) }
                check(index.edit().clear().commit())
                (listOf(scoped.filesDir, scoped.cacheDir, scoped.codeCacheDir, scoped.noBackupFilesDir) + scoped.getExternalFilesDirs(null).filterNotNull() + scoped.externalCacheDirs.filterNotNull()).forEach { check(it.deleteRecursively()) }
                if (java.security.Security.getProvider("AndroidKeyStore")!=null) java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let {
                    if (it.containsAlias("lab_replica.github_update_token")) it.deleteEntry("lab_replica.github_update_token")
                }
            }
            // Drain existing repository leases before retiring Room; new opens are blocked.
            if (container == null) erase() else container.databaseAccessGate.withRestore { erase() }
        } finally { LabRuntime.endReset() }
    }
}
