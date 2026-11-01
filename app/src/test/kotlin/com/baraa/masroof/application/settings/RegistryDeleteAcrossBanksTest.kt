package com.baraa.masroof.application.settings

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.bank.contract.FiveBankSmsTest
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.LoanReference
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.sms.model.ProviderSmsRecord
import com.baraa.masroof.testsupport.DashboardLedgerWorld
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Each bundled bank's own posted fixtures block deletion; its unused items delete. STC is covered by StcSingleAccountJourneyTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RegistryDeleteAcrossBanksTest {
    @Test fun everyBankRefusesItemsWithPostedTransactionsAndDeletesUnusedOnes() = runBlocking {
        val byBank = FiveBankSmsTest.rows.filter { it.getValue("status").jsonPrimitive.content == "SUCCESS" }
            .groupBy { it.getValue("bankId").jsonPrimitive.content }
        assertEquals(setOf("BANK_ALINMA", "BANK_BARQ", "BANK_EMIRATES_NBD", "BANK_SAB", "BANK_SNB"), byBank.keys - "BANK_ALJAZIRA")
        byBank.forEach { (bankId, rows) ->
            DashboardLedgerWorld(ApplicationProvider.getApplicationContext(), FiveBankSmsTest.adapters.getValue(bankId)).use { world ->
                val workflow = SettingsRegistryWorkflow(world.cards, world.accounts, world.loans,
                    OwnershipConfirmationService(world.accounts, world.cards, world.loans), world.parsedRepo,
                    inTransaction = { block -> world.db.withTransaction { block() } }, transactions = world.ftRepo)
                val sender = rows.first().getValue("sender").jsonPrimitive.content
                // A card purchase every bundled bank reads (ArabicAmountRoleFormsTest), so each bank has a linked card.
                val purchase = ProviderSmsRecord("$bankId-card-purchase", sender,
                    "شراء عبر نقاط البيع\nبطاقة مدى: 4321\nبمبلغ: 100 SAR\nلدى: TEST SHOP\nفي: 2026-09-14 12:30",
                    Instant.parse("2026-09-14T09:30:00Z"))
                world.importProviderRows(listOf(purchase) + rows.mapIndexed { index, row -> ProviderSmsRecord(
                    row.getValue("id").jsonPrimitive.content, sender,
                    row.getValue("body").jsonPrimitive.content, Instant.parse("2026-09-15T09:00:00Z").plusSeconds(index * 60L)) })
                // Confirm everything the messages introduced, then let stored evidence post against owned items.
                world.accounts.listAll().forEach { world.accounts.setOwnership(AccountReference(it.bank, it.maskedNumber), OwnershipStatus.OWNED) }
                world.cards.listAll().forEach { world.cards.setOwnership(CardReference(it.bank, it.last4), OwnershipStatus.OWNED) }
                world.loans.listAll().forEach { world.loans.setOwnership(LoanReference(it.bank, it.loanType), OwnershipStatus.OWNED) }
                world.reprocessStoredEvidence()
                assertTrue("$bankId posted nothing", world.ftRepo.listAll().isNotEmpty())

                val targets = world.accounts.listAll().map { RegistryDeleteTarget.Account(AccountReference(it.bank, it.maskedNumber)) } +
                    world.cards.listAll().map { RegistryDeleteTarget.Card(CardReference(it.bank, it.last4)) } +
                    world.loans.listAll().map { RegistryDeleteTarget.Loan(LoanReference(it.bank, it.loanType)) }
                var blocked = 0
                targets.forEach { target ->
                    val preview = workflow.deletionPreview(target)
                    if (preview.linkedCount > 0) {
                        blocked++
                        assertEquals("$bankId $target", DeleteBlocker.LINKED_TRANSACTIONS, preview.blocker)
                        assertTrue("$bankId $target", runCatching { workflow.delete(target) }.isFailure)
                    }
                }
                assertTrue("$bankId has no item linked to its posted transactions", blocked > 0)
                assertEquals("$bankId card 4321", DeleteBlocker.LINKED_TRANSACTIONS,
                    workflow.deletionPreview(RegistryDeleteTarget.Card(CardReference(com.baraa.masroof.domain.model.Bank(bankId), "4321"))).blocker)
                // Linked items survive; everything else (after dependants) can be removed.
                targets.forEach { target -> if (workflow.deletionPreview(target).blocker == null) workflow.delete(target) }
                targets.forEach { target ->
                    val linked = workflow.deletionPreview(target).linkedCount > 0
                    val exists = when (target) {
                        is RegistryDeleteTarget.Account -> world.accounts.get(target.account)
                        is RegistryDeleteTarget.Card -> world.cards.get(target.card)
                        is RegistryDeleteTarget.Loan -> world.loans.get(target.loan)
                    } != null
                    if (linked) assertTrue("$bankId $target", exists)
                }

                val manual = workflow.addAccount(bankId, "9999", null)
                assertNotNull(world.accounts.get(manual))
                assertNull(workflow.deletionPreview(RegistryDeleteTarget.Account(manual)).blocker)
                workflow.delete(RegistryDeleteTarget.Account(manual))
                assertNull("$bankId manual account", world.accounts.get(manual))
            }
        }
    }
}
