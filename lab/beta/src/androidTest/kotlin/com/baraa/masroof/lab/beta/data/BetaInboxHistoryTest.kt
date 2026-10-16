package com.baraa.masroof.lab.beta.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.application.history.HistorySession
import com.baraa.masroof.lab.finance.value.BankId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual provider SQL on the CI emulator. Only the fixture writer is a shell process. */
@RunWith(AndroidJUnit4::class)
class BetaInboxHistoryTest {
    @Test fun providerKeysetReadsSameTimestampAcrossBoundedPages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.baraa.masroof.lab.beta.test", context.packageName)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun read(pipe: ParcelFileDescriptor) = ParcelFileDescriptor.AutoCloseInputStream(pipe).use { it.readBytes().toString(Charsets.UTF_8) }
        fun shell(command: String): String {
            val output = if (Build.VERSION.SDK_INT >= 34) {
                val pipes = automation.executeShellCommandRwe(command)
                pipes[1].close()
                val stdout = read(pipes[0])
                val stderr = read(pipes[2])
                assertTrue("Fixture shell stderr: $stderr", stderr.isBlank())
                stdout
            } else read(automation.executeShellCommand(command))
            assertFalse("Fixture shell failed: $output", output.contains("Exception") || output.contains("Error:"))
            return output
        }
        fun mode(packageName: String, op: String): String {
            val text = shell("appops get --uid $packageName $op")
            return Regex("$op: +([a-z]+)").find(text)?.groupValues?.get(1) ?: "default"
        }
        automation.grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
        val previousRead = mode(context.packageName, "READ_SMS")
        val previousShellWrite = mode("com.android.shell", "WRITE_SMS")
        val date = 2_000_000_000_000L
        val token = "BetaFixture_" + java.util.UUID.randomUUID().toString().replace("-", "")
        val inserted = linkedMapOf<String, Uri?>()
        fun find(body: String): List<Long> = context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf("_id"), "body = ? AND address = ? AND date = ?", arrayOf(body, "BetaFixture", date.toString()), null)!!.use { c ->
                buildList { while (c.moveToNext()) add(c.getLong(0)) }
            }
        try {
            shell("appops set --uid ${context.packageName} READ_SMS allow")
            // Actual shell UID has WRITE_SMS. Adopting its permission inside the reader UID
            // still returns the provider's denied-insert placeholder URI /0.
            shell("appops set --uid com.android.shell WRITE_SMS allow")
            assertEquals("allow", mode("com.android.shell", "WRITE_SMS"))
            repeat(28) { index ->
                val body = "${token}_$index" // Fixed ASCII tokens, never user input or shell metacharacters.
                inserted[body] = null // Own-token cleanup remains possible if readback fails.
                shell("content insert --uri content://sms/inbox --bind address:s:BetaFixture --bind body:s:$body --bind date:l:$date --bind type:i:1 --bind sub_id:i:-1 --bind read:i:1")
                // The CLI discards its return URI; empty stdout is not an insertion proof.
                val ids = find(body)
                assertEquals("Real fixture must be visible to the normal READ_SMS caller", 1, ids.size)
                val id = ids.single()
                assertTrue("Denied-insert placeholder /0 is not a fixture", id > 0)
                val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
                assertFalse("Each fixture needs a distinct provider identity", uri in inserted.values)
                inserted[body] = uri
            }
            assertEquals(28, inserted.values.filterNotNull().distinct().size)
            assertEquals(androidx.core.content.PermissionChecker.PERMISSION_GRANTED,
                androidx.core.content.PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_SMS))
            context.contentResolver.query(inserted.values.first()!!, arrayOf("_id", "date", "type", "sub_id"), null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(date, cursor.getLong(1))
                assertEquals(Telephony.Sms.MESSAGE_TYPE_INBOX, cursor.getInt(2))
                assertEquals(android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID, cursor.getInt(3))
            }
            val source = AndroidHistorySource(context)
            var session = HistorySession(date, date, setOf(BankId("BANK_ALJAZIRA")))
            val first = source.read(session, 25)
            assertEquals(25, first.size)
            session = session.copy(afterDate = first.last().date, afterId = first.last().id)
            val second = source.read(session, 25)
            assertEquals(3, second.size)
            assertEquals(28, (first + second).map { it.id }.distinct().size)
            assertTrue((first + second).all { it.body.startsWith(token) })
            shell("appops set --uid ${context.packageName} READ_SMS ignore")
            assertThrows(IllegalStateException::class.java) { source.read(session, 25) }
        } finally {
            try {
                // Verify deletion with a permitted normal reader, never an ignored empty cursor.
                shell("appops set --uid ${context.packageName} READ_SMS allow")
                inserted.forEach { (body, uri) ->
                    if (uri != null) shell("content delete --uri $uri")
                    else shell("content delete --uri content://sms --where body='$body'")
                    assertTrue("Owned fixture must be removed", find(body).isEmpty())
                }
            } finally {
                shell("appops set --uid com.android.shell WRITE_SMS $previousShellWrite")
                shell("appops set --uid ${context.packageName} READ_SMS $previousRead")
            }
        }
    }
}
