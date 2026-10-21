package com.baraa.masroof.lab.beta.replica

import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.replica.data.room.MasroofDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LabStorageContextTest {
    private val host = ApplicationProvider.getApplicationContext<Context>()
    @Test fun coldWorkersExposeDefaultFactoryConstructors() {
        listOf("sms.LiveSmsProcessingWorker", "sms.HistoricalDerivedRecoveryWorker", "sms.ExchangeRateEnrichmentWorker", "maintenance.ParsedEventFactsBackfillWorker").forEach {
            assertNotNull(Class.forName("com.baraa.masroof.lab.replica.application.$it").getConstructor(Context::class.java, androidx.work.WorkerParameters::class.java))
        }
    }
    @Test fun contextAndLocaleNeverEscapeLabStorage() {
        val lab = LabStorageContext(host)
        assertSame(lab, lab.applicationContext)
        assertTrue(lab.createConfigurationContext(Configuration()).applicationContext is LabStorageContext)
        assertNotEquals(host.getDatabasePath("masroof.db"), lab.getDatabasePath("masroof.db"))
        assertEquals("lab_replica_masroof.db", lab.getDatabasePath("masroof.db").name)
        assertEquals(lab.getDatabasePath("masroof.db"), lab.getDatabasePath(lab.getDatabasePath("masroof.db").path))
        host.getSharedPreferences("theme",0).edit().putString("value","production").commit()
        lab.getSharedPreferences("theme",0).edit().putString("value","lab").commit()
        assertEquals("production",host.getSharedPreferences("theme",0).getString("value",null))
        assertEquals("lab",lab.createConfigurationContext(Configuration()).getSharedPreferences("theme",0).getString("value",null))
        assertEquals(java.io.File(host.filesDir,"lab_replica").canonicalFile,lab.filesDir.canonicalFile)
    }
    @Test fun choosingLabLanguageDoesNotChangeTheHostsProcessLocale() {
        val before=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            val wrapped=com.baraa.masroof.lab.replica.application.locale.AppLocaleContextFactory.wrap(LabStorageContext(host),"ar")
            assertEquals("ar",wrapped.resources.configuration.locales[0].language)
            assertEquals(java.util.Locale.US,java.util.Locale.getDefault())
            assertTrue(wrapped.applicationContext is LabStorageContext)
        } finally {java.util.Locale.setDefault(before)}
    }
    @Test fun databaseCannotOpenAnAbsoluteProductionPath() {
        val lab = LabStorageContext(host)
        assertThrows(IllegalArgumentException::class.java) { lab.getDatabasePath(host.getDatabasePath("masroof.db").path) }
        assertThrows(IllegalArgumentException::class.java) { lab.getDatabasePath("../masroof.db") }
    }
    @Test fun copiedRoomSchemaHasFourteenProductionTablesAndReopens() {
        val lab = LabStorageContext(host)
        lab.deleteDatabase(MasroofDatabase.NAME)
        fun open() = Room.databaseBuilder(lab,MasroofDatabase::class.java,MasroofDatabase.NAME).allowMainThreadQueries().build()
        val first = open()
        val tables = mutableListOf<String>()
        first.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table')").use { while(it.moveToNext()) tables += it.getString(0) }
        assertEquals(14,tables.size)
        first.openHelper.writableDatabase.query("SELECT identity_hash FROM room_master_table").use { assertTrue(it.moveToFirst()); assertEquals(MasroofDatabase.IDENTITY_HASH,it.getString(0)) }
        first.close()
        open().also { assertTrue(it.openHelper.writableDatabase.isOpen); it.close() }
        assertFalse(host.getDatabasePath(MasroofDatabase.NAME).exists())
    }
}
