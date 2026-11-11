package com.baraa.masroof.core.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CurrencyTest {

    @Test
    fun convertsToSar_returnsFalseForSar() {
        assertFalse(Currency.SAR.convertsToSar())
    }

    @Test
    fun convertsToSar_returnsTrueForUsd() {
        assertTrue(Currency.USD.convertsToSar())
    }

    @Test
    fun convertsToSar_returnsTrueForEur() {
        assertTrue(Currency.EUR.convertsToSar())
    }

    @Test
    fun convertsToSar_returnsTrueForGbp() {
        assertTrue(Currency.GBP.convertsToSar())
    }

    @Test
    fun foreignForSarConversion_containsExactlyUsdEurAndGbp() {
        assertEquals(
            setOf(Currency.USD, Currency.EUR, Currency.GBP),
            Currency.FOREIGN_FOR_SAR_CONVERSION,
        )
    }
}
