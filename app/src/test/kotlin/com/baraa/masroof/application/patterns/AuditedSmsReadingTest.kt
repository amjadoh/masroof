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
    @Test fun incomingDefaultIsExplicitAndNeverOverridesTheObservedDestination() {
        val body = "حوالة واردة داخلية ب 43.25 SAR\nمن 8765* عميل تجريبي\n2026-10-20 12:30"
        val linked = parse("BANK_SNB", "SNB-AlAhli", body, "4321", family = "TRANSFER_IN", destinationDefault = true)
        assertEquals("4321", linked.facts.destinationAccount?.maskedNumber)
        assertEquals("8765", linked.facts.sourceAccount?.maskedNumber)
        val observed = parse("BANK_SNB", "SNB-AlAhli", body + "\nإلى 7654*", "4321", family = "TRANSFER_IN", destinationDefault = true)
        assertEquals("7654", observed.facts.destinationAccount?.maskedNumber)
    }

    @Test fun barqPartyNameDoesNotConsumeTheSeparateAccountRole() {
        val incoming = parse("BANK_BARQ", "barq", "Credit transfer Local\nAmount: 43.25 SAR\nFrom: Test Sender\nFrom a/c: **8765\nBank: NCBKSARI\n2026-10-20 12:30")
        assertEquals("Test Sender", incoming.facts.counterparty)
        assertEquals("8765", incoming.facts.sourceAccount?.maskedNumber)
        assertFalse(incoming.diagnostics.any { it.code == "CONFLICTING_counterparty" })
        val outgoing = parse("BANK_BARQ", "barq", "Debit Transfer Internal\nAmount: 43.25 SAR\nTo: Test Receiver\nTo A/C:**8765\n2026-10-20 12:30")
        assertEquals("Test Receiver", outgoing.facts.counterparty)
        assertEquals("8765", outgoing.facts.destinationAccount?.maskedNumber)
    }

    @Test fun explicitOutgoingAccountWinsOverConfirmedPrimaryAccount() {
        val result = parse("BANK_SNB", "SNB-AlAhli", "حوالة صادرة محلية\nمن:8765*\nإلى:عميل تجريبي\nعبر:AL RAJHI BANK\nآيبان:*7654\nمبلغ:43.25 SAR\nفي:26/10/20 12:30", "4321", family = "TRANSFER_OUT")
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals("8765", result.facts.sourceAccount?.maskedNumber)
        assertEquals("7654", result.facts.destinationAccount?.maskedNumber)
    }

    @Test fun reversalKeepsItsExplicitBarqAccountAndCard() {
        val result = parse("BANK_BARQ", "barq", "عملية عكسية\nبطاقة مدى **4321\nمبلغ 3.25 SAR\nلدى TEST SHOP\nرقم الحساب123456789018765\nالدولة Saudi Arabia\n2026-10-20 12:30")
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals(MessageFamily.REFUND, result.facts.family)
        assertEquals("8765", result.facts.destinationAccount?.maskedNumber)
        assertEquals("4321", result.facts.card?.last4)
        assertEquals("3.25", result.facts.amount?.amount?.toPlainString())
    }

    @Test fun titleAmountsDoNotInventAnIncomingDestination() {
        for (kind in listOf("محلية", "داخلية")) {
            val result = parse("BANK_SNB", "SNB-AlAhli", "حوالة واردة $kind ب 43.25 SAR\nمن *8765 عميل تجريبي\n26/10/20 12:30")
            assertEquals("43.25", result.facts.amount?.amount?.toPlainString())
            assertNull(result.facts.destinationAccount)
            assertNotEquals(ParseStatus.SUCCESS, result.status)
        }
    }

    @Test fun cashWithdrawalReadsPrincipalFromItsTitle() {
        val result = parse("BANK_ALINMA", "alinma", "سحب نقدي 43.25 SAR\nفي TEST ATM\nبطاقة مدى 8765*\nفي 12:30 2026-10-20")
        assertEquals("43.25", result.facts.amount?.amount?.toPlainString())
        assertEquals("8765", result.facts.card?.last4)
        assertEquals(MessageFamily.WITHDRAWAL, result.facts.family)
    }

    @Test fun greetingCardPaymentPreservesExplicitCardAndSource() {
        val body = "هلا عميل تجريبي\nتم سداد البطاقة الائتمانية: **8765\nبمبلغ: 43.25 ريال\nمن: **4321\nفي: 2026-10-20 12:30\n"
        val result = parse("BANK_ALINMA", "alinma", body)
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals(MessageFamily.CARD_PAYMENT, result.facts.family)
        assertEquals("8765", result.facts.card?.last4)
        assertEquals("4321", result.facts.sourceAccount?.maskedNumber)
        assertEquals("43.25", result.facts.amount?.amount?.toPlainString())
        assertNotEquals(ParseStatus.SUCCESS, parse("BANK_ALINMA", "alinma", body.replace("تم سداد", "تعذر سداد")).status)
    }

    @Test fun travelerTopUpRequiresExplicitSourceAndCard() {
        val body = "خصم مبلغ: 75.25 USD\nمن حساب *4321\nإلى بطاقة المسافر *8765\nفي 2026-10-20 12:30\nالرصيد المتبقي: 90.50 USD"
        val result = parse("BANK_ALINMA", "alinma", body)
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals(MessageFamily.CARD_TOP_UP, result.facts.family)
        assertEquals("4321", result.facts.sourceAccount?.maskedNumber)
        assertEquals("8765", result.facts.card?.last4)
        assertEquals("75.25", result.facts.amount?.amount?.toPlainString())
        for (variant in listOf(body.replace("مبلغ: ", "مبلغ "), body.replace("مبلغ: ", "مبلغ:"), body.replace("\n", "\r\n\r\n"))) {
            val parsed = parse("BANK_ALINMA", "alinma", variant)
            assertEquals(parsed.diagnostics.toString(), ParseStatus.SUCCESS, parsed.status)
            assertEquals(result.facts.amount, parsed.facts.amount)
        }
        for (missing in listOf(body.replace("من حساب *4321\n", ""), body.replace("إلى بطاقة المسافر *8765\n", ""))) {
            assertNotEquals(ParseStatus.SUCCESS, parse("BANK_ALINMA", "alinma", missing).status)
        }
        val otp = parse("BANK_ALINMA", "alinma", "رمز التحقق 654321\n$body")
        assertEquals(MessageFamily.OTP, otp.facts.family)
    }

    private fun parse(bank: String, sender: String, body: String, defaultAccount: String? = null,
        reference: String? = null, family: String = "PURCHASE", destinationDefault: Boolean = false): Interpretation {
        val bundled = MessagePatternConfigurationCodec.decode(File("src/main/assets/bank-patterns/$bank.patterns.json").readText())
        val config = if (defaultAccount == null) bundled else bundled.copy(banks = bundled.banks.map { definition ->
            definition.copy(patterns = definition.patterns.map { pattern ->
                if (pattern.label != family) pattern else pattern.copy(factContract = pattern.factContract!!.copy(
                    version = 2, currency = com.baraa.masroof.finance.value.Currency.SAR,
                    accountBindings = listOf(PatternAccountBinding(if (destinationDefault) PatternAccountRole.DESTINATION else PatternAccountRole.SOURCE, BankId(bank), defaultAccount,
                        reference?.let { "source_account" }, reference))))
            })
        })
        val persisted = MessagePatternConfigurationCodec.decode(MessagePatternConfigurationCodec.encode(config))
        return PatternFactInterpreter(persisted).interpretRouted(
            MessageInput("synthetic", sender, body, Instant.parse("2026-10-20T12:00:00Z"), true), BankId(bank))
    }

    @Test fun confirmedShortReferenceLinksOnlyTheObservedSourceAndRetainsLoanFacts() {
        val body = "خصم قسط تمويل\nمن 900*567\nالقسط 123.45 SAR\nالمبلغ المتبقي 54321.00 SAR\nلـ تمويل شخصي\nفي 2026-10-20 12:30"
        val linked = parse("BANK_SNB", "SNB-AlAhli", body, "8765", "900*567", "FINANCING_INSTALLMENT")
        assertEquals(linked.diagnostics.toString(), ParseStatus.SUCCESS, linked.status)
        assertEquals("8765", linked.facts.sourceAccount?.maskedNumber)
        assertEquals(LoanType.PERSONAL, linked.facts.loanType)
        for (other in listOf(body.replace("900*567", "900*568"), body.replace("900*567", "901*567"),
                body.replace("من 900*567\n", ""), body + "\nمن 900*568")) {
            val rejected = parse("BANK_SNB", "SNB-AlAhli", other, "8765", "900*567", "FINANCING_INSTALLMENT")
            assertNull(rejected.facts.sourceAccount)
            assertNotEquals(ParseStatus.SUCCESS, rejected.status)
        }
        val unconfirmed = parse("BANK_SNB", "SNB-AlAhli", body, "8765", family = "FINANCING_INSTALLMENT")
        assertNotEquals(ParseStatus.SUCCESS, unconfirmed.status)
        assertTrue(runCatching { parse("BANK_SNB", "SNB-AlAhli", body, "8765", "900*4321", "FINANCING_INSTALLMENT") }.isFailure)
    }

    @Test fun cardPaymentRequiresAnExplicitReferenceBindingNotABankWideDefault() {
        val body = "بطاقة إئتمانية: تسديد\nبطاقة: ***6543;VISA\nمبلغ: 123.45 SAR\nمن: ***321\nفي: 2026-10-20 12:30:00"
        val linked = parse("BANK_SAB", "SAB", body, "4321", "321", "CARD_PAYMENT")
        assertEquals(linked.diagnostics.toString(), ParseStatus.SUCCESS, linked.status)
        assertEquals("4321", linked.facts.sourceAccount?.maskedNumber)
        assertEquals("6543", linked.facts.card?.last4)
        assertTrue(runCatching { parse("BANK_SAB", "SAB", body, "4321", family = "CARD_PAYMENT") }.isFailure)
        val changed = parse("BANK_SAB", "SAB", body.replace("***321", "***322"), "4321", "321", "CARD_PAYMENT")
        assertNotEquals(ParseStatus.SUCCESS, changed.status)
    }

    @Test fun explicitCardDoesNotAcquireTheDefaultAccountOrAnotherCardsIdentity() {
        val result = parse("BANK_SNB", "SNB-AlAhli", "شراء-POS\nبـ37 SAR\nمدى-ابل*4321\nفي 2026-10-20 12:30", "9876")
        assertEquals("4321", result.facts.card?.last4)
        assertNull(result.facts.sourceAccount)
        assertNull(result.facts.destinationAccount)
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        val missingCurrency = parse("BANK_SNB", "SNB-AlAhli", "شراء-POS\nمبلغ 37\nمدى-ابل*4321\nفي 2026-10-20 12:30", "9876")
        assertNotEquals(ParseStatus.SUCCESS, missingCurrency.status)
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
