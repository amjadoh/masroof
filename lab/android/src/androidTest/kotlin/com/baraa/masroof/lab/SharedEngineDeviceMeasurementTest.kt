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

/** Synthetic, paired reference/candidate device timing. No approved budget or promotion claim. */
@RunWith(AndroidJUnit4::class)
class SharedEngineDeviceMeasurementTest {
    @Test fun measuresPairedReferenceAndCandidateOnDeviceAndVerifiesRepeatedOutcomes() {
        val instant = Instant.parse("2026-10-16T12:00:00Z")
        val engine = SharedLabEngine(Clock.fixed(instant, ZoneOffset.UTC))
        val banks = listOf(BankProfile("BANK_ALJAZIRA", "synthetic", listOf("ALJAZIRA")))
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
            // Both scopes include routing, parsing, finalization/validation and LabOutcome projection.
            // Reference has its fixed one-bank rules; only candidate accepts max dynamic config.
            fun candidate() = engine.parse(banks, config, "ALJAZIRA", body, "device-measurement", instant)
            fun reference() = LabResultProjection.baseline("ALJAZIRA", body, "device-measurement", instant)
            val candidateExpected = candidate()
            val referenceExpected = reference()
            repeat(5) { index ->
                if (index % 2 == 0) {
                    assertEquals(referenceExpected, reference()); assertEquals(candidateExpected, candidate())
                } else {
                    assertEquals(candidateExpected, candidate()); assertEquals(referenceExpected, reference())
                }
            }
            val candidateTimes = mutableListOf<Double>()
            val referenceTimes = mutableListOf<Double>()
            fun measure(parse: () -> LabOutcome, expected: LabOutcome, durations: MutableList<Double>) {
                val start = SystemClock.elapsedRealtimeNanos()
                val actual = parse()
                val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                assertEquals(expected, actual)
                durations += elapsed
            }
            repeat(25) { index ->
                if (index % 2 == 0) {
                    measure(::reference, referenceExpected, referenceTimes)
                    measure(::candidate, candidateExpected, candidateTimes)
                } else {
                    measure(::candidate, candidateExpected, candidateTimes)
                    measure(::reference, referenceExpected, referenceTimes)
                }
            }
            fun stats(name: String, durations: List<Double>): String {
                val sorted = durations.sorted()
                fun Double.ms() = String.format(Locale.ROOT, "%.3f", this)
                return "$name p50_ms=${sorted[12].ms()} p95_ms=${sorted[23].ms()} max_ms=${sorted.last().ms()} repeat=31/31"
            }
            "$id ${stats("reference", referenceTimes)}; ${stats("candidate", candidateTimes)}; " +
                "reference_status=${referenceExpected.status}; candidate_status=${candidateExpected.status}; " +
                "cross_engine_semantic_approval=false"
        }
        val summary = "M6 paired device reference/candidate timing; SYNTHETIC ONLY; API=${Build.VERSION.SDK_INT}; " +
            "ABI=${Build.SUPPORTED_ABIS.joinToString(",")}; engine=${SharedLabEngine.VERSION}; library=${library.version}; " +
            "5 warmups + 25 measured calls per engine per case; alternating order; routing+parse+validation+projection timed; assertions outside timer; " +
            "same input/sender/clock/device; fixed one-bank reference vs dynamic candidate; bounded-max-config is asymmetric stress, not equivalent work; " +
            "no M0 budget acceptance, real-holdout precision or promotion\n" + rows.joinToString("\n")
        // Instrumentation result is preserved in existing connected-test XML, without SMS/facts.
        // IN_PROGRESS (2), never a terminal test-result code; the JUnit runner owns completion.
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "$summary\n") })
        println(summary)
    }
}
