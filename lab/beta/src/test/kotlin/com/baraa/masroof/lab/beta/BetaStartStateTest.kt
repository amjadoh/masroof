package com.baraa.masroof.lab.beta

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.application.BetaStartState
import com.baraa.masroof.lab.beta.data.BetaDatabase
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BetaStartStateTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(BetaEnvironment.PREFS_NAME, 0)
    @Before fun clean() { BetaContainer.resetOffline(context) }
    @After fun close() { BetaContainer.resetOffline(context) }

    @Test fun freshStateIsReadOnlyAndIgnoresProductionAndParsingLab() {
        val protected = listOf(BetaEnvironment.PRODUCTION_DATABASE_NAME, BetaEnvironment.PARSING_LAB_DATABASE_NAME,
            BetaEnvironment.MIGRATION_STAGING_DATABASE_NAME).map { name ->
            context.getDatabasePath(name).apply { parentFile?.mkdirs(); writeText("synthetic-protected-$name") }
        }
        val hashes = protected.map(::hash)
        BetaEnvironment.forbiddenPreferenceNames.forEach { name ->
            assertTrue(context.getSharedPreferences(name, 0).edit().putString("onboarded", "true").commit())
        }
        repeat(2) {
            BetaContainer(context).use { beta ->
                assertEquals(BetaStartState.NEW, beta.startState())
                assertTrue(beta.assets().isEmpty()); assertTrue(beta.evidencePage().isEmpty())
                assertTrue(beta.financialReviewPage().isEmpty()); assertNull(beta.historySession())
                assertTrue(beta.bankChoices().all { it.state == null })
            }
            assertTrue(prefs.all.isEmpty())
        }
        assertTrue(snapshot().values.all { it.isEmpty() })
        assertEquals(hashes, protected.map(::hash))
        BetaEnvironment.forbiddenPreferenceNames.forEach { name ->
            assertEquals("true", context.getSharedPreferences(name, 0).getString("onboarded", null))
        }
    }

    @Test fun explicitStartSurvivesReopenWithoutSelectingBankOrImportingMessages() {
        BetaContainer(context).use { beta ->
            assertEquals(BetaStartState.NEW, beta.startState()); beta.acknowledgeStart(); beta.acknowledgeStart()
        }
        BetaContainer(context).use { beta ->
            assertEquals(BetaStartState.READY, beta.startState())
            assertTrue(beta.bankChoices().all { it.state == null }); assertNull(beta.historySession())
        }
        assertEquals(mapOf(BetaContainer.START_KEY to "acknowledged-v1"), prefs.all)
        assertTrue(snapshot().values.all { it.isEmpty() })
    }

    @Test fun existingSurvivesBackup() {
        val bank = BankId("BANK_ALJAZIRA")
        val asset = OwnedAsset(AssetId(Environment.BETA, bank, "synthetic-start"), AssetKind.ACCOUNT, "1234")
        BetaContainer(context).use { beta ->
            beta.registerAsset(asset); beta.setBankEnabled(bank, true); beta.putMarker("annotation", "synthetic-keep")
            beta.putPreferenceProbe("keep"); beta.setTheme("DARK")
            val input = FinancialClassificationInput(PostingKey(Environment.BETA, "synthetic-purchase"), PostingRevision(0, 0),
                FinancialType.EXPENSE, Money.of("42.35", Currency.SAR),
                TransactionTime(Instant.parse("2026-10-17T12:00:00Z"), ZoneId.of("Asia/Riyadh")),
                FinancialEndpoint.Owned(asset.id), FinancialEndpoint.External, mapOf(asset.id to asset), setOf("synthetic-evidence"), true)
            assertTrue(beta.postFinancial(input) is FinancialPostingResult.Applied)
        }
        val before = snapshot(); val beforePrefs = prefs.all.toMap()
        BetaContainer(context).use { beta ->
            assertEquals(BetaStartState.EXISTING, beta.startState()); assertEquals(BetaStartState.EXISTING, beta.startState())
        }
        assertEquals(before, snapshot()); assertEquals(beforePrefs, prefs.all)
        BetaContainer(context).use { beta -> beta.acknowledgeStart(); beta.backup(); beta.putPreferenceProbe("changed"); assertTrue(beta.restore()) }
        BetaContainer(context).use { beta ->
            assertEquals(BetaStartState.READY, beta.startState()); assertEquals("keep", beta.preferenceProbe())
            assertEquals(listOf(asset), beta.assets())
        }
        assertEquals(before, snapshot())
        assertEquals(beforePrefs + (BetaContainer.START_KEY to "acknowledged-v1"), prefs.all)
    }

    @Test fun preferenceOnlyAndMarkerOnlyHistoryAreNotFresh() {
        assertTrue(prefs.edit().putString("previous_annotation", "keep").commit())
        BetaContainer(context).use { assertEquals(BetaStartState.EXISTING, it.startState()) }
        assertTrue(prefs.edit().clear().commit()) // Synthetic fixture only.
        BetaContainer(context).use { it.putMarker("only-annotation", "keep"); assertEquals(BetaStartState.EXISTING, it.startState()) }
    }

    @Test fun unknownEntryVersionFailsClosedWithoutRewritingPreferences() {
        assertTrue(prefs.edit().putString(BetaContainer.START_KEY, "future-v2").commit())
        BetaContainer(context).use { beta ->
            assertThrows(IllegalStateException::class.java) { beta.startState() }
            assertThrows(IllegalStateException::class.java) { beta.acknowledgeStart() }
        }
        assertEquals(mapOf(BetaContainer.START_KEY to "future-v2"), prefs.all)
    }

    @Test fun corruptDatabaseIsRetainedWithoutCreatingFreshExperience() {
        val file = context.getDatabasePath(BetaEnvironment.DATABASE_NAME)
        file.parentFile?.mkdirs(); file.writeText("synthetic-corrupt-database-keep")
        val before = hash(file)
        assertThrows(Exception::class.java) { BetaContainer(context).use { it.startState() } }
        assertTrue(file.exists()); assertEquals(before, hash(file)); assertTrue(prefs.all.isEmpty())
    }

    private fun hash(file: java.io.File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    private fun snapshot(): Map<String, List<List<String?>>> {
        val db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        try {
            val sql = db.openHelper.writableDatabase
            val names = sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertEquals(21, names.size)
            return names.associateWith { table -> sql.query("SELECT * FROM \"$table\"").use { c ->
                buildList { while (c.moveToNext()) add((0 until c.columnCount).map { i -> if (c.isNull(i)) null else c.getString(i) }) }.sortedBy { it.toString() }
            } }
        } finally { db.close() }
    }
}
