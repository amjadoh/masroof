package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.engine.bank.*
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter
import com.baraa.masroof.finance.value.BankId
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Fictional statements: a statement updates explicit due facts, never spending. */
class CardStatementReadingTest {
    @Test fun snbStatementPreservesExplicitDueWithoutTurningStatementTotalIntoSpending() {
        for (total in listOf("123.45", "-123.45")) {
            val result = parse("BANK_SNB", "SNB-AlAhli", "تم اصدار الكشف الإلكتروني لبطاقتك الائتمانية **8765\nمبلغ كشف الحساب $total ريال\nالمبلغ المستحق 12.34 ريال\nتاريخ الاستحقاق 20/11/2026")
            verify(result)
        }
    }

    @Test fun sabStatementDueTimestampIsNotATransactionDateOrCashAccount() {
        val result = parse("BANK_SAB", "SAB", "عزيزي العميل،\nتم إصدار كشف حساب بطاقة Visa Noon الائتمانية (8765)\nرقم الحساب: 1234567890\nالمبلغ المستحق: SAR 12.34\nتاريخ الاستحقاق: 2026-11-20 00:00:00")
        verify(result)
        assertNull(result.facts.sourceAccount)
        assertNull(result.facts.destinationAccount)
        assertNull(result.facts.occurredAtLocal)
    }

    @Test fun courtesyHeaderCannotHideMovementOrTurnOtherTextIntoAStatement() {
        for (body in listOf("عزيزي العميل،\nشراء\nمبلغ: 43.25 SAR\nبطاقة:8765",
            "عزيزي العميل،\nتم إصدار كشف حساب بطاقة Visa Noon الائتمانية (8765)\nالمبلغ المستحق: SAR 12.34\nشراء\nمبلغ: 43.25 SAR")) {
            assertEquals(ParseStatus.REVIEW_REQUIRED, parse("BANK_SAB", "SAB", body).status)
        }
        val invalid = parse("BANK_SAB", "SAB", "عزيزي العميل،\nتم إصدار كشف حساب بطاقة Visa Noon الائتمانية (8765)\nالمبلغ المستحق: SAR 12.34\nتاريخ الاستحقاق: 2026-02-30 00:00:00")
        assertEquals(ParseStatus.REVIEW_REQUIRED, invalid.status)
        assertNull(invalid.facts.paymentDueDate)
    }

    private fun verify(result: Interpretation) {
        assertEquals(result.diagnostics.toString(), ParseStatus.NON_FINANCIAL, result.status)
        assertEquals(MessageFamily.BALANCE_NOTICE, result.facts.family)
        assertEquals(CardChannel.STATEMENT, result.facts.cardChannel)
        assertEquals("8765", result.facts.card?.last4)
        assertEquals("12.34", result.facts.outstandingBalance?.amount?.toPlainString())
        assertEquals(LocalDate.of(2026, 11, 20), result.facts.paymentDueDate)
        assertNull(result.facts.amount)
    }

    private fun parse(bank: String, sender: String, body: String): Interpretation {
        val definitions = MessagePatternConfigurationCodec.decode(File("src/main/assets/bank-patterns/$bank.patterns.json").readText())
        return PatternFactInterpreter(definitions).interpretRouted(MessageInput("fictional-statement", sender, body,
            Instant.parse("2026-10-20T12:00:00Z"), true), BankId(bank))
    }
}
