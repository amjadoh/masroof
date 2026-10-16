package com.baraa.masroof.lab

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.engine.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic device timing only. No speed threshold, real holdout, M0 baseline or promotion claim. */
@RunWith(AndroidJUnit4::class)
class SharedEngineDeviceMeasurementTest {
    @Test fun measuresCandidateOnDeviceAndVerifiesRepeatedOutcomes() {
        val instant = Instant.parse("2026-10-16T12:00:00Z")
        val engine = SharedLabEngine(Clock.fixed(instant, ZoneOffset.UTC))
        val banks = listOf(BankProfile("BANK_ALJAZIRA", "synthetic", listOf("SYNTHETIC")))
        val library = SharedRuleDefaults.create()
        val bounded = library.copy(network = library.network.copy(
            bankAliases = (0 until 100).associate { i -> (if (i == 0) "BANK_ALJAZIRA" else "SYNTHETIC_$i") to List(20) { "Bank $i alias $it" } },
            sourceBankLabels = List(20) { "Sender label $it" }, destinationBankLabels = List(20) { "Recipient label $it" },
            networkLabels = List(20) { "Network label $it" }))
        val inputs = listOf(
            "purchase" to "POS purchase\nAmount: 10 SAR\nCard: 1234\nAt: SYNTHETIC_MERCHANT",
            "security" to "OTP: 987654\nPOS purchase\nAmount: 10 SAR",
            "unexecuted" to "Pending outgoing transfer\nAmount: 20 SAR",
            "bounded-max-config" to ("Outgoing transfer\nAmount: 67 SAR\n" + "x\n".repeat(8_000)).take(16_000))
        val rows = inputs.map { (id, body) ->
            val config = if (id == "bounded-max-config") bounded else library
            fun parse() = engine.parse(banks, config, "SYNTHETIC", body, "device-measurement", instant)
            val expected = parse()
            repeat(5) { assertEquals(expected, parse()) }
            val durations = List(25) {
                val start = SystemClock.elapsedRealtimeNanos()
                val actual = parse()
                val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                assertEquals(expected, actual)
                elapsed
            }.sorted()
            fun Double.ms() = String.format(Locale.ROOT, "%.3f", this)
            "$id p50_ms=${durations[12].ms()} p95_ms=${durations[23].ms()} max_ms=${durations.last().ms()} repeat=31/31"
        }
        val summary = "M6 device candidate timing; SYNTHETIC ONLY; API=${Build.VERSION.SDK_INT}; " +
            "ABI=${Build.SUPPORTED_ABIS.joinToString(",")}; engine=${SharedLabEngine.VERSION}; library=${library.version}; " +
            "5 warmups + 25 measured calls per case; parse+validation timed; assertions outside timer; " +
            "no M0 comparison, budget acceptance or promotion\n" + rows.joinToString("\n")
        // Instrumentation result is preserved in existing connected-test XML, without SMS/facts.
        // IN_PROGRESS (2), never a terminal test-result code; the JUnit runner owns completion.
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "$summary\n") })
        println(summary)
    }
}
