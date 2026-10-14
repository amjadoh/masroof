package com.baraa.masroof.lab.engine

import com.baraa.masroof.lab.engine.evidence.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class EvidenceLabEngineTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val bank = BankProfile("demo", "Demo", listOf("DEMO"))
    private fun parse(body: String, knowledge: EvidenceKnowledge = EvidenceDefaults.create()) =
        EvidenceLabEngine(Clock.fixed(now, ZoneOffset.UTC)).parse(listOf(bank), knowledge, "DEMO", body, "test", now)
    @Test fun independentExtractionRetainsAmountsEvenForUnknownText() {
        val a = parse("صيغة مجهولة\nالمبلغ: SAR 10\nالرصيد: 90.00 SAR\nالمبلغ المستحق: 40.00 SAR")
        assertEquals("UNKNOWN", a.outcome.facts.family)
        assertEquals("10", a.outcome.facts.amount)
        assertEquals("90.00", a.outcome.facts.balance)
        assertEquals("40.00", a.outcome.facts.dueAmount)
        assertEquals("REVIEW_REQUIRED", a.outcome.status)
    }
    @Test fun purchaseSeparatesDuesAndBalanceAndDoesNotInventVisa() {
        val a = parse("POS Purchase Contactless\nCredit card 1234\nof :37.50 SAR\nAt: TEST_SHOP\non :2026-10-13 15:29\nAvailable Balance :9000.00 SAR\nDue Amount: 2000.00 SAR")
        assertEquals("SUCCESS", a.outcome.status)
        assertEquals("37.50", a.outcome.facts.amount)
        assertEquals("1234", a.outcome.facts.card)
        assertEquals("CREDIT", a.outcome.facts.cardKind)
        assertNull(a.outcome.facts.network)
        assertEquals("TEST_SHOP", a.outcome.facts.merchant)
        assertEquals("9000.00", a.outcome.facts.balance)
        assertEquals("2026-10-13T15:29", a.outcome.facts.occurredAtLocal)
    }
    @Test fun datesRatesAndIdentifiersDoNotBecomePrincipalMoney() {
        val a = parse("شراء\nبطاقة: 2026\nالمبلغ: 10 SAR\nسعر الصرف: 3.756957\nفي: 2026-10-13 12:00")
        assertEquals("10", a.outcome.facts.amount)
        assertEquals("2026", a.outcome.facts.card)
        assertEquals("3.756957", a.outcome.facts.exchangeRate)
        assertEquals("SUCCESS", a.outcome.status)
        assertEquals("REVIEW_REQUIRED", parse("شراء\nبطاقة: 2026\nالرصيد: 10.00 SAR").outcome.status)
    }
    @Test fun originalTextSpansSurviveArabicFoldingAndDigits() {
        val body = "شِراء\nبمبلغ: ٣٨٫٠٠ SAR\nلدى: أَسواق النُور\nبطاقة: ٠٠٠٧"
        val a = parse(body)
        assertEquals("38.00", a.outcome.facts.amount)
        assertEquals("0007", a.outcome.facts.card)
        assertEquals("أَسواق النُور", a.outcome.facts.merchant)
        a.outcome.evidence.forEach { assertTrue(it.start >= 0 && it.endExclusive <= body.length && it.endExclusive > it.start) }
        val merchant = a.relations.single { it.role == "merchant" }
        assertEquals("أَسواق النُور", body.substring(merchant.start, merchant.endExclusive))
    }
    @Test fun securityAndExecutionStateOverrideFinancialAcceptance() {
        assertEquals("NON_FINANCIAL", parse("رمز التحقق لعملية شراء بمبلغ 42 SAR هو 9876").outcome.status)
        listOf("declined" to "REJECTED", "pending" to "PENDING", "payment request" to "REQUESTED").forEach { (cue, state) ->
            val a = parse("POS purchase\nAmount: 42 SAR\n$cue")
            assertEquals(state, a.outcome.facts.executionState)
            assertEquals("REVIEW_REQUIRED", a.outcome.status)
        }
    }
    @Test fun conflictsMissingCurrencyAndBadDatesStayReviewable() {
        listOf("شراء\nالمبلغ: 10 SAR\nالمبلغ: 11 SAR", "شراء\nالمبلغ: 10", "شراء\nالمبلغ: 10 SAR\nفي: 2026-02-30 12:00",
            "حوالة واردة وحوالة صادرة\nالمبلغ: 10 SAR", "شراء\nالمبلغ: 10 SAR\nبطاقة: 1234\nبطاقة: 5678").forEach {
            assertEquals(it, "REVIEW_REQUIRED", parse(it).outcome.status)
        }
    }
    @Test fun sharedKnowledgeHasNoBankSpecificBranchAndOrderDoesNotMatter() {
        val library = EvidenceDefaults.create()
        val body = "شراء\nبمبلغ 42 SAR\nبطاقة: 1234"
        val first = parse(body, library)
        val second = parse(body, library.copy(rules = library.rules.reversed()))
        assertEquals(first.outcome.facts, second.outcome.facts)
        assertEquals(first.outcome.status, second.outcome.status)
        val other = bank.copy(id = "other", senders = listOf("OTHER"))
        assertEquals(first.outcome.facts, EvidenceLabEngine().parse(listOf(other), library, "OTHER", body, "t", now).outcome.facts)
        assertEquals("REVIEW_REQUIRED", EvidenceLabEngine().parse(listOf(bank, bank.copy(id = "collision")), library, "DEMO", body, "t", now).outcome.status)
    }
    @Test fun explicitAbsenceDetectsInventedValuesAndLegacyBlanksStayUnscored() {
        val actual = LabOutcome("REVIEW_REQUIRED", LabFacts(family = "UNKNOWN", card = "2026"))
        val legacy = ExpectedResult("REVIEW_REQUIRED", LabFacts(family = "UNKNOWN"))
        assertTrue(ReferenceFields.differences(legacy, actual).isEmpty())
        assertEquals(listOf("card"), ReferenceFields.differences(legacy.copy(absentFields = setOf("card")), actual))
        try { ReferenceFields.validate(legacy.copy(absentFields = setOf("family"))); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun newPhraseNeedsRequiredRolesAndCanBePreviewedWithoutMutation() {
        val library = EvidenceDefaults.create()
        val draft = library.copy(rules = library.rules + ConceptRule("custom.purchase", "family.PURCHASE", listOf("تم التسوق"), requiredRoles = setOf("card")))
        assertEquals("UNKNOWN", parse("تم التسوق\nالمبلغ: 10 SAR", draft).outcome.facts.family)
        assertEquals("PURCHASE", parse("تم التسوق\nالمبلغ: 10 SAR\nبطاقة: 1234", draft).outcome.facts.family)
        assertEquals("UNKNOWN", parse("تم التسوق\nالمبلغ: 10 SAR\nبطاقة: 1234", library).outcome.facts.family)
    }
    @Test fun threeKnownContextConflictsStayReviewableWhileNearbyValidPurchasePasses() {
        listOf("collision_purchase_statement_due_ar_001", "collision_titled_fee_transfer_ar_001", "collision_transfer_beneficiary_notice_ar_001").forEach { id ->
            val resource = javaClass.getResource("/testdata/bank_aljazira/unknown/$id.json")!!
            val body = Json.parseToJsonElement(resource.readText()).jsonObject.getValue("body").jsonPrimitive.content
            val outcome = parse(body).outcome
            assertEquals(id, "UNKNOWN", outcome.facts.family)
            assertEquals(id, "REVIEW_REQUIRED", outcome.status)
        }
        assertEquals("SUCCESS", parse("شراء\nالمبلغ: 10 SAR\nالمبلغ المستحق: 300 SAR").outcome.status)
    }
    @Test fun requiredRoleOnFieldRuleIsEnforcedWithoutCircularSelfJustification() {
        val knowledge = EvidenceDefaults.create()
        val conditional = knowledge.copy(rules = knowledge.rules.map { if (it.concept == "field.amount") it.copy(requiredRoles = setOf("card")) else it })
        assertNull(parse("شراء\nالمبلغ: 10 SAR", conditional).outcome.facts.amount)
        assertEquals("10", parse("شراء\nالمبلغ: 10 SAR\nبطاقة: 1234", conditional).outcome.facts.amount)
        val circular = conditional.copy(rules = conditional.rules.map { if (it.concept == "field.card") it.copy(requiredRoles = setOf("amount")) else it })
        assertNull(parse("شراء\nالمبلغ: 10 SAR\nبطاقة: 1234", circular).outcome.facts.amount)
    }
    @Test fun taxAndMinimumPaymentsNeverBecomePurchasePrincipal() {
        listOf("tax amount", "minimum amount", "minimum payment", "مبلغ الضريبة", "الحد الأدنى للسداد").forEach { label ->
            val a = parse("شراء\n$label: 42 SAR")
            assertNull(label, a.outcome.facts.amount)
            assertEquals(label, "REVIEW_REQUIRED", a.outcome.status)
        }
    }
    @Test fun excessiveEvidenceIsBoundedAndEquivalentDateReferencesMatch() {
        assertEquals("REVIEW_REQUIRED", parse("شراء ".repeat(700) + "\nالمبلغ: 10 SAR").outcome.status)
        assertTrue(ReferenceFields.equal("occurredAtLocal", "2026-10-01T09:00:00", "2026-10-01T09:00"))
    }
    @Test fun dateWithoutTimeNeverInventsMidnight() {
        val a = parse("شراء\nبمبلغ 10 SAR\nفي: 2026-10-01")
        assertNull(a.outcome.facts.occurredAtLocal)
        assertEquals("REVIEW_REQUIRED", a.outcome.status)
        assertEquals("2026-10-01", a.relations.single { it.role == "dateOnly" }.value)
    }
    @Test fun sourceAliasesCanImproveKnowledgeWithoutMakingCurrencyAmountsAccounts() {
        val original = EvidenceDefaults.create()
        val tuned = original.copy(version = 2, rules = original.rules.map { if (it.concept == "field.source") it.copy(phrases = it.phrases + listOf("من", "رقم حساب المرسل")) else it })
        val message = "حوالة واردة\nمبلغ: 100 SAR\nرقم حساب المرسل: 0007\nإلى: 8001"
        assertNull(parse(message, original).outcome.facts.source)
        assertEquals("0007", parse(message, tuned).outcome.facts.source)
        assertNull(parse("حوالة واردة\nمبلغ: 10 SAR\nمن: 1000 SAR", tuned).outcome.facts.source)
    }
    @Test fun merchantNetworkWordsDoNotIdentifyTheCardNetworkOrKind() {
        val a = parse("POS purchase\nAmount: 10 SAR\nCard: 1234\nAt: Visa Mada Shop").outcome
        assertEquals("SUCCESS", a.status)
        assertNull(a.facts.network);assertNull(a.facts.cardKind)
    }
    @Test fun refundWordInsideMerchantDoesNotProveAnIncomingRefundAndRejectionStillBlocks() {
        val a = parse("POS purchase\nAmount: 10 SAR\nCard: 1234\nAt: Refund Shop").outcome
        assertEquals("REVIEW_REQUIRED", a.status);assertEquals("UNKNOWN", a.facts.family);assertNull(a.facts.direction)
        val declined = parse("POS purchase\nAmount: 10 SAR\nCard: 1234\nAt: TEST_SHOP declined").outcome
        assertEquals("REVIEW_REQUIRED", declined.status)
        assertEquals("REJECTED", declined.facts.executionState)
    }
    @Test fun genericSettlementNeedsCardAndCannotOverrideBillOrInstallmentWording() {
        assertEquals("UNKNOWN", parse("تسديد\nالمبلغ: 10 SAR").outcome.facts.family)
        assertEquals("CARD_PAYMENT", parse("تسديد\nالمبلغ: 10 SAR\nبطاقة: 1234").outcome.facts.family)
        assertEquals("UNKNOWN", parse("تسديد فاتورة\nالمبلغ: 10 SAR\nبطاقة: 1234").outcome.facts.family)
    }
}
