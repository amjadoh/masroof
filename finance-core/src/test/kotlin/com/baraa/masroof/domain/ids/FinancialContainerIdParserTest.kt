package com.baraa.masroof.domain.ids

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FinancialContainerIdParserTest {
    @Test
    fun kindChecks_followThePrefixAndRejectNull() {
        assertEquals(true, FinancialContainerIdParser.isCardContainer("card:BANK_ALJAZIRA:7271"))
        assertEquals(false, FinancialContainerIdParser.isAccountContainer("card:BANK_ALJAZIRA:7271"))
        assertEquals(true, FinancialContainerIdParser.isAccountContainer("account:BANK_SNB:3001"))
        assertEquals(false, FinancialContainerIdParser.isCardContainer(null))
    }

    @Test
    fun cardLast4_parsesCardContainerId() {
        assertEquals("7271", FinancialContainerIdParser.cardLast4("card:BANK_ALJAZIRA:7271"))
    }

    @Test
    fun cardLast4_ignoresAccountContainerId() {
        assertNull(FinancialContainerIdParser.cardLast4("account:BANK_ALJAZIRA:3001"))
    }

    @Test
    fun cardLast4FromContainers_prefersSourceThenDestination() {
        assertEquals(
            "7271",
            FinancialContainerIdParser.cardLast4FromContainers(
                sourceContainerId = "card:BANK_ALJAZIRA:7271",
                destinationContainerId = null,
            ),
        )
        assertEquals(
            "8332",
            FinancialContainerIdParser.cardLast4FromContainers(
                sourceContainerId = null,
                destinationContainerId = "card:BANK_ALJAZIRA:8332",
            ),
        )
    }
}
