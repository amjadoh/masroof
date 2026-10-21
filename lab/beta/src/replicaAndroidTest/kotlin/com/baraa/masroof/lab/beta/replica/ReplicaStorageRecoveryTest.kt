package com.baraa.masroof.lab.beta.replica

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.verification.ReplicaHistory
import com.baraa.masroof.lab.replica.application.backup.BackupImportOutcome
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReplicaStorageRecoveryTest {
    private val host=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun corruptFinancialFileIsPreservedAndOnlyExplicitLabResetErasesIt()=runBlocking {
        LabResetWorkflow.reset(host)
        val scoped=LabStorageContext(host)
        val file=scoped.getDatabasePath("masroof.db");file.parentFile!!.mkdirs()
        val bytes=("SQLite format 3\u0000"+"damaged-sqlite-test-file-do-not-delete".repeat(100)).toByteArray();file.writeBytes(bytes)
        val damaged=LabRuntime.container(host)
        assertEquals(com.baraa.masroof.lab.replica.application.maintenance.StartupMaintenanceOutcome.BLOCKED,damaged.awaitStartupMaintenance())
        assertArrayEquals(bytes,file.readBytes())
        LabResetWorkflow.reset(host)
        assertFalse(file.exists())
        val c=LabRuntime.container(host);ReplicaHistory.seed(c,1)
        assertEquals(1,c.financialTransactionRepository.listAll().size)
        LabResetWorkflow.reset(host)
    }
    @Test fun originalEncryptedBackupRejectsBadSecretsThenRestoresLedgerAndOwnership()=runBlocking {
        LabResetWorkflow.reset(host)
        var c=LabRuntime.container(host)
        val copy=File(host.filesDir,"replica-financial-backup.mroof")
        try {
            ReplicaHistory.seed(c,4)
            c.labAnalytics.refresh()
            c.labAnalytics.updatePreferences(c.labAnalytics.state.value.preferences.copy(budget = 17100))
            c.labAnalytics.refresh()
            val expected=ReplicaHistory.fingerprint(c)
            assertTrue(c.databaseBackupService.exportTo(Uri.fromFile(copy),"Synthetic backup 42".toCharArray()).isSuccess)
            c.processRawSmsUseCase.ingest(ReplicaHistory.message("after-backup",90))
            val changed=ReplicaHistory.fingerprint(c)
            assertNotEquals(expected,changed)
            assertNotEquals(BackupImportOutcome.SuccessNeedsRestart,c.databaseBackupService.importFrom(Uri.fromFile(copy),"Wrong password".toCharArray()))
            assertEquals(changed,ReplicaHistory.fingerprint(c))
            assertEquals(BackupImportOutcome.SuccessNeedsRestart,c.databaseBackupService.importFrom(Uri.fromFile(copy),"Synthetic backup 42".toCharArray()))
            c=LabRuntime.container(host);c.awaitPostStartupBackgroundWorkForTesting()
            c.labAnalytics.refresh()
            assertEquals(17100L,c.labAnalytics.state.value.preferences.budget)
            assertEquals(expected,ReplicaHistory.fingerprint(c))
            assertEquals(4,c.financialTransactionRepository.listAll().size)
            c.processRawSmsUseCase.ingest(ReplicaHistory.message("after-restore",91))
            assertEquals(5,c.financialTransactionRepository.listAll().size)
            File(host.getExternalFilesDir(null),"replica-backup.txt").writeText("Production encrypted export/import reused. Wrong password preserved current data. Restore recovered 4 transactions and ownership. Next import posted a fifth.\n")
        } finally {copy.delete();LabResetWorkflow.reset(host)}
    }
}
