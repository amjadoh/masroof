package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

/** Exercises real Android Keystore, native SQLite and encrypted chunk boundaries. */
class BetaArchiveDeviceTest {
    @Test fun trustedRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        val password = "كلمة مرور Beta تجريبية 2026".toCharArray()
        try {
            BetaContainer(context).use { beta ->
                repeat(1_000) { beta.putMarker("old-$it", "Synthetic long history $it") }
                beta.putPreferenceProbe("old=one\nline two"); beta.setTheme("DARK"); beta.backup()
                val previous = beta.backupDatabaseFile().readBytes()
                val bytes = ByteArrayOutputStream().also { beta.exportArchive(it, password) }.toByteArray()
                assertTrue(bytes.size > 65_536)
                beta.putMarker("new", "synthetic"); beta.putPreferenceProbe("CURRENT"); beta.setTheme("LIGHT")
                assertThrows(Exception::class.java) { beta.importArchive(bytes.inputStream(), "Incorrect password 42".toCharArray()) }
                assertEquals(1_001, beta.markerCount()); assertEquals("CURRENT", beta.preferenceProbe())
                val tampered = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                assertThrows(Exception::class.java) { beta.importArchive(tampered.inputStream(), password) }
                assertEquals(1_001, beta.markerCount())
                assertTrue(beta.importArchive(bytes.inputStream(), password))
                assertEquals(1_000, beta.markerCount()); assertEquals("old=one\nline two", beta.preferenceProbe()); assertEquals("DARK", beta.settings().theme)
                assertArrayEquals(previous, beta.backupDatabaseFile().readBytes())
                // A confirmed Beta reset intentionally retains its nonextractable origin key.
                beta.reset(); assertEquals(0, beta.markerCount())
                assertTrue(beta.importArchive(bytes.inputStream(), password)); assertEquals(1_000, beta.markerCount())
            }
            BetaContainer(context).use { assertEquals(1_000, it.markerCount()); assertEquals("DARK", it.settings().theme) }
        } finally { password.fill('\u0000') }
    }
}
