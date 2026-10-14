package com.baraa.masroof.lab.engine

import com.baraa.masroof.lab.copy.parsing.fixtures.AlJaziraFixtureLoader
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class SharedContextCollisionTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val engine = SharedLabEngine(Clock.fixed(now, ZoneOffset.UTC))
    private val bank = BankProfile("generic", "Generic bank", listOf("TEST"))
    private fun parse(body: String, reversed: Boolean = false): LabOutcome {
        val library = SharedRuleDefaults.create()
        return engine.parse(listOf(bank), if (reversed) library.copy(rules = library.rules.reversed()) else library,
            "TEST", body, "case", now)
    }

    @Test fun threeKnownCollisionsAreReviewableInEitherRuleOrder() {
        val ids = setOf("collision_purchase_statement_due_ar_001", "collision_titled_fee_transfer_ar_001",
            "collision_transfer_beneficiary_notice_ar_001")
        val fixtures = AlJaziraFixtureLoader.loadAllFromClasspath().filter { it.id in ids }
        assertEquals(3, fixtures.size)
        fixtures.forEach { fixture -> listOf(false, true).forEach { reversed ->
            val result = parse(fixture.body, reversed)
            assertEquals(fixture.id + result.trace, "REVIEW_REQUIRED", result.status)
            assertEquals("UNKNOWN", result.facts.family)
            assertTrue(result.trace.toString(), result.trace.any { "shared.context." in it })
        } }
    }

    @Test fun ordinaryCreditPurchaseAndBeneficiaryTransferRemainFinancial() {
        assertEquals("SUCCESS", parse("POS Purchase\nCredit card 1234\nAmount: SAR 42.00\nAt: TEST SHOP\nDue Amount: SAR 1000.00").status)
        assertEquals("SUCCESS", parse("حوالة صادرة\nاسم المستفيد: TEST PERSON\nبمبلغ 100 SAR").status)
        assertEquals("SUCCESS", parse("حوالة صادرة\nبمبلغ 100 SAR\nمبلغ الرسوم: SAR 5.00").status)
    }

    @Test fun pairedContextAndTitleAreRequired() {
        assertEquals("SUCCESS", parse("شراء بمبلغ 42 SAR\nتاريخ الاستحقاق: 2026-10-25").status)
        assertEquals("SUCCESS", parse("حوالة صادرة\nبمبلغ 42 SAR\nحالة: غير نشط").status)
        val english = parse("Outgoing transfer\nBeneficiary: TEST\nStatus: inactive\nAmount: SAR 42.00")
        assertEquals("REVIEW_REQUIRED", english.status)
        assertEquals("UNKNOWN", english.facts.family)
        assertEquals("UNKNOWN", parse("Transfer fee for outgoing transfer\nAmount: SAR 5.00").facts.family)
    }

    @Test fun securityStillWinsOverConflictingFinancialContext() {
        val result = parse("رمز التحقق لعملية الشراء هو 9876\nبمبلغ 42 SAR\nالمبلغ المستحق: 100 SAR\nتاريخ الاستحقاق: 2026-10-25")
        assertEquals("OTP", result.facts.family)
        assertEquals("NON_FINANCIAL", result.status)
    }
}
