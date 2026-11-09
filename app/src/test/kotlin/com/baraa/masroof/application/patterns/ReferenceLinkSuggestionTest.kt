package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.data.room.DatabaseAccessGate
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.NoOpLoanRegistryRepository
import com.baraa.masroof.engine.bank.MessageInput
import com.baraa.masroof.engine.bank.ParseStatus
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter
import com.baraa.masroof.finance.value.BankId
import com.baraa.masroof.testsupport.NoOpCardRegistryRepository
import com.baraa.masroof.testsupport.StubAccountRegistryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

/** Fictional SAB card payments that name the paying account only by its last three digits. */
class ReferenceLinkSuggestionTest {
    private val bank = Bank("BANK_SAB")
    private val payment = "بطاقة إئتمانية: تسديد\nبطاقة: ***6543;VISA\nمبلغ: 123.45 SAR\nمن: ***321\nفي: 2026-10-20 12:30:00"

    @Test fun aReferenceOneOwnedAccountEndsWithIsSuggestedAndLinkingPostsFromIt() = withWorkflow("4321") { workflow, store ->
        val suggestion = workflow.referenceLinkSuggestions(bank.id).single()
        assertEquals("321", suggestion.reference)
        assertEquals("4321", suggestion.account.last4)
        assertEquals("CARD_PAYMENT", suggestion.family)
        assertEquals(2, suggestion.messageCount)

        workflow.linkReference(bank.id, suggestion)
        val reading = PatternFactInterpreter(store.load()).interpretRouted(
            MessageInput("x", "SAB", payment, Instant.parse("2026-10-20T12:00:00Z"), true), BankId(bank.id))
        assertEquals(reading.diagnostics.toString(), ParseStatus.SUCCESS, reading.status)
        assertEquals("4321", reading.facts.sourceAccount?.maskedNumber)
        assertTrue(workflow.referenceLinkSuggestions(bank.id).isEmpty())
    }

    @Test fun twoOwnedAccountsWithTheSameEndingAreNeverGuessed() = withWorkflow("4321", "7321") { workflow, _ ->
        assertTrue(workflow.referenceLinkSuggestions(bank.id).isEmpty())
    }

    @Test fun aReferenceNoOwnedAccountEndsWithIsNotSuggested() = withWorkflow("4322") { workflow, _ ->
        assertTrue(workflow.referenceLinkSuggestions(bank.id).isEmpty())
    }

    private fun withWorkflow(vararg owned: String, block: suspend (PatternApprovalWorkflow, MessagePatternStore) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("reference-link-").toFile()
        try {
            val accounts = StubAccountRegistryRepository(*owned.map { AccountRegistryEntry.forTest(bank, it) }.toTypedArray())
            val store = MessagePatternStore(directory.resolve("patterns.json"))
            store.installMissingBanks(listOf(MessagePatternConfigurationCodec.decode(
                File("src/main/assets/bank-patterns/BANK_SAB.patterns.json").readText()).banks.single()))
            val messages = listOf("SAB" to payment, "SAB" to payment.replace("123.45", "50.00"))
            val workflow = PatternApprovalWorkflow(store, DatabaseAccessGate(), accounts,
                OwnershipConfirmationService(accounts, NoOpCardRegistryRepository(), NoOpLoanRegistryRepository),
                unpostedMessages = { messages })
            block(workflow, store)
        } finally {
            directory.deleteRecursively()
        }
    }
}
