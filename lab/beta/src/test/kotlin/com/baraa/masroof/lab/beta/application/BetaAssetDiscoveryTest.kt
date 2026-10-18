package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.beta.application.evidence.*
import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.value.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BetaAssetDiscoveryTest {
    private val bank = BankId("BANK_ALJAZIRA")
    private val route = EvidenceRoute(RouteKind.MATCHED, setOf(bank), "full")
    private fun archive(facts: Facts = Facts(MessageFamily.PURCHASE, sourceAccount = AccountReference(bank, "1234")), status: ParseStatus = ParseStatus.PARTIAL,
        blockers: Set<Blocker> = setOf(Blocker.VALIDATION_ERROR), id: String = "evidence") =
        InterpretationArchive.encode(Interpretation(id, com.baraa.masroof.lab.beta.verification.FinancialArchiveFixture.VERSION, bank, facts, status, null, listOf(Evidence("typed-source", "source", 0, 4), Evidence("typed-destination", "destination", 5, 9),
            Evidence("typed-card", "card", 10, 14), Evidence("typed-channel", "card.credit", 15, 20)), blockers + if (facts.family == MessageFamily.UNKNOWN) setOf(Blocker.UNKNOWN_FAMILY) else emptySet(),
            if (blockers == setOf(Blocker.VALIDATION_ERROR)) listOf(InterpretationDiagnostic("MISSING_AMOUNT", "Amount absent", DiagnosticSeverity.ERROR)) else emptyList()))
    private fun proposals(json: String) = BetaAssetDiscovery.proposals("evidence", route, json)
    @Test fun missingFinancialAmountDoesNotEraseSafeIdentityAndNeverCreatesOwnership() {
        val found = proposals(archive())
        assertEquals(1, found.size)
        assertEquals(AssetKind.ACCOUNT, found.single().kind)
        assertEquals("1234", found.single().last4)
        assertEquals(found, proposals(archive()))
        assertFalse(found.single().toString().contains("1234"))
    }
    @Test fun securityUnexecutedUnknownAndInvalidAreNotAssetEvidence() {
        listOf(MessageFamily.OTP, MessageFamily.NON_FINANCIAL, MessageFamily.UNKNOWN).forEach { family ->
            assertTrue(proposals(archive(Facts(family, sourceAccount = AccountReference(bank, "1234")))).isEmpty())
        }
        listOf("REJECTED", "PENDING", "REQUESTED", "UNPROVEN", "unknown").forEach { state ->
            assertTrue(proposals(archive(Facts(MessageFamily.PURCHASE, sourceAccount = AccountReference(bank, "1234"), executionState = state))).isEmpty())
        }
        listOf(ParseStatus.INVALID, ParseStatus.UNSUPPORTED).forEach { assertTrue(proposals(archive(status = it)).isEmpty()) }
        listOf(Blocker.CONFLICTING_FACTS, Blocker.EVIDENCE_MAPPING_ERROR, Blocker.UNKNOWN_BANK).forEach { assertTrue(proposals(archive(blockers = setOf(it))).isEmpty()) }
    }
    @Test fun counterpartyNumbersIbanReferencesAndUnclassifiedCardsAreNotOwnedCandidates() {
        val incoming = Facts(MessageFamily.TRANSFER_IN, sourceAccount = AccountReference(bank, "1111"), destinationAccount = AccountReference(bank, "2222"))
        assertEquals(listOf("2222"), proposals(archive(incoming)).map { it.last4 })
        assertEquals(listOf("1111"), proposals(archive(incoming.copy(family = MessageFamily.TRANSFER_OUT))).map { it.last4 })
        assertTrue(proposals(archive(Facts(MessageFamily.PURCHASE, card = CardReference(bank, "3333"), ibanSuffix = "4444", transactionReference = "5555", accountIdentifier = "6666"))).isEmpty())
        assertTrue(proposals(archive(Facts(MessageFamily.PURCHASE, sourceAccount = AccountReference(null, "1234")))).isEmpty())
        listOf("123456789", "****1234", "12", "abcd").forEach { assertTrue(proposals(archive(Facts(MessageFamily.PURCHASE, sourceAccount = AccountReference(bank, it)))).isEmpty()) }
    }
    @Test fun explicitCardKindDoesNotInferParentsFacilityRoleOrMergeEqualSuffixEvidence() {
        val card = Facts(MessageFamily.PURCHASE, card = CardReference(bank, "1234"), cardChannel = CardChannel.CREDIT)
        assertEquals(AssetKind.CREDIT_CARD, proposals(archive(card)).single().kind)
        val a = proposals(archive()).single()
        val b = BetaAssetDiscovery.proposals("other", route, archive(id = "other")).single()
        assertNotEquals(a.id, b.id)
        assertEquals(a.id, proposals(archive(Facts(MessageFamily.PURCHASE, sourceAccount = AccountReference(bank, "9999")))).single().id)
        assertNotEquals(a.revision, proposals(archive(Facts(MessageFamily.PURCHASE, sourceAccount = AccountReference(bank, "9999")))).single().revision)
    }
    @Test fun genericValidationUnknownSecurityAndMissingRoleProvenanceStayReviewable() {
        val generic = JSONObject(archive())
        generic.getJSONArray("diagnostics").getJSONObject(0).put("code", "LAB_TRACE")
        assertTrue(proposals(generic.toString()).isEmpty())
        val security = JSONObject(archive(blockers = emptySet()))
        security.getJSONArray("diagnostics").put(JSONObject().put("code", "SECURITY_WARNING"))
        assertTrue(proposals(security.toString()).isEmpty())
        assertTrue(proposals(JSONObject(archive()).put("evidence", org.json.JSONArray()).toString()).isEmpty())
        val card = JSONObject(archive(Facts(MessageFamily.PURCHASE, card = CardReference(bank, "1234"), cardChannel = CardChannel.DEBIT)))
        assertTrue(proposals(card.toString()).isEmpty()) // Only credit-channel provenance was supplied.
    }
    @Test fun archiveVersionIdentityBankAndMalformedNullsFailClosed() {
        val original = archive()
        listOf("archiveVersion", "contractVersion").forEach { assertTrue(proposals(JSONObject(original).put(it, 999).toString()).isEmpty()) }
        listOf("evidenceId", "bank", "status").forEach { assertTrue(proposals(JSONObject(original).put(it, "wrong").toString()).isEmpty()) }
        assertTrue(proposals("not json").isEmpty())
        val malformedExecution = JSONObject(original)
        malformedExecution.getJSONObject("facts").put("executionState", 123)
        assertTrue(proposals(malformedExecution.toString()).isEmpty())
        assertTrue(proposals(JSONObject(original).put("archiveVersion", "1").toString()).isEmpty())
        assertTrue(proposals(JSONObject(original).put("facts", JSONObject.NULL).toString()).isEmpty())
        assertTrue(BetaAssetDiscovery.proposals("evidence", EvidenceRoute(RouteKind.AMBIGUOUS, setOf(bank, BankId("OTHER")), "full"), original).isEmpty())
    }
}
