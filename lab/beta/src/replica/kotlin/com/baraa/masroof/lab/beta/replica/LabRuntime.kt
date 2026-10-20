package com.baraa.masroof.lab.beta.replica

import android.app.Application
import androidx.work.WorkManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.baraa.masroof.lab.replica.MainActivity
import com.baraa.masroof.lab.replica.application.AppContainer

/** A second composition root in the existing process; never casts the host Application. */
object LabRuntime {
    @Volatile private var current: AppContainer? = null
    @Volatile private var resetting = false
    fun context(context: Context): Context = if (context is LabStorageContext) context else LabStorageContext(context)
    fun host(context: Context): Context {
        var base = context
        while (base is ContextWrapper && base !is Application) base = base.baseContext
        return base.applicationContext
    }
    @Synchronized fun container(context: Context): AppContainer {
        check(!resetting) { "Lab reset in progress" }
        current?.let { return it }
        val host = host(context)
        // WorkManager belongs to the host process. Its own DB must never be redirected.
        WorkManager.getInstance(host)
        return AppContainer(LabStorageContext(host)).also { current = it; it.runStartupMaintenance() }
    }
    fun liveEnabled(context: Context): Boolean = context(context).getSharedPreferences("lab_runtime", Context.MODE_PRIVATE).getBoolean("live_enabled", false)
    fun enableLive(context: Context) { context(context).getSharedPreferences("lab_runtime", Context.MODE_PRIVATE).edit().putBoolean("live_enabled", true).commit() }
    @Synchronized internal fun close() {
        try { current?.close() } finally { current = null }
    }
    @Synchronized internal fun beginReset(): AppContainer? {
        check(!resetting) { "Lab reset in progress" }
        resetting = true
        return current
    }
    @Synchronized internal fun endReset() { resetting = false }
    fun beforeRestore() { current?.labInterpretationService?.invalidateForRestore() }
    fun restart(context: Context) {
        close()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }
    fun activity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> activity(context.baseContext)
        else -> null
    }
}
