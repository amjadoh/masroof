package com.baraa.masroof.lab.beta.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.application.history.HistorySession
import com.baraa.masroof.lab.finance.value.BankId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual provider SQL on the CI emulator; synthetic fixtures only, no production app API. */
@RunWith(AndroidJUnit4::class)
class BetaInboxHistoryTest {
    @Test fun providerKeysetReadsSameTimestampAcrossBoundedPages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
        val date = 2_000_000_000_000L
        val inserted = mutableListOf<android.net.Uri>()
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        // Telephony checks WRITE_SMS app-op independently of permission identity.
        shell("appops set ${context.packageName} READ_SMS allow")
        shell("appops set ${context.packageName} WRITE_SMS allow")
        automation.adoptShellPermissionIdentity("android.permission.WRITE_SMS")
        try {
            repeat(28) { index ->
                inserted += requireNotNull(context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
                    put("address", "BetaFixture")
                    put("body", "Synthetic provider fixture $index")
                    put("date", date)
                    put("read", 1)
                }))
            }
            automation.dropShellPermissionIdentity()
            assertEquals(androidx.core.content.PermissionChecker.PERMISSION_GRANTED,
                androidx.core.content.PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_SMS))
            val source = AndroidHistorySource(context)
            var session = HistorySession(date, date, setOf(BankId("BANK_ALJAZIRA")))
            val first = source.read(session, 25)
            assertEquals(25, first.size)
            session = session.copy(afterDate = first.last().date, afterId = first.last().id)
            val second = source.read(session, 25)
            assertEquals(3, second.size)
            assertEquals(28, (first + second).map { it.id }.distinct().size)
            assertTrue((first + second).all { it.body.startsWith("Synthetic provider fixture") })
            shell("appops set ${context.packageName} READ_SMS ignore")
            assertThrows(IllegalStateException::class.java) { source.read(session, 25) }
        } finally {
            automation.adoptShellPermissionIdentity("android.permission.WRITE_SMS")
            try { inserted.forEach { context.contentResolver.delete(it, null, null) } }
            finally {
                automation.dropShellPermissionIdentity()
                shell("appops set ${context.packageName} WRITE_SMS default")
                shell("appops set ${context.packageName} READ_SMS default")
            }
        }
    }
}
