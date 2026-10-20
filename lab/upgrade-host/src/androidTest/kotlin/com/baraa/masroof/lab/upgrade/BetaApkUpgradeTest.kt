package com.baraa.masroof.lab.upgrade

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.replica.*
import com.baraa.masroof.lab.beta.verification.ReplicaHistory
import com.baraa.masroof.lab.replica.application.AppContainer
import com.baraa.masroof.lab.replica.application.backup.BackupImportOutcome
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Same signed application/UID, version 1 → 2, no clear or uninstall in between. */
class BetaApkUpgradeTest {
    private val host=InstrumentationRegistry.getInstrumentation().targetContext
    private val report get()=File(host.getExternalFilesDir(null),"upgrade-rehearsal").apply {mkdirs()}
    private val baseline get()=File(host.filesDir,"replica-upgrade-baseline.json")
    private val copy get()=File(host.filesDir,"replica-upgrade-copy.mroof")
    private fun guard(version: Int) {
        assertEquals("com.baraa.masroof.beta.upgrade.rehearsal",host.packageName)
        assertEquals(version.toLong(),host.packageManager.getPackageInfo(host.packageName,0).longVersionCode)
        assertEquals(PackageManager.PERMISSION_DENIED,host.checkSelfPermission("android.permission.READ_SMS"))
    }
    @Test fun seedVersionOne()=runBlocking {
        guard(1);LabResetWorkflow.reset(host)
        val c=LabRuntime.container(host);ReplicaHistory.seed(c,40)
        assertTrue(c.databaseBackupService.exportTo(Uri.fromFile(copy),"Synthetic update password 42".toCharArray()).isSuccess)
        val snapshot=JSONObject().put("schema",18).put("financialRows",40).put("fingerprint",ReplicaHistory.fingerprint(c))
        val record=JSONObject().put("version",1).put("uid",Process.myUid()).put("pid",Process.myPid())
            .put("snapshot",snapshot).put("copySha256",ReplicaHistory.sha(copy.readBytes())).put("performance",measure(c))
        baseline.writeText(record.toString());File(report,"before.json").writeText(record.toString(2))
        LabRuntime.close()
    }
    @Test fun verifyVersionTwo()=runBlocking {
        guard(2)
        val before=JSONObject(baseline.readText())
        assertEquals(before.getInt("uid"),Process.myUid());assertNotEquals(before.getInt("pid"),Process.myPid())
        assertEquals(before.getString("copySha256"),ReplicaHistory.sha(copy.readBytes()))
        var c=LabRuntime.container(host);c.awaitPostStartupBackgroundWorkForTesting()
        val expected=before.getJSONObject("snapshot").getString("fingerprint")
        assertEquals(expected,ReplicaHistory.fingerprint(c))
        val performance=measure(c)
        c.processRawSmsUseCase.ingest(ReplicaHistory.message("after-update",99))
        val changed=ReplicaHistory.fingerprint(c);assertNotEquals(expected,changed)
        assertEquals(BackupImportOutcome.AuthenticationFailed,c.databaseBackupService.importFrom(Uri.fromFile(copy),"wrong".toCharArray()))
        assertEquals(changed,ReplicaHistory.fingerprint(c))
        val damaged=File(host.filesDir,"tampered.mroof").apply {writeBytes(copy.readBytes().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()})}
        assertNotEquals(BackupImportOutcome.SuccessNeedsRestart,c.databaseBackupService.importFrom(Uri.fromFile(damaged),"Synthetic update password 42".toCharArray()))
        assertEquals(changed,ReplicaHistory.fingerprint(c))
        assertEquals(BackupImportOutcome.SuccessNeedsRestart,c.databaseBackupService.importFrom(Uri.fromFile(copy),"Synthetic update password 42".toCharArray()))
        c=LabRuntime.container(host);c.awaitPostStartupBackgroundWorkForTesting()
        assertEquals(expected,ReplicaHistory.fingerprint(c))
        val after=JSONObject().put("version",2).put("uid",Process.myUid()).put("pid",Process.myPid())
            .put("snapshot",before.getJSONObject("snapshot")).put("performance",performance)
            .put("encryptedRestoreAcrossUpdate",true).put("wrongPasswordPreservedCurrent",true).put("tamperingPreservedCurrent",true)
            .put("schemaTransition","same production-replica v18 schema; no legacy lab migration")
        File(report,"after.json").writeText(after.toString(2));damaged.delete();LabRuntime.close()
    }
    private suspend fun measure(c: AppContainer): JSONObject {
        repeat(2) {c.dashboardService.loadCurrentOverview()}
        val samples=(1..5).map {
            val start=SystemClock.elapsedRealtimeNanos()
            assertTrue(c.dashboardService.loadCurrentOverview().transactionFacts.isNotEmpty())
            (SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0
        }.sorted()
        return JSONObject().put("definition","production dashboard load over 40 synthetic transactions")
            .put("warmup",2).put("samplesMs",JSONArray(samples)).put("p50Ms",samples[2]).put("p95Ms",samples[4])
            .put("api",android.os.Build.VERSION.SDK_INT).put("device",android.os.Build.MODEL).put("acceptanceBudget",JSONObject.NULL)
    }
}
