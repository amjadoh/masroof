package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.verification.SyntheticFinancialHistory
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class BetaFinancialRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val crypto = BetaEncryptedArchive { SecretKeySpec(ByteArray(32) { 19 }, "HmacSHA256") }
    private val password get() = "Synthetic financial password".toCharArray()
    @Before fun clear() { BetaContainer.resetOffline(context) }

    @Test fun richFailures() {
        BetaContainer(context, com.baraa.masroof.lab.beta.application.evidence.LabEvidenceComparer(com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService.independent())).use { SyntheticFinancialHistory.seed(it, 80) }
        val archive = ByteArrayOutputStream().also { BetaBackupRecovery(context).exportArchive(it, password, crypto) }.toByteArray()
        BetaContainer(context).use { it.putMarker("changed", "synthetic"); it.putPreferenceProbe("CURRENT"); it.setTheme("LIGHT") }
        val current = SyntheticFinancialHistory.fingerprint(context).toString()
        val tampered = archive.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val foreign = BetaEncryptedArchive { SecretKeySpec(ByteArray(32) { 20 }, "HmacSHA256") }
        for ((bytes, secret, origin) in listOf(Triple(archive, "Wrong synthetic password".toCharArray(), crypto),
            Triple(tampered, password, crypto), Triple(archive.copyOf(archive.size - 1), password, crypto), Triple(archive, password, foreign))) {
            try { assertThrows(Exception::class.java) { BetaBackupRecovery(context).importArchive(bytes.inputStream(), secret, origin) } }
            finally { secret.fill('\u0000') }
            assertEquals(current, SyntheticFinancialHistory.fingerprint(context).toString())
        }
    }

    @Test fun richCutover() {
        lateinit var fixture: org.json.JSONObject
        BetaContainer(context, com.baraa.masroof.lab.beta.application.evidence.LabEvidenceComparer(com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService.independent())).use { fixture = SyntheticFinancialHistory.seed(it, 80) }
        val original = SyntheticFinancialHistory.fingerprint(context).toString()
        val archive = ByteArrayOutputStream().also { BetaBackupRecovery(context).exportArchive(it, password, crypto) }.toByteArray()
        for (stage in listOf(BetaBackupRecovery.Stage.RESTORE_VALIDATED, BetaBackupRecovery.Stage.JOURNALED,
            BetaBackupRecovery.Stage.DATABASE_INSTALLED, BetaBackupRecovery.Stage.PREFERENCES_INSTALLED, BetaBackupRecovery.Stage.COMMITTED)) {
            BetaContainer(context).use { it.putMarker("changed-${stage.name}", "synthetic"); it.putPreferenceProbe("CURRENT"); it.setTheme("LIGHT") }
            val current = SyntheticFinancialHistory.fingerprint(context).toString()
            assertThrows(IOException::class.java) { BetaBackupRecovery(context) { if (it == stage) throw IOException("Synthetic storage interruption") }
                .importArchive(archive.inputStream(), password, crypto) }
            // Reopen first: uncommitted cutovers recover originals; committed copies stay committed.
            BetaContainer(context).use { }
            assertEquals(if (stage == BetaBackupRecovery.Stage.COMMITTED) original else current,
                SyntheticFinancialHistory.fingerprint(context).toString())
        }
        assertTrue(BetaBackupRecovery(context).importArchive(archive.inputStream(), password, crypto))
        BetaContainer(context).use { SyntheticFinancialHistory.verify(it, fixture) }
        assertEquals(original, SyntheticFinancialHistory.fingerprint(context).toString())
    }
}
