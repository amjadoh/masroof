package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaLearningMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)

    @Test fun versionTenRetainsExistingRowsWithoutPredictingLabels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        try {
            helper.createDatabase(BetaDatabase.NAME, 10).apply {
                execSQL("INSERT INTO beta_shell_marker(id,note) VALUES ('retained','synthetic-before')")
                execSQL("INSERT INTO beta_bank_selection(bankId,state) VALUES ('BANK_ALJAZIRA','DISABLED')")
                execSQL("INSERT INTO beta_asset(environment,bankId,localId,kind,last4,parentLocalId,creditCardRole) VALUES ('BETA','BANK_ALJAZIRA','owned','ACCOUNT','1234',NULL,NULL)")
                execSQL("INSERT INTO beta_raw_evidence(id,namespace,providerId,origin,sender,body,receivedAt,timeSource,capturedAt,captureRoute,captureSelections,historyBanks) VALUES ('raw','migration','one','EXPLICIT_SAMPLE','AlJazira','synthetic original','2026-10-18T00:00:00Z','USER_SUPPLIED','2026-10-18T00:00:00Z','{}','[]','[]')")
                execSQL("INSERT INTO beta_evidence_outcome(rawId,disposition,route,reasons,baselineJson,candidateJson,updatedAt,attempts) VALUES ('raw','REVIEW','{}','[]','historical-baseline','historical-candidate','2026-10-18T00:00:00Z',3)")
                execSQL("INSERT INTO beta_evidence_decision(rawId,revision,decision,updatedAt) VALUES ('raw',2,'KEEP_FOR_REVIEW','2026-10-18T00:00:00Z')")
                // Opaque archived payload: this migration never decodes or posts it.
                execSQL("INSERT INTO beta_ledger_current(environment,transactionId,correction,interpretation,payload) VALUES ('BETA','existing-ledger',4,3,'opaque-archived-payload')")
                close()
            }
            helper.runMigrationsAndValidate(BetaDatabase.NAME, 11, true).apply {
                query("SELECT COUNT(*) FROM beta_learning_label").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                query("SELECT baselineJson,candidateJson,attempts FROM beta_evidence_outcome WHERE rawId='raw'").use {
                    assertTrue(it.moveToFirst()); assertEquals("historical-baseline", it.getString(0)); assertEquals("historical-candidate", it.getString(1)); assertEquals(3, it.getInt(2))
                }
                query("SELECT revision FROM beta_evidence_decision WHERE rawId='raw'").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
                query("SELECT body FROM beta_raw_evidence WHERE id='raw'").use { assertTrue(it.moveToFirst()); assertEquals("synthetic original", it.getString(0)) }
                query("SELECT payload,correction,interpretation FROM beta_ledger_current WHERE transactionId='existing-ledger'").use {
                    assertTrue(it.moveToFirst()); assertEquals("opaque-archived-payload", it.getString(0)); assertEquals(4, it.getInt(1)); assertEquals(3, it.getInt(2))
                }
                query("SELECT state FROM beta_bank_selection").use { assertTrue(it.moveToFirst()); assertEquals("DISABLED", it.getString(0)) }
                query("SELECT localId,last4 FROM beta_asset").use { assertTrue(it.moveToFirst()); assertEquals("owned", it.getString(0)); assertEquals("1234", it.getString(1)) }
                query("SELECT note FROM beta_shell_marker").use { assertTrue(it.moveToFirst()); assertEquals("synthetic-before", it.getString(0)) }
                query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
                close()
            }
        } finally { BetaContainer.resetOffline(context) }
    }

    @Test fun versionOneReachesElevenWithoutReset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        try {
            helper.createDatabase(BetaDatabase.NAME, 1).apply {
                execSQL("INSERT INTO beta_shell_marker(id,note) VALUES ('v1','retained')"); close()
            }
            helper.runMigrationsAndValidate(BetaDatabase.NAME, 11, true).apply {
                query("SELECT note FROM beta_shell_marker WHERE id='v1'").use { assertTrue(it.moveToFirst()); assertEquals("retained", it.getString(0)) }
                query("SELECT COUNT(*) FROM beta_learning_label").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                close()
            }
        } finally { BetaContainer.resetOffline(context) }
    }
}
