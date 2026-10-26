package com.baraa.masroof.bank.patterns

import com.baraa.masroof.bank.InterpretationDraftMapper
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.bank.patterns.*
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.parsing.model.ParseResult
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PatternFactGateTest {
    private val body = "Purchase\nAmount:10SAR\nMerchant:Shop\nAt:2026-10-15 12:30"
    private val input = MessageInput("pattern-gate", "BANK", body, Instant.parse("2026-10-15T10:00:00Z"), true)
    private val bank = BankId("BANK_STC")
    private val pattern = SavedMessagePattern.fromChoices("purchase", bank.value, input.sender, "TRANSFER_IN", body,
        listOf("amount" to "10SAR", "merchant" to "Shop", "occurred_at" to "2026-10-15 12:30").map { (field, value) ->
            FieldChoice(field, body.indexOf(value), body.indexOf(value) + value.length)
        }).copy(factContract = PatternFactContract(MessageFamily.PURCHASE, MoneyDirection.OUTGOING))
    private fun interpret(p: SavedMessagePattern = pattern) = PatternFactInterpreter(MessagePatternConfiguration(banks = listOf(
        BankMessagePatterns(bank.value, listOf(input.sender), patterns = listOf(p))))).interpretRouted(input, bank)

    @Test fun validatedFactsCarryTheExactPatternSnapshotAndSourcesThroughTheMapper() {
        val facts = interpret()
        val result = InterpretationDraftMapper().map(input, facts, Bank(bank.value)) as ParseResult.Success
        val reasons = result.event.confidence.reasons
        assertTrue("pattern_config:${facts.patternProvenance!!.configurationFingerprint}" in reasons)
        assertTrue("pattern_revision:purchase:1" in reasons)
        assertTrue("pattern_source:FAMILY:definition" in reasons)
        assertTrue(reasons.any { it.startsWith("pattern_source:AMOUNT:message:") })
        assertFalse(reasons.any { it.contains("10SAR") || it.contains("Shop") })
        assertEquals("10.00", result.event.amount!!.amount.toPlainString())
    }

    @Test fun contractsCannotBypassAmountDirectionOrTimeValidation() {
        val mapper = InterpretationDraftMapper()
        val facts = interpret()
        listOf(facts.copy(evidence = facts.evidence.filterNot { it.field == "AMOUNT" }),
            facts.copy(facts = facts.facts.copy(direction = MoneyDirection.INCOMING)),
            facts.copy(facts = facts.facts.copy(occurredAtLocal = java.time.LocalDateTime.parse("2099-01-01T00:00"))))
            .forEach { assertTrue(mapper.map(input, it, Bank(bank.value)) is ParseResult.ReviewRequired) }
        assertTrue(mapper.map(input, interpret(pattern.copy(factContract = null)), Bank(bank.value)) is ParseResult.ReviewRequired)
    }
}
