package com.baraa.masroof.lab.bank

import com.baraa.masroof.lab.bank.interpretation.RuleInterpreter
import com.baraa.masroof.lab.bank.rules.RuleLibrary
import com.baraa.masroof.lab.finance.value.BankId
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MessageLayoutCoverageTest {
    private val bank = BankId("UNRELATED_BANK")
    private fun parse(body: String): Interpretation = RuleInterpreter(RuleLibrary.current()).interpret(
        MessageInput("layout", "OtherSender", body, Instant.parse("2026-10-18T00:00:00Z")),
        BankCatalog(listOf(BankDefinition(bank, setOf("OtherSender")))))

    @Test fun timeFirstAndInlineClausesWorkAtAnyBankWithOriginalEvidence() {
        val ar = "شراء عبر نقاط البيع\nبطاقة مدى: ٤٥٦٧\nلدى: متجر مستقل 😀\nبمبلغ: SAR ٣٢.٥٠\nخصمت من حساب: ٨٩٠١\nفي: 14:32 03-08-2026"
        val r = parse(ar)
        assertEquals(ParseStatus.SUCCESS, r.status); assertEquals(bank, r.bank)
        assertEquals("8901", r.facts.debitSourceAccountLast4)
        assertEquals("2026-08-03T14:32", r.facts.occurredAtLocal.toString())
        assertTrue(r.evidence.any { it.field == "CARD" && ar.substring(it.start, it.end) == "٤٥٦٧" })
        val inline = "Online Purchase Apple Pay Credit Card: 4567 at :Other Shop of : 32.50 SAR on : 2026-08-03 14:32 Available Balance is: 900 SAR Due Amount: 20 SAR"
        val e = parse(inline)
        assertEquals(e.diagnostics.toString(), ParseStatus.SUCCESS, e.status)
        assertEquals("Other Shop", e.facts.merchant); assertEquals(CardChannel.CREDIT, e.facts.cardChannel)
        assertEquals("4567", e.facts.card!!.last4)
        assertTrue(e.evidence.all { it.start >= 0 && it.end <= inline.length && it.end > it.start })
    }

    @Test fun compoundRefundConsumesOnlyItsOwnPurchaseWords() {
        val body = "استرداد مبلغ عملية شراء عبر نقاط البيع\nبطاقة: 4567\nبمبلغ: SAR 32.50\nفي: 2026-08-03 14:32"
        assertEquals(ParseStatus.SUCCESS, parse(body).status)
        assertEquals(MessageFamily.REFUND, parse(body).facts.family)
        assertEquals(ParseStatus.REVIEW_REQUIRED, parse("$body\nPurchase").status)
        assertEquals(ParseStatus.REVIEW_REQUIRED, parse(body.replace("32.50", "-32.50")).status)
    }

    @Test fun invalidDatesAndRoleInjectionRemainReviewWithoutReceiptFallback() {
        val body = "Purchase\nCard: 4567\nAmount: SAR 32.50\nTime: 14:32 30-02-2026"
        assertEquals(ParseStatus.REVIEW_REQUIRED, parse(body).status)
        assertNull(parse(body).facts.occurredAtLocal)
        assertEquals(ParseStatus.REVIEW_REQUIRED, parse(body.replace("Time:", "Merchant:")).status)
        val quoted = "> Online Purchase Credit Card: 4567 of: 32 SAR on: 2026-08-03 14:32"
        assertNotEquals(ParseStatus.SUCCESS, parse(quoted).status)
        assertEquals(ParseStatus.NON_FINANCIAL, parse("OTP 987654 $body").status)
    }

    @Test fun partyDirectoryAndStatementsNeverCreatePrincipalOrTime() {
        val directory = "اسم المستفيد: Synthetic Person\nالاسم المختصر: Test\nحالة: غير نشط"
        assertEquals(ParseStatus.NON_FINANCIAL, parse(directory).status)
        assertNull(parse(directory).facts.amount)
        assertEquals(ParseStatus.REVIEW_REQUIRED, parse("$directory\nPurchase\nAmount: 42 SAR").status)
        val statement = parse("إصدار كشف حساب\nالمبلغ المستحق: 100 SAR\nتاريخ الاستحقاق: 25/08/2026")
        assertEquals(ParseStatus.NON_FINANCIAL, statement.status)
        assertNull(statement.facts.amount); assertNull(statement.facts.occurredAtLocal)
        assertEquals("2026-08-25", statement.facts.paymentDueDate.toString())
    }
}
