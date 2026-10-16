package com.baraa.masroof.lab.application

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.data.AndroidLabInboxSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLabInboxSourceTest {
    private class InboxProvider(val rows: List<Array<Any?>>) : ContentProvider() {
        var seenArgs: List<String>? = null
        var seenSelection: String? = null
        var seenUri: Uri? = null
        lateinit var cursor: MatrixCursor
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            seenUri = uri; seenSelection = selection; seenArgs = selectionArgs?.toList()
            cursor = MatrixCursor(arrayOf("_id", "address", "body", "date"))
            rows.forEach { cursor.addRow(it) }
            return cursor
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("No writes allowed")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("No writes allowed")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("No writes allowed")
    }
    @Test fun readOnlyInboxQuery_filtersSenderAndBounds_closesCursor() = runBlocking {
        val provider = InboxProvider(listOf(
            arrayOf("1", " demo ", "original", 100L), arrayOf("2", "OTHER", "unrelated", 150L),
            arrayOf("3", "DEMO", "outside", 200L), arrayOf("4", "DEMO", "", 150L)))
        ShadowContentResolver.registerProviderInternal("sms", provider)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = AndroidLabInboxSource(context.contentResolver).read(setOf("DEMO"), 100, 200)
        assertEquals("content://sms/inbox", provider.seenUri.toString())
        assertEquals("date >= ? AND date < ?", provider.seenSelection)
        assertEquals(listOf("100", "200"), provider.seenArgs)
        assertEquals(1, result.messages.size); assertEquals("original", result.messages.single().body)
        assertEquals(1, result.skipped); assertTrue(provider.cursor.isClosed)
    }
    @Test fun overflowClosesCursorAndThrowsInsteadOfReturningTruncatedSuccess() = runBlocking {
        val provider = InboxProvider((1..1001).map { arrayOf<Any?>(it.toString(), "DEMO", "original", 100L) })
        ShadowContentResolver.registerProviderInternal("sms", provider)
        try {
            AndroidLabInboxSource(ApplicationProvider.getApplicationContext<Context>().contentResolver).read(setOf("DEMO"), 100, 200)
            fail()
        } catch (_: IllegalArgumentException) { }
        assertTrue(provider.cursor.isClosed)
    }
}
