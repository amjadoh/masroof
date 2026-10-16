package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.asset.AssetKind
import com.baraa.masroof.lab.finance.asset.OwnedAsset
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.finance.value.Environment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaIdentityMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetaDatabase::class.java)

    @Test fun v1ToV2PreservesMarkersAndIdentitySurvivesReopenWithoutTouchingProduction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(BetaDatabase.NAME)
        val production = context.getDatabasePath(BetaEnvironment.PRODUCTION_DATABASE_NAME)
        production.parentFile?.mkdirs()
        val sentinel = "instrumentation-only production sentinel".toByteArray()
        production.writeBytes(sentinel)
        helper.createDatabase(BetaDatabase.NAME, 1).apply {
            execSQL("INSERT INTO beta_shell_marker(id,note) VALUES ('before','preserved')")
            close()
        }
        helper.runMigrationsAndValidate(BetaDatabase.NAME, 2, true).close()
        val asset = OwnedAsset(AssetId(Environment.BETA, BankId("ALJAZIRA"), "stable-1"), AssetKind.ACCOUNT)
        var db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        try {
            assertEquals(1, db.shellMarkerDao().count())
            assertTrue(BetaAssetRegistry(db).all().isEmpty())
            BetaAssetRegistry(db).register(asset)
        } finally { db.close() }
        db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        try {
            assertEquals(listOf(asset), BetaAssetRegistry(db).all())
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            db.openHelper.readableDatabase.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
        } finally { db.close() }
        assertArrayEquals(sentinel, production.readBytes())
        context.deleteDatabase(BetaDatabase.NAME)
        context.deleteDatabase(BetaEnvironment.PRODUCTION_DATABASE_NAME)
        Unit
    }
}
