package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.engine.bank.*
import com.baraa.masroof.engine.bank.patterns.*
import com.baraa.masroof.finance.value.BankId
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

/** SNB ATM withdrawal in the current production layout (`بـ <ATM place>`); the place, amount and card are fictional. */
class SnbAtmWithdrawalReadingTest {
    private val body = "سحب صراف آلي\nمبلغ 43.25 SAR\nبطاقة مدى *8765\nبـ TEST ATM BUILD MAKKAH SA\nفي 08/11/26 18:39"

    @Test fun atmWithdrawalWithOnlyAMadaCardReadsWithoutReview() {
        val result = parse(body)
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals(MessageFamily.WITHDRAWAL, result.facts.family)
        assertEquals("8765", result.facts.card?.last4)
        assertEquals("43.25", result.facts.amount?.amount?.toPlainString())
    }

    @Test fun placeLineStillLeavesAMissingAmountForReview() {
        val result = parse("سحب صراف آلي\nبطاقة مدى *8765\nبـ TEST ATM BUILD MAKKAH SA\nفي 08/11/26 18:39")
        assertEquals(ParseStatus.REVIEW_REQUIRED, result.status)
    }

    private fun parse(text: String): Interpretation {
        val bundled = MessagePatternConfigurationCodec.decode(File("src/main/assets/bank-patterns/BANK_SNB.patterns.json").readText())
        val persisted = MessagePatternConfigurationCodec.decode(MessagePatternConfigurationCodec.encode(bundled))
        return PatternFactInterpreter(persisted).interpretRouted(
            MessageInput("synthetic", "SNB-AlAhli", text, Instant.parse("2026-11-08T15:39:00Z"), true), BankId("BANK_SNB"))
    }
}
