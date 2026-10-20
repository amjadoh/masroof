package com.baraa.masroof.lab.beta

import android.content.Context
import com.baraa.masroof.lab.beta.replica.LabResetWorkflow
import com.baraa.masroof.lab.beta.replica.LabStorageContext
import com.baraa.masroof.lab.beta.application.BetaStartState
import kotlinx.coroutines.runBlocking
import java.io.Closeable

/** Historical M0 host-test probes only. Product screens use LabRuntime's production workflows. */
@Deprecated("Use LabRuntime; this facade exists only for the unchanged host isolation contract")
class BetaContainer(private val context: Context) : Closeable {
    private val probes get() = LabStorageContext(context).getSharedPreferences("compatibility_probes",Context.MODE_PRIVATE)
    fun markerCount() = probes.all.keys.count {it.startsWith("marker:")}
    fun putMarker(key: String,value: String) {check(probes.edit().putString("marker:$key",value).commit())}
    fun putPreferenceProbe(value: String) {check(probes.edit().putString("preference_probe",value).commit())}
    /** Diagnostic snapshot only, never a financial backup. Financial export uses DatabaseBackupService. */
    fun backup(): Map<String,*> = probes.all.toMap()
    fun reset() = resetOffline(context)
    fun startFresh() = reset()
    fun startState() = if (markerCount()==0) BetaStartState.NEW else BetaStartState.EXISTING
    override fun close() = Unit
    companion object {fun resetOffline(context: Context) {runBlocking {LabResetWorkflow.reset(context)}}}
}
