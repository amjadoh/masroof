package com.baraa.masroof.application.transaction

import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.engine.bank.patterns.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ConfirmedLoanPaymentConfigurationTest {
    @Test fun explicitTreatmentSurvivesCodecAndBankEditsWithoutChangingLegacyFingerprints() {
        val bank = BankMessagePatterns("TEST_BANK", listOf("TEST SENDER"))
        val legacy = MessagePatternConfiguration(banks = listOf(bank))
        val rule = ConfirmedLoanPayment("009", "OTHER_BANK", "PERSONAL", setOf("tx-confirmed"))
        val configured = legacy.copy(confirmedLoanPayments = listOf(rule))
        assertEquals(configured, MessagePatternConfigurationCodec.decode(MessagePatternConfigurationCodec.encode(configured)))
        assertEquals("009", configured.confirmedLoanPayments.single().billerCode)
        assertNotEquals(PatternFactInterpreter(legacy).fingerprint, PatternFactInterpreter(configured).fingerprint)
        assertEquals(PatternFactInterpreter(legacy).fingerprint, PatternFactInterpreter(legacy.copy(confirmedLoanPayments = emptyList())).fingerprint)
        val directory = Files.createTempDirectory("loan-configuration").toFile()
        try {
            val store = MessagePatternStore(directory.resolve("definitions.json"))
            store.restoreRaw(MessagePatternConfigurationCodec.encode(configured))
            val next = store.saveBank(configured.generation, bank.copy(displayName = "Renamed bank"))
            assertEquals(listOf(rule), next.confirmedLoanPayments)
            assertThrows(IllegalArgumentException::class.java) {
                MessagePatternConfiguration(confirmedLoanPayments = listOf(rule, rule.copy(loanBankId = "THIRD_BANK")))
            }
            assertThrows(IllegalArgumentException::class.java) { ConfirmedLoanPayment("not-a-code", "OTHER_BANK", "PERSONAL") }
        } finally { directory.deleteRecursively() }
    }
}
