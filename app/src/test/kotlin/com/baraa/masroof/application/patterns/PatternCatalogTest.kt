package com.baraa.masroof.application.patterns

import com.baraa.masroof.engine.bank.MessageFamily
import com.baraa.masroof.engine.bank.MoneyDirection
import com.baraa.masroof.engine.bank.patterns.*
import org.junit.Assert.*
import org.junit.Test

class PatternCatalogTest {
    @Test fun displayChoicesKeepRevisionsAndApprovalSeparateFromMatching() {
        val body = "Transfer:10SAR From:123 To:456"
        val pattern = SavedMessagePattern.fromChoices("transfer", "BANK_TEST", "TEST", "TRANSFER_OUT", body,
            listOf(FieldChoice("amount", 9, 14), FieldChoice("account_reference", 20, 23),
                FieldChoice("destination_account", 27, 30)))
        val approved = pattern.copy(revision = 2,
            factContract = PatternFactContract(MessageFamily.TRANSFER_OUT, MoneyDirection.OUTGOING))
        fun view(patterns: List<SavedMessagePattern>) = MessagePatternConfiguration(7,
            listOf(BankMessagePatterns("BANK_TEST", listOf("TEST"), patterns = patterns)))
            .toCatalog().banks.single().currentPatterns.single()

        val preview = view(listOf(pattern))
        assertEquals(PatternApprovalStatus.PREVIEW_ONLY, preview.status)
        assertFalse(preview.canApply)
        assertEquals("Transfer:… From:… To:…", preview.example)
        assertEquals(listOf("account_reference"), preview.accountReferenceFields(PatternOperation.TRANSFER_OUT))
        assertEquals(listOf("account_reference", "destination_account"), preview.accountReferenceFields(PatternOperation.TRANSFER_IN))
        val current = view(listOf(pattern, approved))
        assertEquals(listOf(1), current.priorRevisions)
        assertTrue(current.canApprove)
        assertTrue(current.canApply)
        assertFalse(view(listOf(approved.copy(revision = 3, enabled = false))).canApply)
    }
}
