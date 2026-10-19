package com.baraa.masroof.lab.beta.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.lab.beta.BetaContainer
import com.baraa.masroof.lab.beta.BetaEnvironment
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BetaManualAssetWorkflowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bank = BankId("BANK_ALJAZIRA")
    private fun id(value: String) = AssetId(Environment.BETA, bank, value)
    private fun draft(value: String, kind: AssetKind = AssetKind.ACCOUNT, suffix: String = "1234", parent: String? = null,
        role: CreditCardRole? = null, editing: Boolean = false, confirmed: Boolean = true) =
        ManualAssetDraft(id(value), kind, suffix, parent?.let(::id), role, confirmed, editing)
    private fun edit(beta: BetaContainer, draft: ManualAssetDraft) = draft.copy(editing = true,
        expectedOriginal = beta.assets().single { it.id == draft.id })
    private fun withBeta(block: (BetaContainer) -> Unit) {
        BetaContainer.resetOffline(context)
        BetaContainer(context).use { beta -> beta.setBankEnabled(bank, true); block(beta) }
        BetaContainer.resetOffline(context)
    }
    @Test fun displayMetadataPersistsWithoutChangingIdentityAndRejectsStaleEdits() = withBeta { beta ->
        val original=beta.saveManualAsset(draft("named").copy(metadata=BetaAssetMetadata(displayName="Salary account",accountType="CURRENT")))
        val first=beta.assetMetadata().getValue(original.id)
        val editing=edit(beta,draft("named")).copy(metadata=first.copy(displayName="Daily account"))
        assertEquals(original,beta.saveManualAsset(editing))
        val updated=beta.assetMetadata().getValue(original.id)
        assertEquals("Daily account",updated.displayName)
        assertTrue(updated.revision>first.revision)
        expectFailure {beta.saveManualAsset(editing.copy(metadata=first.copy(displayName="Stale name")))}
        assertEquals(updated,beta.assetMetadata().getValue(original.id))
        beta.backup();assertTrue(beta.restore())
        assertEquals(updated,beta.assetMetadata().getValue(original.id))
        assertEquals(listOf(original),beta.assets())
    }
    @Test fun arabicAndPersianSuffixesNormalizeWithoutTruncation() = withBeta { beta ->
        assertEquals("1234", normalizeManualAssetSuffix("١٢٣٤"))
        assertEquals("1234", normalizeManualAssetSuffix("۱۲۳۴"))
        assertEquals("1234", normalizeManualAssetSuffix("1٢۳4"))
        assertNull(normalizeManualAssetSuffix("١٢٣٤٥"))
        assertNull(normalizeManualAssetSuffix("1234x"))
        assertNull(normalizeManualAssetSuffix("12 3"))
        assertEquals("1234", beta.saveManualAsset(draft("arabic", suffix = "١٢٣٤")).last4)
        assertEquals("1234", beta.saveManualAsset(draft("persian", suffix = "۱۲۳۴")).last4)
        expectFailure { beta.saveManualAsset(draft("full-number-arabic", suffix = "١٢٣٤٥")) }
        assertEquals(2, beta.assets().size)
    }
    @Test fun explicitConfirmationDraftRetryAndSameSuffixNeverMerge() = withBeta { beta ->
        expectFailure { beta.saveManualAsset(draft("not-confirmed", confirmed = false)) }
        assertTrue(beta.assets().isEmpty())
        val first = beta.saveManualAsset(draft("one"))
        assertEquals(first, beta.saveManualAsset(draft("one")))
        beta.saveManualAsset(draft("two"))
        assertEquals(setOf(id("one"), id("two")), beta.assets().map { it.id }.toSet())
        expectFailure { beta.saveManualAsset(draft("one", suffix = "9999")) }
        assertEquals("1234", beta.assets().single { it.id == id("one") }.last4)
    }
    @Test fun debitCreditAndSharedFacilityConfigurationSaveWithStableIdentity() = withBeta { beta ->
        beta.saveManualAsset(draft("account"))
        beta.saveManualAsset(draft("facility", AssetKind.CREDIT_FACILITY, ""))
        beta.saveManualAsset(draft("primary", AssetKind.CREDIT_CARD, parent = "facility", role = CreditCardRole.PRIMARY))
        beta.saveManualAsset(draft("supp", AssetKind.CREDIT_CARD, parent = "facility", role = CreditCardRole.SUPPLEMENTARY))
        val debit = beta.saveManualAsset(draft("card", AssetKind.DEBIT_CARD, parent = "account"))
        val corrected = beta.saveManualAsset(edit(beta, draft("card", AssetKind.CREDIT_CARD, "9999", "facility", CreditCardRole.SUPPLEMENTARY)))
        assertEquals(debit.id, corrected.id)
        assertEquals(id("facility"), corrected.parentId)
        assertEquals(5, beta.assets().size)
        beta.setBankEnabled(bank, false)
        beta.saveManualAsset(edit(beta, draft("card", AssetKind.CREDIT_CARD, "8888", "facility", CreditCardRole.SUPPLEMENTARY)))
        expectFailure { beta.saveManualAsset(draft("new-while-paused")) }
    }
    @Test fun invalidParentsRoleChangesAndIdentityKindChangesRollBack() = withBeta { beta ->
        beta.saveManualAsset(draft("account"))
        beta.saveManualAsset(draft("facility", AssetKind.CREDIT_FACILITY, ""))
        beta.saveManualAsset(draft("primary", AssetKind.CREDIT_CARD, parent = "facility", role = CreditCardRole.PRIMARY))
        beta.saveManualAsset(draft("supp", AssetKind.CREDIT_CARD, parent = "facility", role = CreditCardRole.SUPPLEMENTARY))
        val before = beta.assets()
        expectFailure { beta.saveManualAsset(draft("orphan", AssetKind.DEBIT_CARD, parent = "missing")) }
        expectFailure { beta.saveManualAsset(draft("wrong", AssetKind.DEBIT_CARD, parent = "facility")) }
        expectFailure { beta.saveManualAsset(draft("wrong-credit", AssetKind.CREDIT_CARD, parent = "account", role = CreditCardRole.PRIMARY)) }
        expectFailure { beta.saveManualAsset(draft("self", AssetKind.DEBIT_CARD, parent = "self")) }
        expectFailure { beta.saveManualAsset(draft("second-primary", AssetKind.CREDIT_CARD, parent = "facility", role = CreditCardRole.PRIMARY)) }
        expectFailure { beta.saveManualAsset(edit(beta, draft("primary", AssetKind.DEBIT_CARD, parent = "account"))) }
        expectFailure { beta.saveManualAsset(edit(beta, draft("account", AssetKind.CREDIT_FACILITY, ""))) }
        expectFailure { beta.saveManualAsset(draft("unknown-edit", editing = true)) }
        expectFailure { beta.saveManualAsset(draft("cross-bank", AssetKind.DEBIT_CARD).copy(parentId = AssetId(Environment.BETA, BankId("other"), "account"))) }
        assertEquals(before, beta.assets())
    }
    @Test fun missingRoleOrParentAndUnsafeSuffixAreRejected() = withBeta { beta ->
        val unlinked=beta.saveManualAsset(draft("no-parent", AssetKind.DEBIT_CARD))
        assertNull(unlinked.parentId)
        expectFailure { beta.saveManualAsset(draft("no-role", AssetKind.CREDIT_CARD)) }
        expectFailure { beta.saveManualAsset(draft("full-number", suffix = "12345678")) }
        expectFailure { beta.saveManualAsset(draft("unknown-bank").copy(id = AssetId(Environment.BETA, BankId("other"), "unknown-bank"))) }
        expectFailure { beta.saveManualAsset(draft("staging").copy(id = AssetId(Environment.MIGRATION_REHEARSAL, bank, "staging"))) }
        assertEquals(listOf(unlinked),beta.assets())
    }
    @Test fun oldUnrelatedRegistryRecordsDoNotBlockManualAccount() = withBeta { beta ->
        beta.registerAsset(OwnedAsset(id("old-facility"), AssetKind.CREDIT_FACILITY))
        beta.registerAsset(OwnedAsset(id("old-supp"), AssetKind.CREDIT_CARD, "4321", id("old-facility"), CreditCardRole.SUPPLEMENTARY))
        beta.saveManualAsset(draft("new-account"))
        assertEquals(3, beta.assets().size)
    }
    @Test fun staleEditRejected() = withBeta { beta ->
        beta.saveManualAsset(draft("account"))
        beta.backup()
        val first = edit(beta, draft("account", suffix = "9999"))
        beta.saveManualAsset(first)
        assertEquals(beta.assets().single(), beta.saveManualAsset(first))
        expectFailure { beta.saveManualAsset(first.copy(last4 = "7777")) }
        assertEquals("9999", beta.assets().single().last4)
        val stale = edit(beta, draft("account", suffix = "8888"))
        assertTrue(beta.restore())
        expectFailure { beta.saveManualAsset(stale) }
        assertEquals("1234", beta.assets().single().last4)
        val before = beta.assets()
        expectFailure { beta.saveManualAsset(stale.copy(id = id("different"))) }
        assertEquals(before, beta.assets())
    }
    @Test fun configurationRestore() {
        BetaContainer.resetOffline(context)
        val production = context.getDatabasePath(BetaEnvironment.PRODUCTION_DATABASE_NAME)
        production.parentFile?.mkdirs()
        val sentinel = "production-test-sentinel".toByteArray()
        production.writeBytes(sentinel)
        BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            beta.saveManualAsset(draft("account"))
            beta.saveManualAsset(draft("debit", AssetKind.DEBIT_CARD, parent = "account"))
            beta.backup()
            beta.saveManualAsset(edit(beta, draft("debit", AssetKind.DEBIT_CARD, "9999", "account")))
            assertTrue(beta.restore())
            assertEquals("1234", beta.assets().single { it.id == id("debit") }.last4)
            beta.saveManualAsset(edit(beta, draft("debit", AssetKind.DEBIT_CARD, "8888", "account")))
        }
        BetaContainer(context).use { beta ->
            assertEquals("8888", beta.assets().single { it.id == id("debit") }.last4)
            assertEquals(id("account"), beta.assets().single { it.id == id("debit") }.parentId)
            beta.reset()
            beta.setBankEnabled(bank, true)
            beta.saveManualAsset(draft("after-reset"))
            assertEquals(listOf(id("after-reset")), beta.assets().map { it.id })
        }
        assertArrayEquals(sentinel, production.readBytes())
        BetaContainer.resetOffline(context)
    }
    @Test fun loanConfigurationRestore() {
        BetaContainer.resetOffline(context)
        val firstDraft = draft("loan-one", AssetKind.LOAN).copy(last4="")
        val secondDraft = draft("loan-two", AssetKind.LOAN)
        val originals = BetaContainer(context).use { beta ->
            beta.setBankEnabled(bank, true)
            expectFailure { beta.saveManualAsset(firstDraft.copy(ownedConfirmed = false)) }
            expectFailure { beta.saveManualAsset(firstDraft.copy(parentId = id("account"))) }
            expectFailure { beta.saveManualAsset(firstDraft.copy(role = CreditCardRole.PRIMARY)) }
            assertTrue(beta.assets().isEmpty())
            val first = beta.saveManualAsset(firstDraft)
            assertNull(first.last4)
            assertEquals(first, beta.saveManualAsset(firstDraft))
            val second = beta.saveManualAsset(secondDraft)
            assertNotEquals(first.id, second.id)
            assertEquals("1234", second.last4)
            assertNull(first.parentId); assertNull(first.creditCardRole)
            beta.backup()
            beta.saveManualAsset(firstDraft.copy(last4 = "5678", editing = true, expectedOriginal = first))
            listOf(first, second)
        }
        BetaContainer(context).use { beta ->
            val changed = beta.assets().single { it.id == firstDraft.id }
            assertEquals("5678", changed.last4)
            expectFailure { beta.saveManualAsset(firstDraft.copy(last4 = "9999", editing = true, expectedOriginal = originals.first())) }
            expectFailure { beta.saveManualAsset(firstDraft.copy(kind = AssetKind.ACCOUNT, editing = true, expectedOriginal = changed)) }
            assertTrue(beta.restore())
            assertEquals(originals.toSet(), beta.assets().toSet())
            beta.setBankEnabled(bank, false)
            expectFailure { beta.saveManualAsset(draft("paused-loan", AssetKind.LOAN)) }
            beta.saveManualAsset(firstDraft.copy(last4 = "8888", editing = true, expectedOriginal = originals.first()))
            assertTrue(beta.discoveryPage().proposals.isEmpty())
        }
        val db = androidx.room.Room.databaseBuilder(context, com.baraa.masroof.lab.beta.data.BetaDatabase::class.java,
            com.baraa.masroof.lab.beta.data.BetaDatabase.NAME).build()
        try {
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM beta_financial_transaction").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        } finally { db.close() }
        BetaContainer.resetOffline(context)
    }
    private fun expectFailure(block: () -> Unit) {
        try { block(); fail("Expected rejected manual configuration") } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
    }
}
