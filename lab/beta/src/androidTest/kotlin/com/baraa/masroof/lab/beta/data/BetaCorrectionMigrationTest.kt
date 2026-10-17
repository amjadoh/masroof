package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.beta.application.corrections.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.corrections.*
import com.baraa.masroof.lab.finance.matching.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.security.MessageDigest
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaCorrectionMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val priorTables = listOf("beta_shell_marker", "beta_asset", "beta_asset_alias", "beta_bank_selection", "beta_raw_evidence",
        "beta_evidence_outcome", "beta_evidence_retry", "beta_evidence_decision", "beta_ledger_current", "beta_ledger_history",
        "beta_ledger_evidence_claim", "beta_ledger_proof_claim")
    private fun fingerprint(database: SupportSQLiteDatabase, table: String): String {
        val text = buildString { database.query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
            cursor.columnNames.forEach { append(it.length).append(':').append(it) }
            while (cursor.moveToNext()) for (column in 0 until cursor.columnCount) {
                append(cursor.getType(column)).append(':')
                if (cursor.isNull(column)) append("NULL;") else cursor.getString(column).let { append(it.length).append(':').append(it).append(';') }
            }
        } }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    @After fun cleanup() { BetaContainer.resetOffline(context) }

    @Test fun versionFivePreservesAllTwelveDataFamiliesAndStartsWithNoCorrectionState() {
        BetaContainer.resetOffline(context)
        val bank = BankId("BANK_ALJAZIRA")
        val first = OwnedAsset(AssetId(Environment.BETA, bank, "first"), AssetKind.ACCOUNT, "1234")
        val second = OwnedAsset(first.id.copy(localId = "second"), AssetKind.ACCOUNT, "5678")
        val out = FinancialClassificationInput(PostingKey(Environment.BETA, "legacy-pair"), PostingRevision(5, 0), FinancialType.SELF_TRANSFER,
            Money.of("42.35", Currency.USD), TransactionTime(Instant.parse("2026-10-17T12:00:00.123456789Z"), ZoneId.of("Asia/Riyadh")),
            FinancialEndpoint.Owned(first.id), FinancialEndpoint.Owned(second.id), listOf(first, second).associateBy { it.id }, setOf("sms-out"), false,
            CorrectionAuthority.USER_FINANCIAL_TYPE, appliedFx = AppliedFx(BigDecimal("3.750000012345678900"), "stored-rate"))
        val incoming = out.copy(key = PostingKey(Environment.BETA, "legacy-incoming"), evidenceIds = setOf("sms-in"))
        val reference = ConfirmedBankTransferReference(bank, "legacy-proof")
        val pair = (SelfTransferMatcher.match(listOf(TransferObservation(out, TransferDirection.OUTBOUND, reference),
            TransferObservation(incoming, TransferDirection.INBOUND, reference)), TransferMatchProof.BankReference(reference)) as TransferMatchResult.Matched).transfer
        val stored = (FinancialPostingRules.prepare(pair) as PostingPreparation.Ready).posting
        val payload = BetaLedgerCodec.encode(stored)
        val fingerprints = linkedMapOf<String, String>()
        helper.createDatabase(BetaDatabase.NAME, 5).apply {
            execSQL("INSERT INTO beta_shell_marker VALUES ('v5','preserved')")
            execSQL("INSERT INTO beta_asset VALUES ('BETA','BANK_ALJAZIRA','first','ACCOUNT','1234',NULL,NULL)")
            execSQL("INSERT INTO beta_asset VALUES ('BETA','BANK_ALJAZIRA','second','ACCOUNT','5678',NULL,NULL)")
            execSQL("INSERT INTO beta_asset_alias VALUES ('BETA','BANK_ALJAZIRA','first','LAST4','1234','manual-owned-confirmation')")
            execSQL("INSERT INTO beta_bank_selection VALUES ('BANK_ALJAZIRA','DISABLED')")
            execSQL("INSERT INTO beta_raw_evidence VALUES ('raw','namespace','provider','EXPLICIT_SAMPLE','synthetic','preserved body','2026-10-17T00:00:00Z','USER_SUPPLIED','2026-10-17T00:00:00Z','fixture','','')")
            execSQL("INSERT INTO beta_evidence_outcome VALUES ('raw','REVIEW','fixture','reason','baseline-original','candidate-original','2026-10-17T00:00:00Z',2)")
            execSQL("INSERT INTO beta_evidence_retry VALUES ('raw','INTERPRETATION',2,'code','2026-10-17T00:00:00Z')")
            execSQL("INSERT INTO beta_evidence_decision VALUES ('raw',3,'KEEP_FOR_REVIEW','2026-10-17T00:00:00Z')")
            execSQL("INSERT INTO beta_ledger_current VALUES ('BETA','legacy-pair',5,0,?)", arrayOf(payload))
            execSQL("INSERT INTO beta_ledger_history VALUES ('BETA','legacy-pair',5,0,?)", arrayOf(payload))
            execSQL("INSERT INTO beta_ledger_evidence_claim VALUES ('BETA','sms-out','legacy-pair')")
            execSQL("INSERT INTO beta_ledger_evidence_claim VALUES ('BETA','sms-in','legacy-pair')")
            execSQL("INSERT INTO beta_ledger_proof_claim VALUES ('BETA','BANK_REFERENCE','BANK_ALJAZIRA','legacy-proof','legacy-pair')")
            priorTables.forEach { table -> fingerprints[table] = fingerprint(this, table) }
            close()
        }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 6, true).apply {
            priorTables.forEach { table -> assertEquals("Changed existing Beta table $table", fingerprints.getValue(table), fingerprint(this, table)) }
            for (table in listOf("beta_correction_current", "beta_correction_audit", "beta_correction_evidence_claim", "beta_correction_proof_claim")) {
                query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
            for (table in listOf("beta_shell_marker", "beta_asset_alias", "beta_bank_selection", "beta_raw_evidence", "beta_evidence_outcome", "beta_evidence_retry", "beta_evidence_decision", "beta_ledger_current", "beta_ledger_history", "beta_ledger_proof_claim")) {
                query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            }
            query("SELECT COUNT(*) FROM beta_asset").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
            query("SELECT COUNT(*) FROM beta_ledger_evidence_claim").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
            query("SELECT payload FROM beta_ledger_current").use { assertTrue(it.moveToFirst()); assertEquals(payload, it.getString(0)) }
            query("SELECT body FROM beta_raw_evidence").use { assertTrue(it.moveToFirst()); assertEquals("preserved body", it.getString(0)) }
            query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            close()
        }
        BetaContainer(context).use { beta ->
            assertEquals(stored, beta.financialPosting(stored.command.key))
            val observation = CorrectionObservation(pair.input.copy(revision = PostingRevision(0, 1), authority = CorrectionAuthority.NONE), ObservationSafety.ORDINARY)
            val suppress = CorrectionRequest("after-migration", pair.input.key, 6, 1, pair.input.evidenceIds, FinancialDecision.SuppressAsNonFinancial)
            assertEquals(BetaLedgerActivity.SUPPRESSED, (beta.submitCorrection(observation, suppress) as BetaCorrectionResult.Applied).current.activity)
            assertNull(beta.financialPosting(pair.input.key)); assertEquals(stored, beta.financialAuditPosting(pair.input.key))
        }
    }

    @Test fun versionOneUpgradesThroughAllSixVersionsWithoutInventingCorrectionOrFinancialRows() {
        BetaContainer.resetOffline(context)
        helper.createDatabase(BetaDatabase.NAME, 1).apply { execSQL("INSERT INTO beta_shell_marker VALUES ('v1','preserved')"); close() }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 6, true).apply {
            query("SELECT note FROM beta_shell_marker").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
            for (table in listOf("beta_correction_current", "beta_correction_audit", "beta_correction_evidence_claim", "beta_correction_proof_claim", "beta_ledger_current")) {
                query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
            close()
        }
    }

    @Test fun versionFiveBackupRestoresLedgerAndMigratesToEmptyCorrectionSchema() {
        BetaContainer.resetOffline(context)
        val asset = OwnedAsset(AssetId(Environment.BETA, BankId("BANK_ALJAZIRA"), "backup-account"), AssetKind.ACCOUNT, "1234")
        val input = FinancialClassificationInput(PostingKey(Environment.BETA, "backup-transaction"), PostingRevision(0, 0), FinancialType.EXPENSE,
            Money.of("42", Currency.SAR), TransactionTime(Instant.parse("2026-10-17T12:00:00Z"), ZoneId.of("Asia/Riyadh")), FinancialEndpoint.Owned(asset.id),
            FinancialEndpoint.External, mapOf(asset.id to asset), setOf("backup-evidence"), true)
        val stored = (FinancialPostingRules.prepare(input) as PostingPreparation.Ready).posting
        val payload = BetaLedgerCodec.encode(stored)
        helper.createDatabase(BetaDatabase.NAME, 5).apply {
            execSQL("INSERT INTO beta_asset VALUES ('BETA','BANK_ALJAZIRA','backup-account','ACCOUNT','1234',NULL,NULL)")
            execSQL("INSERT INTO beta_ledger_current VALUES ('BETA','backup-transaction',0,0,?)", arrayOf(payload))
            execSQL("INSERT INTO beta_ledger_history VALUES ('BETA','backup-transaction',0,0,?)", arrayOf(payload))
            execSQL("INSERT INTO beta_ledger_evidence_claim VALUES ('BETA','backup-evidence','backup-transaction')")
            close()
        }
        com.baraa.masroof.lab.beta.BetaBackupRecovery(context).publish(context.getDatabasePath(BetaDatabase.NAME),
            mapOf(BetaContainer.PREFERENCE_PROBE_KEY to "v5-backup"))
        context.deleteDatabase(BetaDatabase.NAME)
        assertTrue(BetaContainer.restoreOffline(context))
        BetaContainer(context).use { beta ->
            assertEquals("v5-backup", beta.preferenceProbe())
            assertEquals(stored, beta.financialPosting(input.key)); assertNull(beta.correction(input.key))
            assertEquals(1, beta.financialHistory(input.key).size)
            val observation = CorrectionObservation(input, ObservationSafety.ORDINARY)
            val request = CorrectionRequest("backup-suppression", input.key, 1, 0, input.evidenceIds, FinancialDecision.SuppressAsNonFinancial)
            assertEquals(BetaLedgerActivity.SUPPRESSED, (beta.submitCorrection(observation, request) as BetaCorrectionResult.Applied).current.activity)
            assertNull(beta.financialPosting(input.key)); assertEquals(stored, beta.financialAuditPosting(input.key))
        }
    }
}
