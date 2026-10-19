package com.baraa.masroof.lab.beta.application.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reschedules only initialized lab retry rows after boot or an APK replacement. */
class BetaRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED) || !BetaLiveSms.enabled(context)) return
        val pending=goAsync()
        com.baraa.masroof.lab.beta.BetaIo.execute {
            try {BetaLiveSms.resumeOnIo(context.applicationContext)}
            finally {pending.finish()}
        }
    }
}
