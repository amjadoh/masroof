package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.ui.components.MasroofAmountPrivacy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal

class AmountPrivacyFormattingTest {
    @After
    fun reset() {
        MasroofAmountPrivacy.hidden = false
    }

    @Test
    fun hidden_masksDigitsButKeepsCurrency() {
        MasroofAmountPrivacy.hidden = true

        val text = MoneyUiFormatter.format(Money(BigDecimal("1234.50"), Currency.SAR), "ar")

        assertEquals("${MasroofAmountPrivacy.MASK} ر.س", text)
        assertFalse(text.any { it.isDigit() })
    }

    @Test
    fun shown_formatsNormally() {
        assertEquals("1,234.50 SAR", MoneyUiFormatter.format(Money(BigDecimal("1234.50"), Currency.SAR), "en"))
    }
}
