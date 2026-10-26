package com.baraa.masroof.application.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.patterns.MessagePatternsWorkflow
import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.lab.bank.patterns.*
import com.baraa.masroof.testsupport.TestStorageContext
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MessagePatternBackupTest {
    @Test(timeout = 30_000) fun exportPairsItsFinancialSnapshotWithTheSamePatternGeneration() = runBlocking {
        val context = TestStorageContext(ApplicationProvider.getApplicationContext<Context>(), "pattern-export")
        val gate = DatabaseAccessGate()
        val store = MessagePatternStore(context)
        val workflow = MessagePatternsWorkflow(store, gate, Dispatchers.Unconfined)
        val original = patternArchive("STC")
        store.restoreRaw(original)
        val database = Room.databaseBuilder(context, MasroofDatabase::class.java, MasroofDatabase.NAME)
            .allowMainThreadQueries().build()
        val snapshotReady = CountDownLatch(1)
        val continueExport = CountDownLatch(1)
        val secret = "synthetic-pattern-backup".toCharArray()
        try {
            database.openHelper.writableDatabase
            val archive = context.cacheDir.resolve("patterns.masroof")
            val backup = DatabaseBackupService(context, database, { database.close() }, "test",
                clockEpochMillis = {
                    snapshotReady.countDown()
                    check(continueExport.await(10, TimeUnit.SECONDS))
                    1_700_000_000_000L
                }, databaseAccessGate = gate, kdfIterations = 2)
            val exporting = async(Dispatchers.IO) { backup.exportTo(Uri.fromFile(archive), secret.copyOf()) }
            assertTrue(snapshotReady.await(10, TimeUnit.SECONDS))
            val bank = store.load().banks.single()
            val saving = async(start = CoroutineStart.UNDISPATCHED) {
                workflow.saveBank(1, bank.copy(patterns = listOf(bank.patterns.single().copy(revision = 2, enabled = false))))
            }
            assertFalse(saving.isCompleted)
            continueExport.countDown()
            assertTrue(exporting.await().isSuccess)
            assertEquals(2L, saving.await().generation)
            val plain = context.cacheDir.resolve("plain.zip")
            archive.inputStream().use { input -> plain.outputStream().use { BackupEnvelope.decrypt(input, it, secret.copyOf()) } }
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(plain.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) { entries[entry.name] = zip.readBytes(); entry = zip.nextEntry }
            }
            assertEquals(setOf("manifest.json", "preferences.json", "masroof.db"), entries.keys)
            val saved = BackupPackageCodec.decodePreferences(requireNotNull(entries["preferences.json"]).toString(Charsets.UTF_8))
            assertEquals(original, saved.messagePatterns)
            assertEquals(18, BackupPackageCodec.decodeManifest(requireNotNull(entries["manifest.json"]).toString(Charsets.UTF_8)).roomVersion)
        } finally {
            continueExport.countDown()
            database.close()
            secret.fill('\u0000')
            context.getDatabasePath(MasroofDatabase.NAME).parentFile?.deleteRecursively()
        }
        Unit
    }

    @Test fun rollbackRestoresEvenAnInvalidOriginalConfigurationInsteadOfSilentlyDiscardingIt() {
        val context = TestStorageContext(ApplicationProvider.getApplicationContext<Context>(), "pattern-rollback")
        val live = context.getDatabasePath(MasroofDatabase.NAME)
        val store = MessagePatternStore(context)
        try {
            SQLiteDatabase.openOrCreateDatabase(live, null).use { db -> db.execSQL("CREATE TABLE original_marker(value TEXT)") }
            store.restoreRaw("{broken-original", validate = false)
            DatabaseRestoreRecovery.writeSnapshots(live, DatabaseRestoreRecovery.capturePreferences(context),
                DatabaseRestoreRecovery.incomingPreferences(BackupPreferencesSnapshot(true, true, historicalImportCompleted = true,
                    languageTag = "ar", themeMode = "SYSTEM", messagePatterns = patternArchive("IMPORTED"))))
            DatabaseRestoreRecovery.parkOriginal(live)
            live.writeText("corrupt installed database")
            store.restoreRaw(patternArchive("IMPORTED"))
            DatabaseRestoreRecovery.writeJournal(live, DatabaseRestoreRecovery.Stage.NEW_INSTALLED, false)
            DatabaseRestoreRecovery.recover(context)
            assertEquals("{broken-original", store.captureRaw())
            assertThrows(Exception::class.java) { store.load() }
            SQLiteDatabase.openDatabase(live.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT * FROM original_marker", null).use { assertEquals(0, it.count) }
            }
        } finally {
            DatabaseRestoreRecovery.deleteRestoreArtifacts(live)
            live.parentFile?.deleteRecursively()
        }
    }
}

internal fun patternArchive(bank: String): String {
    val body = "Synthetic event\nAmount:10SAR"
    val pattern = SavedMessagePattern.fromChoices("pattern", bank, "Sender $bank", "PURCHASE", body,
        listOf(FieldChoice("amount", body.indexOf("10SAR"), body.length)))
    return MessagePatternConfigurationCodec.encode(MessagePatternConfiguration(1,
        listOf(BankMessagePatterns(bank, listOf(pattern.sender), patterns = listOf(pattern)))))
}
