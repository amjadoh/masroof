package com.baraa.masroof.lab.beta.application.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.data.*
import com.baraa.masroof.lab.finance.value.BankId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BetaHistoryStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bank = BankId("BANK_ALJAZIRA")
    private val prefs get() = context.getSharedPreferences(BetaEnvironment.PREFS_NAME, Context.MODE_PRIVATE)
    @Test fun checkpointBackupRestoreResetStayInsideBeta() {
        BetaContainer.resetOffline(context)
        val production = context.getSharedPreferences("production_canary", Context.MODE_PRIVATE)
        production.edit().putString("value", "untouched").commit()
        BetaContainer(context).use { beta ->
            assertThrows(IllegalArgumentException::class.java) { beta.startHistory(30, 1000) }
            beta.setBankEnabled(bank, true)
            val initial = beta.startHistory(30, 1000)
            val store = PreferenceHistorySessionStore(prefs)
            store.save(initial.copy(scanned = 3, excluded = 3, afterDate = 500, afterId = 3))
            beta.backup()
            beta.startHistory(7, 2000)
            assertTrue(beta.restore())
            assertEquals(3, beta.historySession()!!.scanned)
            assertEquals(setOf(bank), beta.historySession()!!.banks)
            beta.reset()
            assertNull(beta.historySession())
        }
        assertEquals("untouched", production.getString("value", null))
        BetaContainer.resetOffline(context)
    }
    @Test fun deniedPermissionAndInvalidPersistedMetadataNeverReadProvider() {
        val source = AndroidHistorySource(context)
        val session = HistorySession(0, 1000, setOf(bank))
        assertThrows(IllegalStateException::class.java) { source.read(session, 25) }
        prefs.edit().putString(PreferenceHistorySessionStore.KEY, "{\"version\":99}").commit()
        assertThrows(IllegalStateException::class.java) { PreferenceHistorySessionStore(prefs).load() }
        BetaContainer.resetOffline(context)
    }
    @Test fun disablingBankPreservesSessionButPreventsNewCapture() {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            beta.startHistory(7, 1000)
            beta.setBankEnabled(bank, false)
            val result = BetaHistoryImport(PreferenceHistorySessionStore(prefs), { _, _ -> listOf(HistoryRow(5, 500, "AlJazira", "synthetic")) }, beta::importEvidence).step()
            assertEquals(1, result.excluded)
            assertEquals(0, result.stored)
            assertTrue(beta.evidencePage().isEmpty())
            assertEquals(setOf(bank), beta.historySession()!!.banks)
        }
        BetaContainer.resetOffline(context)
    }
}
