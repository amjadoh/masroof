package com.baraa.masroof.lab.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DynamicLabEngineTest {
    private val purchase = RuleSpec("purchase", "PURCHASE", listOf("شراء عبر نقاط البيع"))
    private val profile = BankProfile("demo", "بنك تجريبي", listOf("DEMO"))
    private val pack = RulePack(bank = profile, version = 1, rules = listOf(purchase))
    private fun parse(body: String, rules: List<RuleSpec> = pack.rules) = DynamicLabEngine().parse(
        listOf(pack.copy(rules = rules)), "DEMO", body, "sample", Instant.parse("2026-10-13T10:00:00Z"))

    @Test fun separatesAmountBalanceAndCard_withArabicDigitsAndDisplayEvidence() {
        val body = "شراء عبر نقاط البيع\nبطاقة: ١٢٣٤\nبمبلغ ١٧٨٫٠٢ SAR\nالرصيد المتاح: ٤٨٢١٫٩٨ SAR\nلدى: مَتجر أحمد"
        val result = parse(body)
        assertEquals("SUCCESS", result.status)
        assertEquals("178.02", result.facts.amount)
        assertEquals("4821.98", result.facts.balance)
        assertEquals("1234", result.facts.card)
        assertEquals("مَتجر أحمد", result.facts.merchant)
        result.evidence.forEach { e -> assertTrue(e.start >= 0 && e.endExclusive <= result.normalizedText.length) }
        val merchant = result.evidence.single { it.field == "merchant" }
        assertEquals("مَتجر أحمد", result.normalizedText.substring(merchant.start, merchant.endExclusive))
    }
    @Test fun otpWinsOverFinancialKeywordsAndAmount() {
        val result = parse("رمز التحقق لعملية شراء عبر نقاط البيع بمبلغ 100 SAR")
        assertEquals("NON_FINANCIAL", result.status);assertEquals("OTP", result.facts.family);assertNull(result.facts.amount)
    }
    @Test fun contradictoryAmountsNeverSelectFirst() {
        val result = parse("شراء عبر نقاط البيع\nبمبلغ 100 SAR\nبمبلغ 200 SAR")
        assertEquals("REVIEW_REQUIRED", result.status);assertNull(result.facts.amount)
    }
    @Test fun unknownCurrencyOrMissingAmountCannotSucceed() {
        listOf("شراء عبر نقاط البيع بمبلغ 100 KWD", "شراء عبر نقاط البيع", "شراء عبر نقاط البيع بمبلغ 1.234 SAR").forEach {
            assertEquals("REVIEW_REQUIRED", parse(it).status)
        }
    }
    @Test fun conflictingCardSuffixesAndWrongDirectionBlockAcceptance() {
        assertEquals("REVIEW_REQUIRED", parse("شراء عبر نقاط البيع بمبلغ 100 SAR\nبطاقة: 1234\nبطاقة: 5678").status)
        assertEquals("REVIEW_REQUIRED", parse("شراء عبر نقاط البيع بمبلغ 100 SAR", listOf(purchase.copy(direction = "INCOMING"))).status)
        assertEquals("REVIEW_REQUIRED", parse("شراء عبر نقاط البيع بمبلغ 100 SAR\nبطاقة: 12345").status)
        assertEquals("REVIEW_REQUIRED", parse("شراء عبر نقاط البيع بمبلغ 100 SAR\nبطاقة: غير واضح").status)
    }
    @Test fun classificationTiesAndNoticeMovementCollisionStayReviewable() {
        val transfer = RuleSpec("transfer", "TRANSFER_OUT", listOf("حوالة صادرة"))
        val body = "شراء عبر نقاط البيع حوالة صادرة بمبلغ 100 SAR"
        val a = parse(body, listOf(purchase, transfer));val b = parse(body, listOf(transfer, purchase))
        assertEquals("REVIEW_REQUIRED", a.status);assertEquals("UNKNOWN", a.facts.family);assertEquals(a, b)
        val notice = RuleSpec("notice", "NON_FINANCIAL", listOf("إشعار حساب"))
        assertEquals("UNKNOWN", parse("إشعار حساب شراء عبر نقاط البيع بمبلغ 100 SAR", listOf(notice, purchase)).facts.family)
    }
    @Test fun sameFamilyDifferentExtractionDoesNotDependOnRegistrationOrder() {
        val other = purchase.copy(id = "another", amountLabels = listOf("القيمة"))
        assertEquals("REVIEW_REQUIRED", parse("شراء عبر نقاط البيع بمبلغ 100 SAR", listOf(purchase, other)).status)
    }
    @Test fun routingChecksAllBanksAndRequiresExactSender() {
        val engine = DynamicLabEngine();val other = pack.copy(bank = profile.copy(id = "other"))
        assertEquals("REVIEW_REQUIRED", engine.parse(listOf(pack, other), "DEMO", "شراء عبر نقاط البيع بمبلغ 100 SAR", "s", Instant.now()).status)
        assertEquals("REVIEW_REQUIRED", engine.parse(listOf(pack), "DEMO-OTHER", "شراء عبر نقاط البيع بمبلغ 100 SAR", "s", Instant.now()).status)
        assertEquals("REVIEW_REQUIRED", engine.parse(listOf(pack), "DEMO", "شراء عبر نقاط البيع بمبلغ 100 SAR", "s", Instant.now(), "different-selected-bank").status)
    }
    @Test fun inlineMerchantCannotSwallowOtherFieldsAndConflictsReachValidationGate() {
        val inline = parse("شراء عبر نقاط البيع بمبلغ 100 SAR لدى: متجر المثال الرصيد المتاح: 200 SAR")
        assertNull(inline.facts.merchant)
        val conflict = parse("شراء عبر نقاط البيع بمبلغ 100 SAR\nبطاقة: 1234\nبطاقة: 5678")
        assertTrue(conflict.trace.any { it.startsWith("LAB-EVIDENCE:") })
    }
    @Test fun literalsCannotExecuteRegexAndInvalidConfigsFailEarly() {
        assertEquals("UNKNOWN", parse("شراء عبر نقاط البيع بمبلغ 100 SAR", listOf(purchase.copy(phrases = listOf(".*")))).facts.family)
        assertThrows(IllegalArgumentException::class.java) { RulePackValidation.check(pack.copy(schema = 2)) }
        assertThrows(IllegalArgumentException::class.java) { RulePackValidation.check(pack.copy(rules = listOf(purchase.copy(phrases = listOf(""))))) }
    }
    @Test fun baselineHonorsItsOwnSenderRegistryBeforeParsing() {
        val body = "شراء عبر نقاط البيع\nبمبلغ 100 SAR"
        assertEquals("SUCCESS", LabResultProjection.baseline("AlJazira", body, "s", Instant.now()).status)
        assertEquals("UNSUPPORTED", LabResultProjection.baseline("OTHER", body, "s", Instant.now()).status)
    }
    @Test fun expectedAmountDirectionAndIdentityAreIndependentOfPrediction() {
        val outcome = parse("شراء عبر نقاط البيع بمبلغ 100 SAR\nبطاقة: 1234")
        val expected = ExpectedResult("SUCCESS", LabFacts("PURCHASE", "INCOMING", "200.00", "SAR", card = "9999"))
        assertEquals(setOf("direction", "amount", "card"), LabComparison.differences(expected, outcome).toSet())
    }
}
