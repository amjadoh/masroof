package com.baraa.masroof.lab.upgrade

/** Separate lab package only. The production Application and SMS receiver are never involved. */
class StandaloneLabApplication: android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        com.baraa.masroof.lab.beta.application.sms.BetaLiveSms.resume(this)
    }
}
