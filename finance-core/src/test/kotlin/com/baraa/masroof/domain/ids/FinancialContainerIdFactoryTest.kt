package com.baraa.masroof.domain.ids

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class FinancialContainerIdFactoryTest {

    @Test
    fun d360_6810_isDurableDeterministicAccountId() {
        val id = FinancialContainerIdFactory.accountId(Bank("D360"), "6810")
        assertEquals("account:D360:6810", id)
        assertEquals(id, FinancialContainerIdFactory.accountId(AccountReference(Bank("D360"), "6810")))
    }

    @Test
    fun bankUnknown_6810_doesNotGenerateDurableAccountId() {
        assertNull(FinancialContainerIdFactory.accountId(AccountReference(Bank.UNKNOWN, "6810")))
        try {
            FinancialContainerIdFactory.accountId(Bank.UNKNOWN, "6810")
            fail<Unit>("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        assertNotEquals(
            FinancialContainerIdFactory.accountId(Bank("D360"), "6810"),
            "account:UNKNOWN:6810",
        )
    }

    @Test
    fun bankUnknown_card_doesNotGenerateDurableCardId() {
        assertNull(FinancialContainerIdFactory.cardId(CardReference(Bank.UNKNOWN, "7271")))
        try {
            FinancialContainerIdFactory.cardId(Bank.UNKNOWN, "7271")
            fail<Unit>("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
