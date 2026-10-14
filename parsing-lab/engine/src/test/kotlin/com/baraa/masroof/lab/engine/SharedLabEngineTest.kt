package com.baraa.masroof.lab.engine

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class SharedLabEngineTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val engine = SharedLabEngine(Clock.fixed(now, ZoneOffset.UTC))
    private val banks = listOf(BankProfile("a", "بنك تجريبي أ", listOf("TEST-A")), BankProfile("b", "بنك تجريبي ب", listOf("TEST-B")))
    private fun parse(body: String, sender: String = "TEST-A", library: SharedRuleLibrary = SharedRuleDefaults.create()) =
        engine.parse(banks, library, sender, body, "sample", now)

    @Test fun anonymousPurchaseVariantsWorkForEveryBankWithoutBankRules() {
        val bodies = listOf(
            "POS Purchase Contactless\nCredit card 1234\nof :42.00 SAR\nAt: TEST SHOP\non : 2026-10-01 15:29\nAvailable Balance : 9000.00 SAR\nDue Amount: 1000.00 SAR",
            "شراء عبر نقاط البيع\nباستخدام بطاقة اختبار مدى-أثير (1234) بمبلغ (001) SAR 42.00 لدى TEST SHOP في: 2026-10-01 15:29:00.",
            "شراء عبر نقاط البيع\nبطاقة: ١٢٣٤\nبمبلغ: ٤٢.٠٠ SAR\nلدى: TEST SHOP\nفي: ٢٠٢٦-١٠-٠١ ١٥:٢٩",
        )
        bodies.forEach { body -> banks.forEach { bank ->
            val result = parse(body, bank.senders.single())
            assertEquals(result.trace.toString(), "SUCCESS", result.status)
            assertEquals("PURCHASE", result.facts.family)
            assertEquals("42.00", result.facts.amount)
            assertEquals("1234", result.facts.card)
            assertEquals("TEST SHOP", result.facts.merchant)
            assertEquals("2026-10-01T15:29", result.facts.occurredAtLocal)
            assertEquals("OUTGOING", result.facts.direction)
            result.evidence.forEach { assertTrue(it.start >= 0 && it.endExclusive <= result.normalizedText.length) }
        } }
        val credit = parse(bodies.first())
        assertEquals("9000.00", credit.facts.balance)
        assertEquals("1000.00", credit.facts.dueAmount)
    }

    @Test fun sarieTransferSeparatesDestinationAndIbanWithoutInferringOwnership() {
        val body = "Fund Transfer Credited (sarie)\nFrom: TEST PERSON\nExample Bank\nTo: 4321\nIBAN: 5678\nExample Bank\nAmount: SAR 1500.00\nOn: 2026-10-01 19:39:03"
        val result = parse(body)
        assertEquals(result.trace.toString(), "SUCCESS", result.status)
        assertEquals("TRANSFER_IN", result.facts.family)
        assertEquals("INCOMING", result.facts.direction)
        assertEquals("1500.00", result.facts.amount)
        assertEquals("4321", result.facts.destination)
        assertEquals("5678", result.facts.ibanSuffix)
        assertNull(result.facts.source)
    }

    @Test fun broadFamilyCatalogHasIndependentArabicAndEnglishExamples() {
        val examples = mapOf(
            "TRANSFER_IN" to listOf("حوالة واردة", "Incoming transfer"),
            "TRANSFER_OUT" to listOf("حوالة صادرة", "Fund Transfer Debited"),
            "BILL_PAYMENT" to listOf("سداد فاتورة", "Bill payment"),
            "CARD_PAYMENT" to listOf("سداد بطاقة", "Credit card payment"),
            "FINANCING_INSTALLMENT" to listOf("سداد قسط", "Loan repayment"),
            "WITHDRAWAL" to listOf("سحب نقدي", "ATM withdrawal"),
            "REFUND" to listOf("استرداد مبلغ شراء", "Purchase refund"),
            "FEE" to listOf("خصم رسوم", "Service fee"),
        )
        examples.forEach { (family, titles) -> titles.forEach { title ->
            val result = parse("$title\nبمبلغ 50 SAR\nبطاقة: 1234")
            assertEquals("$title: ${result.trace}", "SUCCESS", result.status)
            assertEquals(title, family, result.facts.family)
            assertEquals(LabFamilies.direction(family), result.facts.direction)
        } }
    }

    @Test fun otpDeclinesRequestsAndNoticesNeverBecomeSuccessfulMovements() {
        val otp = parse("رمز التحقق لعملية الشراء هو 9876. بمبلغ 42 SAR")
        assertEquals("NON_FINANCIAL", otp.status); assertEquals("OTP", otp.facts.family)
        assertNull(otp.facts.amount)
        listOf("POS Purchase declined", "POS Purchase pending", "لم تتم عملية شراء", "طلب سداد بطاقة").forEach {
            assertEquals(it, "REVIEW_REQUIRED", parse("$it\nبمبلغ 42 SAR\nبطاقة: 1234").status)
        }
        listOf("كشف حساب", "Statement issued", "Balance notification", "تم إضافة مستفيد").forEach {
            assertEquals(it, "NON_FINANCIAL", parse("$it\nAmount: SAR 42.00").status)
        }
        assertEquals("REVIEW_REQUIRED", parse("كشف حساب\nشراء بمبلغ 42 SAR").status)
    }

    @Test fun conflictingFieldsUnknownFormatsAndBadNumbersStayReviewable() {
        listOf(
            "شراء بمبلغ 42 SAR\nبمبلغ 43 SAR",
            "شراء بمبلغ 42 SAR\nبطاقة: 1234\nبطاقة: 5678",
            "شراء\nAmount: SAR 42.123",
            "شراء\nAmount: SAR -42.00",
            "شراء\nCredit card 1234567890123456\nAmount: SAR 42.00",
            "شراء\nAvailable balance: 9000 SAR\nDue Amount: 1000 SAR",
            "شراء\nTax amount: SAR 5.00",
            "شراء\nبمبلغ (1234) SAR 42.00",
            "شراء\nAmount: SAR 42.00\nOn: 2026-02-30 12:00",
            "نص غير معروف بمبلغ 42 SAR",
            "حوالة واردة وحوالة صادرة بمبلغ 42 SAR",
        ).forEach { assertEquals(it, "REVIEW_REQUIRED", parse(it).status) }
        val onlyDue = parse("شراء\nAmount: 42 SAR\nOutstanding balance: 1000 SAR")
        assertEquals("42.00", onlyDue.facts.amount); assertNull(onlyDue.facts.balance)
    }

    @Test fun routingIsSenderOnlyAndRejectsCollisions() {
        assertEquals("REVIEW_REQUIRED", parse("TEST-A شراء بمبلغ 42 SAR", "OTHER").status)
        val collision = engine.parse(banks + banks.first().copy(id = "c"), SharedRuleDefaults.create(), "TEST-A", "شراء بمبلغ 42 SAR", "s", now)
        assertEquals("REVIEW_REQUIRED", collision.status)
        assertEquals("SUCCESS", parse("شراء بمبلغ 42 SAR", " test-a ").status)
    }

    @Test fun sharedEditsApplyToBothBanksAndDoNotMutatePreviousLibrary() {
        val initial = SharedRuleDefaults.create()
        val edited = initial.copy(version = 2, rules = initial.rules.map { if (it.family == "PURCHASE") it.copy(amountLabels = it.amountLabels + "قيمة العملية") else it })
        banks.forEach {
            assertEquals("REVIEW_REQUIRED", parse("شراء\nقيمة العملية: 50 SAR", it.senders.single(), initial).status)
            assertEquals("SUCCESS", parse("شراء\nقيمة العملية: 50 SAR", it.senders.single(), edited).status)
        }
    }

    @Test fun arabicMoneyFormattingPreservesRolesAndMerchantSpelling() {
        val result = parse("شراء بمبلغ ١٬٢٠٠٫٥٠ ريال سعودي\nبطاقة: ١٢٣٤\nلدى: مَتجر أمان\nالرصيد المتاح: ٩٬٠٠٠٫٠٠ ر.س")
        assertEquals(result.trace.toString(), "SUCCESS", result.status)
        assertEquals("1200.50", result.facts.amount)
        assertEquals("9000.00", result.facts.balance)
        assertEquals("مَتجر أمان", result.facts.merchant)
        val merchant = result.evidence.single { it.field == "merchant" }
        assertEquals(result.facts.merchant, result.normalizedText.substring(merchant.start, merchant.endExclusive))
    }

    @Test fun sameFamilyMergesEvidenceAndDifferentDirectionsStayAmbiguous() {
        val library = SharedRuleDefaults.create()
        val purchase = library.rules.first()
        val extra = purchase.copy(id = "extra", amountLabels = listOf("قيمة العملية"))
        assertEquals("SUCCESS", parse("شراء بمبلغ 42 SAR", library = library.copy(rules = library.rules + extra)).status)
        assertEquals("REVIEW_REQUIRED", parse("شراء بمبلغ 42 SAR", library = library.copy(rules = library.rules + extra.copy(direction = "INCOMING"))).status)
    }
}
