package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.*
import com.baraa.masroof.lab.beta.verification.SyntheticFinancialHistory
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

/** Current fresh schema only: an encrypted round trip preserves financial and audit history. */
class BetaFinancialArchiveDeviceTest {
    @Test fun encryptedCurrentSchemaPreservesFinancialHistory() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        val password="Synthetic current archive password".toCharArray()
        try {
            BetaContainer(context).use {beta ->
                val fixture=SyntheticFinancialHistory.seed(beta,80)
                beta.backup()
                val before=SyntheticFinancialHistory.fingerprint(context)
                val bytes=ByteArrayOutputStream().also {beta.exportArchive(it,password)}.toByteArray()
                beta.putMarker("after-backup","synthetic");beta.setTheme("DARK")
                assertTrue(beta.importArchive(bytes.inputStream(),password))
                SyntheticFinancialHistory.verify(beta,fixture)
                val after=SyntheticFinancialHistory.fingerprint(context)
                assertEquals(BetaDatabase.VERSION,after.getInt("schema"))
                before.keys().asSequence().filter {it!="preferences"}.forEach {key ->
                    assertEquals(key,before.get(key).toString(),after.get(key).toString())
                }
            }
        } finally {password.fill('\u0000');BetaContainer.resetOffline(context)}
    }
}
