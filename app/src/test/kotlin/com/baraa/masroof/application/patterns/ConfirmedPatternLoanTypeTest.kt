package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.engine.bank.*
import com.baraa.masroof.engine.bank.patterns.*
import com.baraa.masroof.finance.value.BankId
import com.baraa.masroof.finance.value.Currency
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ConfirmedPatternLoanTypeTest {
    private val bank = BankId("BANK_STC")
    private fun contract(type: LoanType? = LoanType.PERSONAL) = PatternFactContract(
        MessageFamily.FINANCING_INSTALLMENT, MoneyDirection.OUTGOING, Currency.SAR,
        version = if (type == null) 2 else 3,
        accountBindings = listOf(PatternAccountBinding(PatternAccountRole.SOURCE, bank, "4321")),
        confirmedLoanType = type,
    )
    private fun configuration(contract: PatternFactContract) = MessagePatternConfiguration(banks = listOf(
        BankMessagePatterns(bank.value, listOf("STC Bank"), patterns = listOf(SavedMessagePattern(
            "confirmed-loan", bank.value, "STC Bank", "FINANCING_INSTALLMENT",
            parts = listOf(StaticText("مرحبا "), FieldSlot("customer", FieldKind.TEXT, 0, true),
                StaticText("\nتم سداد دفعة التمويل بمبلغ "), FieldSlot("amount", FieldKind.DECIMAL, 0, false),
                StaticText(" رس بنجاح!")), factContract = contract,
        ))),
    ))
    private fun parse(config: MessagePatternConfiguration, body: String = "مرحبا عميل تجريبي\nتم سداد دفعة التمويل بمبلغ 321.50 رس بنجاح!") =
        PatternFactInterpreter(config).interpretRouted(MessageInput("fictional", "STC Bank", body,
            Instant.parse("2026-10-20T12:00:00Z"), true), bank)

    @Test fun confirmedLoanTypeRoundTripsAndIsDistinctFromAnUnconfirmedMessage() {
        val config = configuration(contract())
        val restored = MessagePatternConfigurationCodec.decode(MessagePatternConfigurationCodec.encode(config))
        val result = parse(restored)
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals(LoanType.PERSONAL, result.facts.loanType)
        assertEquals("4321", result.facts.sourceAccount?.maskedNumber)
        assertTrue("LOAN_TYPE" in requireNotNull(result.patternProvenance).definitionFacts)
        assertNull(parse(configuration(contract(null))).facts.loanType)
        assertNotEquals(PatternFactInterpreter(config).fingerprint, PatternFactInterpreter(configuration(contract(LoanType.AUTO))).fingerprint)
    }

    @Test fun remindersAndOffersDoNotBecomeExecutedRepayments() {
        val config = configuration(contract())
        for (body in listOf("مرحبا عميل تجريبي\nراح يتم خصم قسط التمويل في يوم 25",
            "مرحبا عميل تجريبي\nتم إرسال العرض النهائي للتمويل")) {
            assertNotEquals(ParseStatus.SUCCESS, parse(config, body).status)
        }
    }

    @Test fun loanConfirmationCannotBeSmuggledIntoOtherFamiliesOrOlderContracts() {
        assertThrows(IllegalArgumentException::class.java) { contract().copy(version = 2) }
        assertThrows(IllegalArgumentException::class.java) { contract().copy(family = MessageFamily.PURCHASE) }
        assertThrows(IllegalArgumentException::class.java) { contract().copy(confirmedLoanType = null) }
    }
}
