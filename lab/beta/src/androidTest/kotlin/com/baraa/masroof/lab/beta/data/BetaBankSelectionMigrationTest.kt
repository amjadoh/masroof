package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.application.BankSelectionState
import com.baraa.masroof.lab.finance.value.BankId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaBankSelectionMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)
    @Test fun versionTwoPreservesAssetsAliasesAndStartsWithNoInventedSelection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        helper.createDatabase(BetaDatabase.NAME, 2).apply {
            execSQL("INSERT INTO beta_shell_marker(id,note) VALUES ('before','preserved')")
            execSQL("INSERT INTO beta_asset(environment,bankId,localId,kind,last4,parentLocalId,creditCardRole) VALUES ('BETA','BANK_ALJAZIRA','stable','ACCOUNT','1234',NULL,NULL)")
            execSQL("INSERT INTO beta_asset_alias(environment,bankId,assetLocalId,kind,value,evidenceId) VALUES ('BETA','BANK_ALJAZIRA','stable','ACCOUNT_REFERENCE','1234','fixture')")
            close()
        }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 3, true).apply {
            query("SELECT COUNT(*) FROM beta_asset_alias").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            query("SELECT COUNT(*) FROM beta_bank_selection").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            close()
        }
        BetaContainer(context).use { beta ->
            assertEquals(1, beta.assets().size)
            assertEquals(1, beta.markerCount())
            assertNull(beta.bankChoices().single().state)
            val bank = BankId("BANK_ALJAZIRA")
            beta.setBankEnabled(bank, true)
            beta.backup()
            beta.setBankEnabled(bank, false)
            assertEquals(1, beta.assets().size)
            assertTrue(beta.restore())
            assertEquals(BankSelectionState.ENABLED, beta.bankChoices().single().state)
            beta.setBankEnabled(bank, false)
        }
        BetaContainer(context).use { beta ->
            assertEquals(BankSelectionState.DISABLED, beta.bankChoices().single().state)
            beta.setBankEnabled(BankId("BANK_ALJAZIRA"), true)
            beta.reset()
            assertNull(beta.bankChoices().single().state)
            beta.setBankEnabled(BankId("BANK_ALJAZIRA"), true)
        }
        BetaContainer.resetOffline(context)
    }
    @Test fun versionOneCanUpgradeDirectlyToLatest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BetaContainer.resetOffline(context)
        helper.createDatabase(BetaDatabase.NAME, 1).apply {
            execSQL("INSERT INTO beta_shell_marker(id,note) VALUES ('v1','preserved')")
            close()
        }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 3, true).apply {
            query("SELECT note FROM beta_shell_marker WHERE id='v1'").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
            close()
        }
        BetaContainer.resetOffline(context)
    }
}
