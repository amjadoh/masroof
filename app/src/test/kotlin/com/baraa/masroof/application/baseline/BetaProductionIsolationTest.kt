package com.baraa.masroof.application.baseline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.repository.RawSmsInsertResult
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEnvironment
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BetaProductionIsolationTest {
    @Test
    fun betaShell_doesNotMutateProductionLedgerOrPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<MasroofApplication>()
        BetaContainer.resetOffline(context)
        val production = context.container
        val sentinelId = "m0-lab-beta-isolation-${System.nanoTime()}"
        val before = production.rawSmsRepository.listIdsByReceivedAt().toSet()
        val inserted = production.rawSmsRepository.insertIfAbsent(
            RawSms(
                id = sentinelId,
                sender = "BankAlJazira",
                body = "beta isolation sentinel",
                receivedAt = Instant.parse("2026-01-01T00:00:00Z"),
                deviceMessageId = sentinelId,
                bodyHash = sentinelId,
            ),
        )
        assertEquals(RawSmsInsertResult.Inserted, inserted)
        val productionIds = before + sentinelId
        BetaEnvironment.forbiddenPreferenceNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .putString(SENTINEL_KEY, "production-$name")
                .commit()
        }
        val lab = context.getDatabasePath(BetaEnvironment.PARSING_LAB_DATABASE_NAME)
        lab.parentFile?.mkdirs()
        lab.writeBytes("parsing-lab-sentinel".toByteArray())
        val labHash = sha256(lab)

        BetaContainer(context).use { beta ->
            assertEquals(0, beta.markerCount())
            beta.putMarker("shell", "probe")
            beta.putPreferenceProbe("A")
            beta.backup()
            beta.reset()
            assertEquals(0, beta.markerCount())
        }

        assertEquals(productionIds, production.rawSmsRepository.listIdsByReceivedAt().toSet())
        BetaEnvironment.forbiddenPreferenceNames.forEach { name ->
            val value = context.getSharedPreferences(name, Context.MODE_PRIVATE).getString(SENTINEL_KEY, null)
            assertEquals("production-$name", value)
        }
        assertEquals(labHash, sha256(lab))
        assertTrue(context.getDatabasePath(MasroofDatabase.NAME).name == MasroofDatabase.NAME)
        assertTrue(context.getDatabasePath(BetaEnvironment.DATABASE_NAME).name == BetaEnvironment.DATABASE_NAME)
    }

    private fun sha256(file: java.io.File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SENTINEL_KEY: String = "m0_sentinel"
    }
}
