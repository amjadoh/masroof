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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BankMessageAccountSetupTest {
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
