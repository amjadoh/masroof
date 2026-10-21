package com.baraa.masroof.lab.beta.presentation
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.baraa.masroof.lab.beta.replica.LabLaunchActivity
object BetaEntry {
    fun open(context: Context) { context.startActivity(intent(context)) }
    fun intent(context: Context): Intent = Intent(context, LabLaunchActivity::class.java).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
