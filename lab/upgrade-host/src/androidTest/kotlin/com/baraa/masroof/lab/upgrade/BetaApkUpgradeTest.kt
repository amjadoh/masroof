package com.baraa.masroof.lab.upgrade

import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.beta.verification.SyntheticFinancialHistory
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Run seed under version1, replace APK using install-r, then run verify under version2. */
class BetaApkUpgradeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val report get() = File(context.getExternalFilesDir(null), "upgrade-rehearsal").also { check(it.mkdirs() || it.isDirectory) }
    private val baseline get() = File(context.filesDir, "synthetic_upgrade_baseline.json")
    private val copy get() = File(context.filesDir, "synthetic_upgrade_copy.mbeta")
    private val password get() = "Synthetic update password 42".toCharArray()

    private fun guard(version: Int) {
        assertEquals("com.baraa.masroof.beta.upgrade.rehearsal", context.packageName)
        assertEquals(version.toLong(), context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission("android.permission.READ_SMS"))
    }

    @Test fun seedVersionOne() {
        guard(1)
        BetaContainer.resetOffline(context)
        lateinit var fixture: JSONObject
        lateinit var performance: JSONObject
        BetaContainer(context, com.baraa.masroof.lab.beta.application.evidence.LabEvidenceComparer(com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService.sharedRules())).use { beta ->
            fixture = SyntheticFinancialHistory.seed(beta, 600)
            SyntheticFinancialHistory.verify(beta, fixture)
            performance = measure(beta)
            val secret = password
            try { copy.outputStream().use { beta.exportArchive(it, secret) } } finally { secret.fill('\u0000') }
        }
        val snapshot = SyntheticFinancialHistory.fingerprint(context)
        val record = JSONObject().put("version", 1).put("uid", Process.myUid()).put("pid", Process.myPid())
            .put("fixture", fixture).put("snapshot", snapshot).put("performance", performance).put("copySha256", sha(copy))
        baseline.writeText(record.toString())
        File(report, "before.json").writeText(record.toString(2))
    }

    @Test fun verifyVersionTwo() {
        guard(2)
        val before = JSONObject(baseline.readText())
        assertEquals(Process.myUid(), before.getInt("uid"))
        assertNotEquals(Process.myPid(), before.getInt("pid"))
        assertEquals(before.getString("copySha256"), sha(copy))
        assertEquals(before.getJSONObject("snapshot").toString(), SyntheticFinancialHistory.fingerprint(context).toString())
        val fixture = before.getJSONObject("fixture")
        lateinit var performance: JSONObject
        BetaContainer(context).use { beta ->
            SyntheticFinancialHistory.verify(beta, fixture)
            performance = measure(beta)
            beta.putMarker("after-update", "synthetic mutation"); beta.putPreferenceProbe("CHANGED"); beta.setTheme("LIGHT")
        }
        val changed = SyntheticFinancialHistory.fingerprint(context).toString()
        BetaContainer(context).use { beta ->
            assertThrows(Exception::class.java) { copy.inputStream().use { beta.importArchive(it, "Wrong synthetic password".toCharArray()) } }
        }
        assertEquals(changed, SyntheticFinancialHistory.fingerprint(context).toString())
        val damaged = File(context.filesDir, "synthetic_damaged.mbeta")
        damaged.writeBytes(copy.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        val secret = password
        try {
            BetaContainer(context).use { beta ->
                assertThrows(Exception::class.java) { damaged.inputStream().use { beta.importArchive(it, secret) } }
            }
            assertEquals(changed, SyntheticFinancialHistory.fingerprint(context).toString())
            BetaContainer(context).use { beta ->
                assertTrue(copy.inputStream().use { beta.importArchive(it, secret) })
                SyntheticFinancialHistory.verify(beta, fixture)
            }
        } finally { secret.fill('\u0000'); check(damaged.delete()) }
        val restored = SyntheticFinancialHistory.fingerprint(context)
        assertEquals(before.getJSONObject("snapshot").toString(), restored.toString())
        File(report, "after.json").writeText(JSONObject().put("version", 2).put("uid", Process.myUid()).put("pid", Process.myPid())
            .put("snapshot", restored).put("performance", performance).put("encryptedRestoreAcrossUpdate", true)
            .put("wrongPasswordPreservedCurrent", true).put("tamperingPreservedCurrent", true)
            .put("schemaTransition", "same source schema; not a production or 9-to-10 APK migration").toString(2))
    }

    private fun measure(beta: BetaContainer): JSONObject {
        repeat(5) { beta.dashboard(SyntheticFinancialHistory.query) }
        val samples = mutableListOf<Double>()
        var peakHeap = 0L; var peakPssKiB = 0
        repeat(25) {
            val start = SystemClock.elapsedRealtimeNanos()
            val result = beta.dashboard(SyntheticFinancialHistory.query)
            samples += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            check(result.snapshot.transactions.isNotEmpty())
            val runtime = Runtime.getRuntime(); peakHeap = maxOf(peakHeap, runtime.totalMemory() - runtime.freeMemory())
            val memory = Debug.MemoryInfo(); Debug.getMemoryInfo(memory); peakPssKiB = maxOf(peakPssKiB, memory.totalPss)
        }
        val sorted = samples.sorted()
        val plans = JSONObject()
        // Exact bounded fact/adoption/current queries used by RoomBetaDashboardSource.
        SQLiteDatabase.openDatabase(context.getDatabasePath(BetaEnvironment.DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val queries = mapOf(
                "facts" to "SELECT f.* FROM beta_dashboard_evidence_facts f ORDER BY f.evidenceId LIMIT 10001",
                "auxiliary" to "SELECT * FROM beta_dashboard_aux_facts ORDER BY evidenceId LIMIT 10001",
                "current" to "SELECT l.transactionId,l.correction,l.interpretation,l.payload,c.correction,c.interpretation,c.transition,c.activity,c.payload FROM beta_ledger_current l LEFT JOIN beta_correction_current c ON c.environment=l.environment AND c.transactionId=l.transactionId WHERE l.environment='BETA' ORDER BY l.transactionId LIMIT 10001",
            )
            queries.forEach { (name, sql) -> db.rawQuery("EXPLAIN QUERY PLAN $sql", null).use { rows ->
                val lines = JSONArray(); while (rows.moveToNext()) lines.put(rows.getString(3)); check(lines.length() > 0)
                plans.put(name, JSONObject().put("sql", sql).put("plan", lines))
            } }
        }
        return JSONObject().put("definition", "synchronous Beta dashboard read+hydration+production; no UI rendering")
            .put("warmup", 5).put("samplesMs", JSONArray(samples)).put("p50Ms", sorted[12]).put("p95Ms", sorted[23])
            .put("peakObservedJvmUsedBytes", peakHeap).put("peakObservedProcessPssKiB", peakPssKiB)
            .put("api", android.os.Build.VERSION.SDK_INT).put("device", android.os.Build.MODEL)
            .put("queryPlans", plans).put("m0Comparison", "unavailable: M0 uses a different tiny Robolectric workload and no device baseline")
            .put("acceptanceBudget", JSONObject.NULL)
    }

    private fun sha(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
