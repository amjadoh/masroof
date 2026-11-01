package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.NoOpLoanRegistryRepository
import com.baraa.masroof.testsupport.NoOpCardRegistryRepository
import com.baraa.masroof.testsupport.StubAccountRegistryRepository
import com.baraa.masroof.finance.value.Currency
import com.baraa.masroof.engine.bank.patterns.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BankMessageAccountSetupTest {
    @Test fun explicitMaskedReferenceApprovalSurvivesReloadAndCannotRemapAVisibleSuffix() = runBlocking {
        val directory = Files.createTempDirectory("confirmed-masked-reference-").toFile()
        try {
            val bank = Bank("BANK_BARQ")
            val owned = AccountRegistryEntry.forTest(bank, "8765")
            val accounts = StubAccountRegistryRepository(owned)
            val cards = NoOpCardRegistryRepository()
            val store = MessagePatternStore(directory.resolve("patterns.json"))
            val body = "Bill\nAmount:42SAR\nFrom:900*567\nAt:2026-10-13 12:30"
            val pattern = SavedMessagePattern.fromChoices("masked-reference", bank.id, "barq app", "BILL_PAYMENT", body,
                listOf("amount" to "42SAR", "source_account" to "900*567", "occurred_at" to "2026-10-13 12:30").map { (field, value) ->
                    FieldChoice(field, body.indexOf(value), body.indexOf(value) + value.length)
                })
            store.saveBank(0, BankMessagePatterns(bank.id, listOf("barq app"), patterns = listOf(pattern)))
            val workflow = PatternApprovalWorkflow(store, DatabaseAccessGate(), accounts,
                OwnershipConfirmationService(accounts, cards, NoOpLoanRegistryRepository))
            val preview = workflow.preview(store.load().generation, bank.id, pattern.id, PatternOperation.BILL_PAYMENT,
                owned.id, body, "source_account", emptySet(), Currency.SAR)
            workflow.save(preview)
            val reloaded = MessagePatternStore(directory.resolve("patterns.json")).load()
            assertEquals("900*567", reloaded.banks.single().currentPatterns.single().factContract!!.accountBindings.single().observedValue)
            val mismatch = runCatching { workflow.preview(reloaded.generation, bank.id, pattern.id, PatternOperation.BILL_PAYMENT,
                owned.id, body.replace("900*567", "900*4321"), "source_account", emptySet(), Currency.SAR) }.exceptionOrNull()
            assertEquals(PatternApprovalProblem.REFERENCE_MISMATCH, (mismatch as? PatternApprovalException)?.problem)
            assertEquals(reloaded, store.load())
        } finally { directory.deleteRecursively() }
    }

    @Test fun explicitOwnedSelectionSurvivesRestartAndRepeatedSaveDoesNotCreateNewRevisions() = runBlocking {
        val directory = Files.createTempDirectory("bank-account-default-").toFile()
        try {
            val bank = Bank("BANK_BARQ")
            val owned = AccountRegistryEntry.forTest(bank, "1234")
            val external = AccountRegistryEntry.forTest(bank, "5678", ownership = OwnershipStatus.EXTERNAL)
            val accounts = StubAccountRegistryRepository(owned, external)
            val cards = NoOpCardRegistryRepository()
            val file = directory.resolve("patterns.json")
            val store = MessagePatternStore(file)
            val seed = MessagePatternConfigurationCodec.decode(File("src/main/assets/bank-patterns/BANK_BARQ.patterns.json").readText()).banks.single()
            store.installMissingBanks(listOf(seed))
            val workflow = PatternApprovalWorkflow(store, DatabaseAccessGate(), accounts,
                OwnershipConfirmationService(accounts, cards, NoOpLoanRegistryRepository))
            val prior = store.load()
            assertEquals(listOf(owned.id), workflow.bankAccountSetup(bank.id).accounts.map { it.id })
            assertTrue(runCatching { workflow.configureBankAccount(prior.generation, bank.id, external.id, Currency.SAR, false) }.isFailure)
            assertEquals(prior, store.load())
            workflow.configureBankAccount(prior.generation, bank.id, owned.id, Currency.SAR, false)
            val saved = store.load()
            assertEquals(saved, MessagePatternStore(file).load())
            assertEquals(owned.id, workflow.bankAccountSetup(bank.id).selectedAccountId)
            assertTrue(runCatching { workflow.configureBankAccount(prior.generation, bank.id, owned.id, Currency.SAR, false) }.isFailure)
            workflow.configureBankAccount(saved.generation, bank.id, owned.id, Currency.SAR, false)
            assertEquals(saved, store.load())
            assertTrue(saved.banks.single().currentPatterns.filter { it.label in setOf("OTP", "NON_FINANCIAL", "CARD_PAYMENT") }
                .all { it.factContract?.accountBindings.orEmpty().isEmpty() })
        } finally { directory.deleteRecursively() }
    }
}
