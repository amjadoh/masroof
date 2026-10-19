package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BetaOwnershipMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)
    @Test fun twelveToThirteenPreservesEvidenceAssetsAndManualHistoryWithoutInventingDecisions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        try {
            helper.createDatabase(BetaDatabase.NAME, 12).apply {
                execSQL("INSERT INTO beta_shell_marker(id,note) VALUES ('retained','synthetic')")
                execSQL("INSERT INTO beta_asset(environment,bankId,localId,kind,last4,parentLocalId,creditCardRole) VALUES ('BETA','BANK_ALJAZIRA','owned','ACCOUNT','1234',NULL,NULL)")
                execSQL("INSERT INTO beta_raw_evidence(id,namespace,providerId,origin,sender,body,receivedAt,timeSource,capturedAt,captureRoute,captureSelections,historyBanks) VALUES ('raw','migration','one','EXPLICIT_SAMPLE','AlJazira','synthetic original','2026-10-18T00:00:00Z','USER_SUPPLIED','2026-10-18T00:00:00Z','{}','[]','[]')")
                execSQL("INSERT INTO beta_evidence_decision(rawId,revision,decision,updatedAt) VALUES ('raw',2,'KEEP_FOR_REVIEW','2026-10-18T00:00:00Z')")
                execSQL("INSERT INTO beta_ledger_current(environment,transactionId,correction,interpretation,payload) VALUES ('BETA','archived',4,3,'opaque-existing-payload')")
                close()
            }
            helper.runMigrationsAndValidate(BetaDatabase.NAME, 13, true).use { db ->
                for (table in listOf("beta_ownership_decision", "beta_ownership_exclusion")) db.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                db.query("SELECT payload FROM beta_ledger_current WHERE transactionId='archived'").use { assertTrue(it.moveToFirst()); assertEquals("opaque-existing-payload", it.getString(0)) }
                db.query("SELECT revision FROM beta_evidence_decision WHERE rawId='raw'").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
                db.query("SELECT COUNT(*) FROM beta_asset").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
                db.query("SELECT body FROM beta_raw_evidence").use { assertTrue(it.moveToFirst()); assertEquals("synthetic original", it.getString(0)) }
                db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            }
        } finally { BetaContainer.resetOffline(context) }
    }
}
