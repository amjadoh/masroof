package com.baraa.masroof.application.settings

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.CardConfiguration
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.testsupport.DashboardLedgerWorld
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Accounts, cards and loans are deletable only when nothing still references them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsRegistryDeleteTest {
    private val world = DashboardLedgerWorld(ApplicationProvider.getApplicationContext())
    private val bank = com.baraa.masroof.domain.model.Bank.BANK_ALJAZIRA
    private var boundAccounts = emptySet<AccountReference>()
    private val workflow = SettingsRegistryWorkflow(world.cards, world.accounts, world.loans,
        OwnershipConfirmationService(world.accounts, world.cards, world.loans), world.parsedRepo,
        inTransaction = { block -> world.db.withTransaction { block() } }, transactions = world.ftRepo,
        patternBoundAccounts = { boundAccounts })

    @After fun close() = world.close()

    @Test fun instrumentsWithPostedTransactionsAreBlockedAndKept() = runBlocking {
        world.importFixtureCorpus()
        val targets = listOf(RegistryDeleteTarget.Account(AccountReference(bank, "3001")),
            RegistryDeleteTarget.Card(CardReference(bank, "7271"))) +
            world.loans.listAll().map { RegistryDeleteTarget.Loan(com.baraa.masroof.domain.model.LoanReference(it.bank, it.loanType)) }
        targets.forEach { target ->
            val preview = workflow.deletionPreview(target)
            assertTrue("$target", preview.linkedCount > 0)
            assertEquals("$target", DeleteBlocker.LINKED_TRANSACTIONS, preview.blocker)
            assertEquals("$target", minOf(DeletionPreview.SHOWN, preview.linkedCount), preview.linked.size)
            assertEquals("$target", preview.linked.sortedByDescending { it.occurredAt }, preview.linked)
            assertThrows(IllegalStateException::class.java) { runBlocking { workflow.delete(target) } }
        }
        assertTrue("the corpus includes a loan repayment", targets.any { it is RegistryDeleteTarget.Loan })
        assertNotNull(world.accounts.get(AccountReference(bank, "3001")))
        assertNotNull(world.cards.get(CardReference(bank, "7271")))
    }

    @Test fun anUnusedAccountIsDeletedUnlessACardOrPatternStillUsesIt() = runBlocking {
        val account = workflow.addAccount(bank.id, "9999", "Spare")
        val target = RegistryDeleteTarget.Account(account)
        workflow.addCard(bank.id, "4444", null, CardConfiguration(CardNetwork.MADA, CardType.DEBIT, linkedAccount = account))
        assertEquals(DeleteBlocker.LINKED_CARD, workflow.deletionPreview(target).blocker)
        workflow.delete(RegistryDeleteTarget.Card(CardReference(bank, "4444")))

        boundAccounts = setOf(account)
        assertEquals(DeleteBlocker.MESSAGE_PATTERN, workflow.deletionPreview(target).blocker)
        boundAccounts = emptySet()

        assertNull(workflow.deletionPreview(target).blocker)
        workflow.delete(target)
        assertNull(world.accounts.get(account))
    }

    @Test fun aPrimaryCardWaitsForItsSupplementariesAndTakesItsFacilityWithIt() = runBlocking {
        workflow.addCard(bank.id, "1111", null, CardConfiguration(CardNetwork.VISA, CardType.CREDIT, CardRole.PRIMARY))
        workflow.addCard(bank.id, "2222", null, CardConfiguration(CardNetwork.VISA, CardType.CREDIT, CardRole.SUPPLEMENTARY, "1111"))
        val primary = RegistryDeleteTarget.Card(CardReference(bank, "1111"))
        assertEquals(DeleteBlocker.SUPPLEMENTARY_CARDS, workflow.deletionPreview(primary).blocker)
        assertThrows(IllegalStateException::class.java) { runBlocking { workflow.delete(primary) } }

        workflow.delete(RegistryDeleteTarget.Card(CardReference(bank, "2222")))
        workflow.delete(primary)
        assertNull(world.cards.get(CardReference(bank, "1111")))
        world.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM credit_facility").use {
            it.moveToFirst(); assertEquals(0, it.getInt(0))
        }
    }
}
