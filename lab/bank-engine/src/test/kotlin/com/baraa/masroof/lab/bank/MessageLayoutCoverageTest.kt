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
        val ar = "ط´ط±ط§ط، ط¹ط¨ط± ظ†ظ‚ط§ط· ط§ظ„ط¨ظٹط¹\nط¨ط·ط§ظ‚ط© ظ…ط¯ظ‰: ظ¤ظ¥ظ¦ظ§\nظ„ط¯ظ‰: ظ…طھط¬ط± ظ…ط³طھظ‚ظ„ ًںک€\nط¨ظ…ط¨ظ„ط؛: SAR ظ£ظ¢.ظ¥ظ \nط®طµظ…طھ ظ…ظ† ط­ط³ط§ط¨: ظ¨ظ©ظ ظ،\nظپظٹ: 14:32 03-08-2026"
        val r = parse(ar)
        assertEquals(ParseStatus.SUCCESS, r.status); assertEquals(bank, r.bank)
        assertEquals("8901", r.facts.debitSourceAccountLast4)
        assertEquals("2026-08-03T14:32", r.facts.occurredAtLocal.toString())
        assertTrue(r.evidence.any { it.field == "CARD" && ar.substring(it.start, it.end) == "ظ¤ظ¥ظ¦ظ§" })
        val inline = "Online Purchase Apple Pay Credit Card: 4567 at :Other Shop of : 32.50 SAR on : 2026-08-03 14:32 Available Balance is: 900 SAR Due Amount: 20 SAR"
        val e = parse(inline)
        assertEquals(e.diagnostics.toString(), ParseStatus.SUCCESS, e.status)
        assertEquals("Other Shop", e.facts.merchant); assertEquals(CardChannel.CREDIT, e.facts.cardChannel)
        assertEquals("4567", e.facts.card!!.last4)
        assertTrue(e.evidence.all { it.start >= 0 && it.end <= inline.length && it.end > it.start })
    }

    @Test fun compoundRefundConsumesOnlyItsOwnPurchaseWords() {
        val body = "ط§ط³طھط±ط¯ط§ط¯ ظ…ط¨ظ„ط؛ ط¹ظ…ظ„ظٹط© ط´ط±ط§ط، ط¹ط¨ط± ظ†ظ‚ط§ط· ط§ظ„ط¨ظٹط¹\nط¨ط·ط§ظ‚ط©: 4567\nط¨ظ…ط¨ظ„ط؛: SAR 32.50\nظپظٹ: 2026-08-03 14:32"
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
        val directory = "ط§ط³ظ… ط§ظ„ظ…ط³طھظپظٹط¯: Synthetic Person\nط§ظ„ط§ط³ظ… ط§ظ„ظ…ط®طھطµط±: Test\nط­ط§ظ„ط©: ط؛ظٹط± ظ†ط´ط·"
        assertEquals(ParseStatus.NON_FINANCIAL, parse(directory).status)
        assertNull(parse(directory).facts.amount)
        assertEquals(ParseStatus.REVIEW_REQUIRED, parse("$directory\nPurchase\nAmount: 42 SAR").status)
        val statement = parse("ط¥طµط¯ط§ط± ظƒط´ظپ ط­ط³ط§ط¨\nط§ظ„ظ…ط¨ظ„ط؛ ط§ظ„ظ…ط³طھط­ظ‚: 100 SAR\nطھط§ط±ظٹط® ط§ظ„ط§ط³طھط­ظ‚ط§ظ‚: 25/08/2026")
        assertEquals(ParseStatus.NON_FINANCIAL, statement.status)
        assertNull(statement.facts.amount); assertNull(statement.facts.occurredAtLocal)
        assertEquals("2026-08-25", statement.facts.paymentDueDate.toString())
    }
    @Test fun sameLineCardNumberAndDirectoryMetadataStayRoleBound() {
        val body = "Credit Card: Refund\nCard: Credit Number:4567\nFrom: Synthetic Shop\nAmount: 12 SAR\nDate: 2026-08-03 14:32"
        val result = parse(body)
        assertEquals(result.diagnostics.toString(), ParseStatus.SUCCESS, result.status)
        assertEquals("4567", result.facts.card!!.last4)
        assertEquals("Synthetic Shop", result.facts.merchant)
        assertEquals(ParseStatus.NON_FINANCIAL, parse("كلمة مرور صالحة لمرة واحدة\n$body").status)
        val directory = "اسم المستفيد: Synthetic Person\nالاسم المختصر: Test\nحالة: غير نشط\nحساب: SA00001234\nبنك: Remote Bank\nفي: 2026-08-03 14:32"
        val notice = parse(directory)
        assertEquals(ParseStatus.NON_FINANCIAL, notice.status)
        assertNull(notice.facts.amount); assertNull(notice.facts.sourceAccount); assertNull(notice.facts.occurredAtLocal)
        assertEquals(ParseStatus.REVIEW_REQUIRED, parse("$directory\nAmount: 12 SAR").status)
        // A custom longer PURCHASE phrase cannot consume an independent refund cue.
        val schema = RuleLibrary.current().let { base -> base.copy(rules = base.rules.map {
            if (it.family == MessageFamily.PURCHASE) it.copy(phrases = it.phrases + "purchase refund") else it
        }) }
        val conflict = RuleInterpreter(schema).interpret(MessageInput("conflict", "OtherSender",
            "purchase refund\nCard:4567\nAmount:12 SAR\nDate:2026-08-03 14:32", Instant.EPOCH),
            BankCatalog(listOf(BankDefinition(bank, setOf("OtherSender")))))
        assertEquals(ParseStatus.REVIEW_REQUIRED, conflict.status)
    }

}
