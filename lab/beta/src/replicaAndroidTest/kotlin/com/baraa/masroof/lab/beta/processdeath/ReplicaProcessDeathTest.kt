package com.baraa.masroof.lab.beta.processdeath

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.baraa.masroof.lab.beta.replica.*
import com.baraa.masroof.lab.beta.verification.ReplicaHistory
import com.baraa.masroof.lab.replica.application.ingestion.BankSmsCaptureResult
import com.baraa.masroof.lab.replica.application.sms.WorkManagerLiveSmsWorkScheduler
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Shell runner kills this UID between methods; each resume runs in a genuinely new process. */
class ReplicaProcessDeathTest {
    private val host=InstrumentationRegistry.getInstrumentation().targetContext
    private val probe get()=host.getSharedPreferences("replica_process_probe",0)
    @Test fun armCapturedBeforeProcessing()=runBlocking<Unit> {
        LabResetWorkflow.reset(host)
        val c=LabRuntime.container(host);ReplicaHistory.own(c)
        assertTrue(com.baraa.masroof.lab.replica.application.ingestion.CaptureBankSmsUseCase(c.rawSmsRepository,com.baraa.masroof.lab.replica.bank.BankSmsRegistry(listOf(com.baraa.masroof.lab.replica.bank.NewEngineSmsAdapter(requireNotNull(c.labInterpretationService)::interpret)))).capture(ReplicaHistory.message("cold-live",19)) is BankSmsCaptureResult.Captured)
        assertTrue(c.financialTransactionRepository.listAll().isEmpty())
        probe.edit().putInt("pid",android.os.Process.myPid()).commit()
    }
    @Test fun resumeCaptureThroughDefaultHostWorkerFactory()=runBlocking<Unit> {
        assertNotEquals(probe.getInt("pid",0),android.os.Process.myPid())
        // First use in the cold process is WorkManager. Worker reconstructs its Lab dependencies.
        val work=WorkManager.getInstance(host)
        val request=WorkManagerLiveSmsWorkScheduler.workRequest("cold-live")
        work.enqueue(request).result.get()
        withTimeout(30_000) {while(work.getWorkInfoById(request.id).get()?.state?.isFinished!=true) delay(50)}
        assertEquals(androidx.work.WorkInfo.State.SUCCEEDED,work.getWorkInfoById(request.id).get()!!.state)
        val c=LabRuntime.container(host)
        assertEquals(1,c.financialTransactionRepository.listAll().size)
        assertEquals("19.00",c.financialTransactionRepository.listAll().single().amount.amount.toPlainString())
        assertTrue(c.reviewRepository.listRequired().isEmpty())
        // Replay through the same worker cannot double-post.
        val replay=WorkManagerLiveSmsWorkScheduler.workRequest("cold-live")
        work.enqueue(replay).result.get()
        withTimeout(30_000) {while(work.getWorkInfoById(replay.id).get()?.state?.isFinished!=true) delay(50)}
        assertEquals(1,c.financialTransactionRepository.listAll().size)
        LabResetWorkflow.reset(host)
    }
    @Test fun armCommittedFinancialHistory()=runBlocking<Unit> {
        LabResetWorkflow.reset(host)
        val c=LabRuntime.container(host);ReplicaHistory.seed(c,20)
        val fingerprint=ReplicaHistory.fingerprint(c)
        probe.edit().putInt("pid",android.os.Process.myPid()).putString("fingerprint",fingerprint).commit()
    }
    @Test fun resumeCommittedHistoryAndResetOnlyLab()=runBlocking<Unit> {
        assertNotEquals(probe.getInt("pid",0),android.os.Process.myPid())
        val c=LabRuntime.container(host);c.awaitPostStartupBackgroundWorkForTesting()
        assertEquals(probe.getString("fingerprint",null),ReplicaHistory.fingerprint(c))
        assertEquals(20,c.financialTransactionRepository.listAll().size)
        assertTrue(c.dashboardService.loadCurrentOverview().transactionFacts.isNotEmpty())
        val production=host.getDatabasePath("masroof.db").also {it.parentFile!!.mkdirs();it.writeText("unrelated-host-sentinel")}
        LabResetWorkflow.reset(host)
        assertEquals("unrelated-host-sentinel",production.readText())
        assertFalse(LabStorageContext(host).getDatabasePath("masroof.db").exists())
        File(host.getExternalFilesDir(null),"replica-process-death.txt").writeText("Cold worker reconstructed dependencies. Captured evidence posted once. Force-stopped committed history retained 20 rows and dashboard. Reset preserved host sentinel.\n")
    }
}
