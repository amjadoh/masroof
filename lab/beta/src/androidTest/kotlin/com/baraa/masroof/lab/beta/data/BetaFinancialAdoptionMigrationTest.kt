package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.beta.application.interpretation.LegacyInterpretationAdapter
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaFinancialAdoptionMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @After fun cleanup() { BetaContainer.resetOffline(context) }

    @Test fun sixToSevenPreservesEveryPriorTableAndAddsOnlyEmptyApprovalState() {
        BetaContainer.resetOffline(context)
        val before = linkedMapOf<String, List<String>>()
        helper.createDatabase(BetaDatabase.NAME, 6).apply {
            execSQL("INSERT INTO beta_shell_marker VALUES ('six','preserved')")
            execSQL("INSERT INTO beta_asset VALUES ('BETA','BANK_ALJAZIRA','owned','ACCOUNT','1234',NULL,NULL)")
            execSQL("INSERT INTO beta_bank_selection VALUES ('BANK_ALJAZIRA','ENABLED')")
            execSQL("INSERT INTO beta_raw_evidence VALUES ('saved','namespace','provider','EXPLICIT_SAMPLE','fixture','synthetic','2026-10-17T00:00:00Z','USER_SUPPLIED','2026-10-17T00:00:00Z','route','[]','[]')")
            execSQL("INSERT INTO beta_evidence_decision VALUES ('saved',4,'KEEP_FOR_REVIEW','2026-10-17T00:00:00Z')")
            query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'beta_%' ORDER BY name").use { cursor ->
                while (cursor.moveToNext()) before[cursor.getString(0)] = emptyList()
            }
            for (table in before.keys.toList()) before[table] = buildList {
                query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
                    add(cursor.columnNames.joinToString("|"))
                    while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") {
                        if (cursor.isNull(it)) "<NULL>" else cursor.getString(it) })
                }
            }
            assertEquals(16, before.size); close()
        }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 7, true).apply {
            for ((table, expected) in before) {
                val actual = buildList {
                    query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
                        add(cursor.columnNames.joinToString("|"))
                        while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") {
                            if (cursor.isNull(it)) "<NULL>" else cursor.getString(it) })
                    }
                }
                assertEquals(table, expected, actual)
            }
            for (table in listOf("beta_financial_adoption", "beta_financial_approval_retry"))
                query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            close()
        }
    }

    @Test fun earliestBetaMigratesToSevenWithoutCreatingFinancialAuthority() {
        BetaContainer.resetOffline(context)
        helper.createDatabase(BetaDatabase.NAME, 1).apply { execSQL("INSERT INTO beta_shell_marker VALUES ('one','preserved')"); close() }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 7, true).apply {
            query("SELECT note FROM beta_shell_marker").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
            query("SELECT COUNT(*) FROM beta_ledger_current").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            close()
        }
    }

    @Test fun approvedArchivedFxAndReparseHoldSurviveActualDatabaseReopen() = runBlocking {
        BetaContainer.resetOffline(context)
        val bank = BankId("BANK_ALJAZIRA")
        val account = OwnedAsset(AssetId(Environment.BETA, bank, "device-owned"), AssetKind.ACCOUNT, "1234")
        val at = Instant.parse("2026-10-17T10:30:00Z")
        var database = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        try {
            BetaAssetRegistry(database).register(account)
            val store = RoomBetaEvidenceStore(database)
            val route = EvidenceRoute(RouteKind.MATCHED, setOf(bank), "fixture-router")
            store.capture(CapturedEvidence("device", EvidenceRequest("device", EvidenceOrigin.EXPLICIT_SAMPLE),
                EvidenceMessage("device", "fixture", "synthetic only", at, ReceiptTimeSource.USER_SUPPLIED), at, route, emptyList()))
            val archive = InterpretationArchive.encode(Interpretation("device", LegacyInterpretationAdapter.VERSION, bank,
                Facts(MessageFamily.PURCHASE, Money.of("23", Currency.USD), occurredAtLocal = LocalDateTime.parse("2026-10-17T13:30:00"),
                    exchangeRate = BigDecimal("3.756957")), ParseStatus.SUCCESS, null, emptyList(), emptySet()))
            store.begin("device", at); store.complete("device", route,
                EvidenceComparison(EvidenceDisposition.COMPARISON_ONLY, emptySet(), archive, "candidate unused"), at)
            val adapter = RoomBetaFinancialAdoption(database)
            val request = BetaFinancialApproval("device", adapter.reviewPage().single().archiveRevision, null,
                FinancialType.EXPENSE, account.id, null)
            assertTrue(adapter.approve(request) is BetaFinancialAdoptionResult.Posted)
            val key = RoomBetaFinancialAdoption.key("device")
            val expected = requireNotNull(RoomBetaFinancialLedger(database).read(key))
            assertEquals(AppliedFx(BigDecimal("3.756957"), "SMS"), expected.command.appliedFx)
            database.close(); database = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
            assertEquals(expected, RoomBetaFinancialLedger(database).read(key))
            assertTrue((RoomBetaFinancialAdoption(database).approve(request) as BetaFinancialAdoptionResult.Posted).result is FinancialPostingResult.AlreadyApplied)
            RoomBetaEvidenceStore(database).begin("device", at)
            assertNull(RoomBetaFinancialLedger(database).read(key))
            assertEquals(expected, RoomBetaFinancialLedger(database).readAudit(key))
            database.close(); database = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
            assertNull(RoomBetaFinancialLedger(database).read(key))
            assertEquals(1, RoomBetaFinancialLedger(database).history(key).size)
        } finally { database.close() }
    }
}
