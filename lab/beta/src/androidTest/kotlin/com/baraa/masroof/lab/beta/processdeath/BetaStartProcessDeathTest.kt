package com.baraa.masroof.lab.beta.processdeath

import android.content.Context
import android.os.Build
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.BetaStartState
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Three separate instrumentation processes, no production host, inbox or user device. */
class BetaStartProcessDeathTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val pidFile get() = File(context.cacheDir, "synthetic-m0-process-pid")
    private fun emulator() { check(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic")) }
    private fun mark() { pidFile.writeText(Process.myPid().toString()) }
    private fun resumed() {
        emulator()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("betaResume"))
        assertNotEquals(pidFile.readText(), Process.myPid().toString())
    }
    @Test fun armUnacknowledged() {
        emulator()
        BetaContainer(context).use { assertEquals(BetaStartState.NEW, it.startState()) }
        mark()
    }
    @Test fun resumeAndAcknowledge() {
        resumed()
        BetaContainer(context).use { assertEquals(BetaStartState.NEW, it.startState()); it.acknowledgeStart() }
        mark()
    }
    @Test fun resumeAcknowledged() {
        resumed()
        BetaContainer(context).use {
            assertEquals(BetaStartState.READY, it.startState())
            assertTrue(it.assets().isEmpty()); assertTrue(it.evidencePage().isEmpty()); assertNull(it.historySession())
            assertTrue(it.bankChoices().all { choice -> choice.state == null })
        }
    }
}
