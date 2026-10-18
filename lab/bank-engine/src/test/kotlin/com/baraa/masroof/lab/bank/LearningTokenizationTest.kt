package com.baraa.masroof.lab.bank

import com.baraa.masroof.lab.bank.interpretation.LearningTokenization
import org.junit.Assert.*
import org.junit.Test

class LearningTokenizationTest {
    @Test fun normalizedTokensKeepOriginalArabicAndUtf16Ranges() {
        val body = "😀 أَلْف\nالمبلغ: ٤٢٫٥٠ SAR"
        val tokens = LearningTokenization.tokens(body)
        val index = tokens.indexOfFirst { it.text == "42.50" }
        assertTrue(index >= 0)
        val token = tokens[index]
        assertEquals("٤٢٫٥٠", body.substring(token.start, token.end))
        assertEquals(index..index, LearningTokenization.range(body, token.start, token.end))
        assertEquals("الف", tokens.first().text)
    }

    @Test fun selectionCanCoverMultipleNormalizedTokens() {
        val body = "Amount: 42 SAR"
        assertEquals(1..2, LearningTokenization.range(body, body.indexOf("42"), body.length))
        assertTrue(LearningTokenization.tokens("  ").isEmpty())
    }
}
