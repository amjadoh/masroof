@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.baraa.masroof.lab.engine.acceptance

import com.baraa.masroof.lab.engine.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Deliberately synthetic evaluator tests; none is evidence of real holdout precision. */
class M6AcceptanceTest {
    private val hash = "a".repeat(64)
    private val facts = LabFacts(family = "PURCHASE", direction = "OUTGOING", amount = "10", currency = "SAR")
    private fun case(body: String = "synthetic-private-message", status: String = "SUCCESS", facts: LabFacts = this.facts) = ReviewedCase(
        "private-id", "private-sender", body, "2026-10-16T12:00:00Z", listOf(BankProfile("BANK_TEST", "test", listOf("private-sender"))),
        status, LabJson.format.encodeToJsonElement(facts).jsonObject, "private-reviewer", "private-review-reference")
    private fun manifest(cases: List<ReviewedCase> = listOf(case())) = HoldoutManifest(1, "REVIEWED_REAL_SMS", "INDEPENDENT_HOLDOUT",
        "2026-10-15T12:00:00Z", "private-declaration-only", hash, hash, cases)
    private val policy = AcceptancePolicy("reviewed-policy", 1, 1, 1.0, 1.0)
    private fun assess(m: HoldoutManifest = manifest(), actual: LabOutcome = LabOutcome("SUCCESS", facts),
                       p: AcceptancePolicy? = policy, development: Set<String> = emptySet()) = M6Acceptance.assess(m, p,
        m.cases.map { M6Acceptance.measure(it, actual) }, development, hash, hash)

    @Test fun exactAcceptedResultsMeetOnlyMeasurementGates() {
        val result = assess()
        assertTrue(result.measurementGatesMet)
        assertEquals(1.0, result.acceptedPrecision!!, 0.0)
        assertEquals(1.0, result.financialCoverage!!, 0.0)
        assertTrue(M6Acceptance.safeReport(result).contains("no production promotion"))
    }

    @Test fun anyExtraOrMissingFactOnSuccessIsACriticalAcceptedError() {
        listOf(facts.copy(amount = "100"), facts.copy(paymentDueDate = "2026-10-31"), facts.copy(currency = null),
            facts.copy(card = "1234"), facts.copy(amount = "10.00")).forEach {
            val result = assess(actual = LabOutcome("SUCCESS", it))
            assertEquals(1, result.criticalAcceptedErrors)
            assertEquals(0.0, result.acceptedPrecision!!, 0.0)
            assertFalse(result.measurementGatesMet)
        }
    }

    @Test fun otpOrReviewAcceptedAsFinancialCannotBeHiddenByCoverage() {
        listOf("NON_FINANCIAL", "REVIEW_REQUIRED", "UNSUPPORTED", "PARTIAL", "INVALID").forEach { status ->
            val result = assess(manifest(listOf(case(status = status, facts = LabFacts(family = "OTP")))))
            assertEquals(1, result.criticalAcceptedErrors)
            assertNull(result.financialCoverage)
        }
    }

    @Test fun zeroAcceptanceHasUndefinedPrecisionAndNeverPasses() {
        val result = assess(actual = LabOutcome("REVIEW_REQUIRED", facts))
        assertNull(result.acceptedPrecision)
        assertEquals(0.0, result.financialCoverage!!, 0.0)
        assertTrue("NO_ACCEPTED_RESULTS" in result.blockers)
    }

    @Test fun financialAbstentionsReduceCoverageWithoutBecomingAcceptedErrors() {
        val first = case()
        val second = case(body = "another-synthetic-message").copy(id = "second")
        val m = manifest(listOf(first, second))
        val rows = listOf(M6Acceptance.measure(first, LabOutcome("SUCCESS", facts)),
            M6Acceptance.measure(second, LabOutcome("REVIEW_REQUIRED")))
        val result = M6Acceptance.assess(m, policy.copy(minimumCases = 2, minimumFinancialCases = 2,
            minimumFinancialCoverage = 0.5), rows, emptySet(), hash, hash)
        assertTrue(result.measurementGatesMet)
        assertEquals(0, result.criticalAcceptedErrors)
        assertEquals(1.0, result.acceptedPrecision!!, 0.0)
        assertEquals(0.5, result.financialCoverage!!, 0.0)
        assertFalse(M6Acceptance.assess(m, policy, rows, emptySet(), hash, hash).measurementGatesMet)
    }

    @Test fun syntheticAndRegressionNeverBecomeHoldoutByPassingParser() {
        assertTrue("NOT_DECLARED_REVIEWED_REAL_HOLDOUT" in assess(manifest().copy(provenance = "SYNTHETIC")).blockers)
        assertTrue("NOT_DECLARED_REVIEWED_REAL_HOLDOUT" in assess(manifest().copy(split = "REGRESSION")).blockers)
        assertTrue("DEVELOPMENT_CORPUS_OVERLAP" in assess(development = setOf(M6Acceptance.messageHash(case().body))).blockers)
        assertEquals(M6Acceptance.messageHash("  مُبلغ: ١٠\r\n SAR "), M6Acceptance.messageHash("مبلغ: 10\nSAR"))
    }

    @Test fun changedCandidateAndMissingPolicyRemainBlocked() {
        assertTrue("CANDIDATE_FINGERPRINT_MISMATCH" in assess(manifest().copy(engineSourceSha256 = "b".repeat(64))).blockers)
        assertTrue("CANDIDATE_FINGERPRINT_MISMATCH" in assess(manifest().copy(librarySha256 = "b".repeat(64))).blockers)
        assertTrue("MISSING_REVIEWED_MEASUREMENT_POLICY" in assess(p = null).blockers)
        assertTrue("INSUFFICIENT_REVIEWED_CASES" in assess(p = policy.copy(minimumCases = 2)).blockers)
    }

    @Test fun duplicateContentWithDifferentIdsAndSendersIsRejected() {
        val first = case()
        val result = assess(manifest(listOf(first, first.copy(id = "other", sender = "other"))))
        assertTrue("DUPLICATE_MESSAGE_CONTENT" in result.blockers)
    }

    @Test fun missingNullFieldsDoNotBecomeUnannotatedWildcards() {
        val partial = case().copy(expectedFacts = JsonObject(case().expectedFacts - "paymentDueDate"))
        assertThrows(IllegalArgumentException::class.java) { M6Acceptance.validate(manifest(listOf(partial))) }
        assertEquals(LabFacts.serializer().descriptor.elementsCount, M6Acceptance.factNames.size)
    }

    @Test fun invalidFinancialAnnotationsAndInvalidPolicyAreRejected() {
        listOf(facts.copy(amount = "-10"), facts.copy(family = "OTP"), facts.copy(currency = null)).forEach {
            assertThrows(IllegalArgumentException::class.java) { M6Acceptance.validate(manifest(listOf(case(facts = it)))) }
        }
        assertThrows(IllegalArgumentException::class.java) { assess(p = policy.copy(minimumAcceptedPrecision = Double.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { assess(p = policy.copy(reviewReference = "")) }
    }

    @Test fun safeArtifactNeverContainsTextIdsReviewersOrExtractedValues() {
        val report = M6Acceptance.safeReport(assess(actual = LabOutcome("SUCCESS", facts.copy(counterparty = "secret-counterparty"))))
        listOf("synthetic-private-message", "private-id", "private-sender", "private-reviewer", "private-review-reference", "secret-counterparty").forEach {
            assertFalse(report.contains(it))
        }
        assertTrue(report.contains("counterparty"))
    }

    @Test fun isoExtensionOnInformationalStatementRemainsVisibleAndUnapproved() {
        val original = LabFacts(family = "NON_FINANCIAL")
        val result = assess(manifest(listOf(case(status = "NON_FINANCIAL", facts = original))),
            LabOutcome("NON_FINANCIAL", original.copy(paymentDueDate = "2026-10-31")))
        assertEquals(setOf("paymentDueDate"), result.measurements.single().differences)
        assertEquals(0, result.criticalAcceptedErrors)
        assertTrue("UNREVIEWED_STATUS_OR_FACT_DIFFERENCES" in result.blockers)
    }
}
