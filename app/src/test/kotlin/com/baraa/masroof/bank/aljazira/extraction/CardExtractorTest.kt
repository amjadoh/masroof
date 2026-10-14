package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardExtractorTest {
    @Test fun standaloneCreditCardLineWithoutColonExtractsSuffix() {
        assertEquals("1234", CardExtractor.extractFromText("POS Purchase Contactless\nCredit card 1234 \nof :42.00 SAR", Bank.BANK_ALJAZIRA)?.last4)
    }
    @Test fun colonFormStillWorks() {
        assertEquals("1234", CardExtractor.extractFromText("Credit card: 1234", Bank.BANK_ALJAZIRA)?.last4)
    }
    @Test fun noColonFormNeverTruncatesLongOrMaskedNumbersOrConsumesOtherFacts() {
        listOf("Credit card 12345", "Credit card 1234 5678", "Credit card ****1234", "Credit card 1234.56 SAR",
            "Credit card\n1234", "Credit card balance 1234", "Credit card 1234 extra").forEach {
            assertNull(it, CardExtractor.extractFromText(it, Bank.BANK_ALJAZIRA))
        }
    }
}
