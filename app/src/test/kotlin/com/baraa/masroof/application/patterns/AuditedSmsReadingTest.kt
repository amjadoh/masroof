package com.baraa.masroof.application.patterns

import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.engine.bank.*
import com.baraa.masroof.engine.bank.patterns.*
import com.baraa.masroof.finance.value.BankId
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

/** Entirely fictional values; production layouts, never private SMS fixtures. */
class AuditedSmsReadingTest {
    private fun parse(bank: String, sender: String, body: String, defaultAccount: String? = null): Interpretation {
        val bundled = MessagePatternConfigurationCodec.decode(File("src/main/assets/bank-patterns/$bank.patterns.json").readText())
        val config = if (defaultAccount == null) bundled else bundled.copy(banks = bundled.banks.map { definition ->
            definition.copy(patterns = definition.patterns.map { pattern ->
                if (pattern.label != "PURCHASE") pattern else pattern.copy(factContract = pattern.factContract!!.copy(
                    version = 2, currency = com.baraa.masroof.finance.value.Currency.SAR,
                    accountBindings = listOf(PatternAccountBinding(PatternAccountRole.SOURCE, BankId(bank), defaultAccount))))
            })
        })
        return PatternFactInterpreter(config).interpretRouted(
            MessageInput("synthetic", sender, body, Instant.parse("2026-10-20T12:00:00Z"), true), BankId(bank))
    }

    @Test fun explicitCardDoesNotAcquireTheDefaultAccountOrAnotherCardsIdentity() {
        val result = parse("BANK_SNB", "SNB-AlAhli", "شراء-POS\nبـ37 SAR\nمدى-ابل*4321\nفي 2026-10-20 12:30", "9876")
        assertEquals("4321", result.facts.card?.last4)
        assertNull(result.facts.sourceAccount)
        assertNull(result.facts.destinationAccount)
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
    }

    @Test fun debitCardReferencesAreReadFromTheirExplicitPurchaseRoles() {
        for (number in listOf("4321", "8765")) {
            val snb = parse("BANK_SNB", "SNB-AlAhli", "شراء-POS\nبـ37 SAR\nمن TEST SHOP\nمدى-ابل*$number\nفي 26/10/20 12:30")
            assertEquals(snb.diagnostics.toString(), number, snb.facts.card?.last4)
            assertEquals(CardChannel.DEBIT, snb.facts.cardChannel)
            val alinma = parse("BANK_ALINMA", "alinma", "شراء POS-مدى $number*-أثير\nبـ 37 SAR\nمن TEST SHOP\n26-10-20 12:30")
            assertEquals(alinma.diagnostics.toString(), number, alinma.facts.card?.last4)
        }
    }

    @Test fun financingTitleIsRecognizedWithoutInventingCurrencyOrAccountOwnership() {
        val result = parse("BANK_EMIRATES_NBD", "EmiratesNBD", "خصم قسط تمويل\nPersonal Finance : نوع التمويل\n1234.50 : القسط المستحق\n45678.90 : اجمالي المبلغ المتبقي\nXX4321 : رقم الحساب\n20-10-2026 : التاريخ\n12:30:00 : الوقت")
        assertEquals(result.diagnostics.toString(), MessageFamily.FINANCING_INSTALLMENT, result.facts.family)
        assertNotEquals(ParseStatus.SUCCESS, result.status)
    }

    @Test fun cardTokensCannotComeFromMerchantTextOrPartialNumbersAndConflictsStayInReview() {
        for (line in listOf("من مدى-ابل*4321", "مدى-ابل*43210")) {
            val result = parse("BANK_SNB", "SNB-AlAhli", "شراء-POS\nبـ37 SAR\n$line")
            assertNull(result.facts.card)
            assertNotEquals(ParseStatus.SUCCESS, result.status)
        }
        val conflict = parse("BANK_SNB", "SNB-AlAhli", "شراء-POS\nبـ37 SAR\nمدى-ابل*4321\nبطاقة:8765")
        assertNull(conflict.facts.card)
        assertNotEquals(ParseStatus.SUCCESS, conflict.status)
    }

    @Test fun declinedPurchaseNeverBecomesAnExecutedPurchase() {
        val result = parse("BANK_SAB", "SAB", "رصيد غير كافِ\nرصيدك غير كافِ على بطاقة Visa Noon (***4321) لإتمام POS Purchase بمبلغ 37 SAR\nفي 2026-10-20 12:30:00")
        assertNotEquals(ParseStatus.SUCCESS, result.status)
    }
}
