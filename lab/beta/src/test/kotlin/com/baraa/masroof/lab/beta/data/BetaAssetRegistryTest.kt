package com.baraa.masroof.lab.beta.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.asset.AssetKind
import com.baraa.masroof.lab.finance.asset.CreditCardRole
import com.baraa.masroof.lab.finance.asset.OwnedAsset
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.finance.value.Environment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BetaAssetRegistryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BetaDatabase
    private val bank = BankId("ALJAZIRA")
    private fun id(local: String, bank: BankId = this.bank) = AssetId(Environment.BETA, bank, local)
    private fun asset(local: String, kind: AssetKind, parent: String? = null,
        role: CreditCardRole? = null) = OwnedAsset(id(local), kind, "1234", parent?.let { id(it) }, role)

    @Before fun open() {
        context.deleteDatabase(BetaDatabase.NAME)
        db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
    }
    @After fun close() { db.close(); context.deleteDatabase(BetaDatabase.NAME) }

    @Test fun collisionsStaySeparateAndBankScopedAcrossReopen() = runBlocking {
        val production = context.getDatabasePath(BetaEnvironment.PRODUCTION_DATABASE_NAME)
        production.parentFile?.mkdirs()
        production.writeText("untouched production bytes")
        val registry = BetaAssetRegistry(db)
        val alias = BetaAssetAlias(BetaAliasKind.LAST4, "1234", "explicit-user-registration")
        registry.register(asset("one", AssetKind.CREDIT_CARD), listOf(alias))
        registry.register(asset("two", AssetKind.CREDIT_CARD), listOf(alias))
        registry.register(OwnedAsset(id("one", BankId("OTHER")), AssetKind.DEBIT_CARD, "1234"), listOf(alias))
        assertEquals(listOf("one", "two"), registry.candidates(bank, alias.kind, alias.value).map { it.id.localId })
        db.close()
        db = Room.databaseBuilder(context, BetaDatabase::class.java, BetaDatabase.NAME).build()
        assertEquals(3, BetaAssetRegistry(db).all().size)
        assertEquals("untouched production bytes", production.readText())
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        db.openHelper.readableDatabase.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
    }

    @Test fun preservesDebitAndSharedCreditFacilityLinks() = runBlocking {
        val r = BetaAssetRegistry(db)
        val account = asset("account", AssetKind.ACCOUNT)
        val facility = asset("facility", AssetKind.CREDIT_FACILITY)
        val debit = asset("debit", AssetKind.DEBIT_CARD, "account")
        val primary = asset("primary", AssetKind.CREDIT_CARD, "facility", CreditCardRole.PRIMARY)
        val supplementary = asset("supplementary", AssetKind.CREDIT_CARD, "facility", CreditCardRole.SUPPLEMENTARY)
        listOf(account, facility, debit, primary, supplementary).forEach { r.register(it) }
        assertEquals(setOf(account, facility, debit, primary, supplementary), r.all().toSet())
        expectFailure { db.openHelper.writableDatabase.execSQL("DELETE FROM beta_asset WHERE localId = 'facility'") }
        assertEquals(5, r.all().size)
    }

    @Test fun rejectsWrongParentAndRollsBackDuplicateAliasBatch() = runBlocking {
        val r = BetaAssetRegistry(db)
        r.register(asset("account", AssetKind.ACCOUNT))
        expectFailure { r.register(asset("credit", AssetKind.CREDIT_CARD, "account")) }
        expectFailure { r.register(asset("missing", AssetKind.DEBIT_CARD, "unknown")) }
        val alias = BetaAssetAlias(BetaAliasKind.CARD_REFERENCE, "token", "evidence")
        expectFailure { r.register(asset("duplicate-alias", AssetKind.CREDIT_CARD), listOf(alias, alias)) }
        assertEquals(listOf("account"), r.all().map { it.id.localId })
    }

    @Test fun databaseForeignKeysRejectOrphanAndCrossBankParent() = runBlocking {
        db.assetDao().insert(BetaAssetEntity("BETA", "OTHER", "parent", "ACCOUNT", null, null, null))
        expectFailure { db.assetDao().insert(BetaAssetEntity("BETA", bank.value, "child", "DEBIT_CARD", null, "parent", null)) }
        expectFailure { db.assetDao().insertAlias(BetaAssetAliasEntity("BETA", bank.value, "missing", "LAST4", "1234", "evidence")) }
        assertEquals(1, db.assetDao().all().size)
    }

    @Test fun duplicateStableIdCannotOverwriteAndRehearsalIsRejected() = runBlocking {
        val r = BetaAssetRegistry(db)
        r.register(asset("stable", AssetKind.ACCOUNT))
        expectFailure { r.register(asset("stable", AssetKind.CREDIT_CARD)) }
        expectFailure { r.register(OwnedAsset(AssetId(Environment.MIGRATION_REHEARSAL, bank, "staging"), AssetKind.ACCOUNT)) }
        assertEquals(AssetKind.ACCOUNT, r.all().single().kind)
    }

    @Test fun manualSuffixCorrectionPreservesMessageEvidenceAndReplacesOnlyManualAlias() = runBlocking {
        val selectedBank = BankId("BANK_ALJAZIRA")
        db.bankSelectionDao().upsert(BetaBankSelectionEntity(selectedBank.value, "ENABLED"))
        val identity = AssetId(Environment.BETA, selectedBank, "manual")
        val original = OwnedAsset(identity, AssetKind.ACCOUNT, "1234")
        val registry = BetaAssetRegistry(db)
        registry.register(original, listOf(BetaAssetAlias(BetaAliasKind.LAST4, "1234", "imported-evidence")))
        val workflow = com.baraa.masroof.lab.beta.application.BetaManualAssetWorkflow(registry)
        val first = workflow.save(com.baraa.masroof.lab.beta.application.ManualAssetDraft(identity, AssetKind.ACCOUNT,
            "5678", ownedConfirmed = true, editing = true, expectedOriginal = original))
        val second = workflow.save(com.baraa.masroof.lab.beta.application.ManualAssetDraft(identity, AssetKind.ACCOUNT,
            "9999", ownedConfirmed = true, editing = true, expectedOriginal = first))
        assertEquals(listOf(second), registry.candidates(selectedBank, BetaAliasKind.LAST4, "1234"))
        assertTrue(registry.candidates(selectedBank, BetaAliasKind.LAST4, "5678").isEmpty())
        assertEquals(listOf(second), registry.candidates(selectedBank, BetaAliasKind.LAST4, "9999"))
        assertEquals(1, registry.all().size)
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Expected invalid write to fail")
    }
}
