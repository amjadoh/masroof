package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.finance.value.BankId
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaEvidenceMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)

    @Test fun versionThreePreservesRegistrySelectionAndStartsWithEmptyEvidenceTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        helper.createDatabase(BetaDatabase.NAME, 3).apply {
            execSQL("INSERT INTO beta_shell_marker(id,note) VALUES ('before','preserved')")
            execSQL("INSERT INTO beta_asset(environment,bankId,localId,kind,last4,parentLocalId,creditCardRole) VALUES ('BETA','BANK_ALJAZIRA','stable','ACCOUNT','1234',NULL,NULL)")
            execSQL("INSERT INTO beta_asset_alias(environment,bankId,assetLocalId,kind,value,evidenceId) VALUES ('BETA','BANK_ALJAZIRA','stable','ACCOUNT_REFERENCE','1234','fixture')")
            execSQL("INSERT INTO beta_bank_selection(bankId,state) VALUES ('BANK_ALJAZIRA','ENABLED')")
            close()
        }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 4, true).apply {
            listOf("beta_raw_evidence", "beta_evidence_outcome", "beta_evidence_retry", "beta_evidence_decision").forEach { table ->
                query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
            query("SELECT COUNT(*) FROM beta_asset_alias").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            query("SELECT state FROM beta_bank_selection").use { assertTrue(it.moveToFirst()); assertEquals("ENABLED", it.getString(0)) }
            query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            close()
        }
        BetaContainer(context).use { beta ->
            assertEquals(1, beta.assets().size)
            val input = EvidenceMessage("explicit-1", "AlJazira", "POS Purchase\nCard: 1234\nAmount: 42 SAR",
                Instant.parse("2026-10-16T12:00:00Z"), ReceiptTimeSource.USER_SUPPLIED)
            val request = EvidenceRequest("device-test", EvidenceOrigin.EXPLICIT_SAMPLE)
            val result = beta.importEvidence(request, listOf(input)).single() as EvidenceResult.Stored
            assertNull(result.summary.retryStage)
            beta.decideEvidence(result.summary.id, 0, EvidenceReviewDecision.KEEP_FOR_REVIEW)
            beta.backup()
            beta.setBankEnabled(BankId("BANK_ALJAZIRA"), false)
            assertTrue(beta.restore())
            assertEquals(1, beta.evidenceSummary(result.summary.id).manualRevision)
            assertNull(beta.reprocessEvidence(result.summary.id).retryStage)
        }
        BetaContainer.resetOffline(context)
    }

    @Test fun versionOneCanUpgradeThroughAllBetaVersionsToEvidenceSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        helper.createDatabase(BetaDatabase.NAME, 1).apply {
            execSQL("INSERT INTO beta_shell_marker(id,note) VALUES ('v1','preserved')")
            close()
        }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 4, true).apply {
            query("SELECT note FROM beta_shell_marker WHERE id='v1'").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
            close()
        }
        BetaContainer.resetOffline(context)
    }
}
