package com.baraa.masroof.lab.bank

import com.baraa.masroof.lab.bank.interpretation.SenderRouter
import com.baraa.masroof.lab.finance.value.BankId
import org.junit.Assert.*
import org.junit.Test

class SenderRouterTest {
    @Test fun normalizedLiteralMatchesPreserveAllCandidatesAndSnapshot() {
        val names = mutableSetOf("Test-Bank")
        val router = SenderRouter(listOf(BankDefinition(BankId("A"), names), BankDefinition(BankId("B"), setOf("TEST BANK"))))
        names.clear()
        assertEquals(setOf(BankId("A"), BankId("B")), router.catalog(" test bank ").candidates(" test bank "))
        assertTrue(router.catalog("TestBankExtra").candidates("TestBankExtra").isEmpty())
        assertTrue(router.suspected("TestBankExtra", ""))
    }
    @Test fun bodySuspicionAndLiteralRegexCharactersNeverAuthorizeIdentity() {
        val router = SenderRouter(listOf(BankDefinition(BankId("A"), setOf("بنك أ", "Example"))), listOf("bank example"))
        assertEquals(setOf(BankId("A")), router.catalog(" بنك ا ").candidates(" بنك ا "))
        assertTrue(router.catalog("Other").candidates("Other").isEmpty())
        assertTrue(router.suspected("Other", "bank example purchase"))
        assertFalse(router.suspected("Other", "ordinary text"))
    }
}
