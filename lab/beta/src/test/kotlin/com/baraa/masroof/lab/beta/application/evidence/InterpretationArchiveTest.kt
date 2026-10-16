package com.baraa.masroof.lab.beta.application.evidence

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService
import com.baraa.masroof.lab.finance.value.*
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InterpretationArchiveTest {
    @Test fun archiveCoversEveryFactsFieldAndRetainsVersionEvidenceConfidenceDiagnosticsNullsAndPrecision() {
        val bank = BankId("BANK_ALJAZIRA")
        val value = Interpretation("raw-id", InterpretationVersion(1, "engine-2", "rules-7"), bank,
            Facts(MessageFamily.PURCHASE, Money.of("10.25", Currency.USD), MoneyDirection.OUTGOING,
                sourceAccount = AccountReference(bank, "1234"), card = CardReference(bank, "5678"),
                merchant = "private merchant", exchangeRate = BigDecimal("3.751234567890"),
                occurredAt = Instant.parse("2026-10-16T10:00:00.123456Z")), ParseStatus.PARTIAL,
            Confidence(0.75, listOf("private confidence reason")), listOf(Evidence("r1", "amount", 2, 7)),
            setOf(Blocker.VALIDATION_ERROR), listOf(InterpretationDiagnostic("CODE", "private diagnostic", DiagnosticSeverity.WARNING)))
        val json = JSONObject(InterpretationArchive.encode(value))
        val fields = Facts::class.java.declaredFields.filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        val facts = json.getJSONObject("facts")
        assertEquals(fields, facts.keys().asSequence().toSet())
        assertEquals("3.751234567890", facts.getString("exchangeRate"))
        assertEquals("10.25", facts.getJSONObject("amount").getString("amount"))
        assertEquals("USD", facts.getJSONObject("amount").getString("currency"))
        assertEquals("BANK_ALJAZIRA", facts.getJSONObject("sourceAccount").getString("bank"))
        assertEquals("1234", facts.getJSONObject("sourceAccount").getString("number"))
        assertTrue(facts.isNull("destinationAccount"))
        assertEquals(1, json.getInt("archiveVersion"))
        assertEquals(1, json.getInt("contractVersion"))
        assertEquals("engine-2", json.getString("engineVersion"))
        assertEquals("rules-7", json.getString("rulesVersion"))
        assertEquals("raw-id", json.getString("evidenceId"))
        assertEquals("BANK_ALJAZIRA", json.getString("bank"))
        assertEquals("PARTIAL", json.getString("status"))
        assertEquals(0.75, json.getJSONObject("confidence").getDouble("score"), 0.0)
        assertEquals("private confidence reason", json.getJSONObject("confidence").getJSONArray("reasons").getString(0))
        assertEquals(2, json.getJSONArray("evidence").getJSONObject(0).getInt("start"))
        assertEquals(7, json.getJSONArray("evidence").getJSONObject(0).getInt("end"))
        assertEquals("private diagnostic", json.getJSONArray("diagnostics").getJSONObject(0).getString("message"))
        assertEquals("VALIDATION_ERROR", json.getJSONArray("blockers").getString(0))
        val unknown = JSONObject(InterpretationArchive.encode(value.copy(bank = null, confidence = null,
            blockers = setOf(Blocker.UNKNOWN_BANK))))
        assertTrue(unknown.isNull("bank"))
        assertTrue(unknown.isNull("confidence"))
    }

    @Test fun privatePayloadNeverAppearsInDefaultToStringOrUnsafeSummaryReasonCodes() {
        val message = EvidenceMessage("provider", "sender", "private body", Instant.EPOCH, ReceiptTimeSource.USER_SUPPLIED)
        assertFalse(message.toString().contains("private body"))
        val comparison = EvidenceComparison(EvidenceDisposition.REVIEW, setOf("FACTS_DIFFER"), "private body", "private diagnostic")
        assertFalse(comparison.toString().contains("private"))
        assertThrows(IllegalArgumentException::class.java) {
            EvidenceComparison(EvidenceDisposition.REVIEW, setOf("private body"), "{}", "{}")
        }
    }

    @Test fun equalPartialInvalidUnsupportedAndReviewStatusesRemainReviewEvenWithoutBlockers() {
        val bank = BankId("BANK_ALJAZIRA")
        val input = MessageInput("id", "sender", "private body", Instant.EPOCH)
        val route = EvidenceRoute(RouteKind.MATCHED, setOf(bank), "full")
        ParseStatus.entries.forEach { status ->
            val interpreter = BankInterpreter { _, _ -> Interpretation("id", InterpretationVersion(1, "fake", "r1"),
                bank, Facts(if (status == ParseStatus.NON_FINANCIAL) MessageFamily.NON_FINANCIAL else MessageFamily.PURCHASE),
                status, null, emptyList(), emptySet()) }
            val comparison = LabEvidenceComparer(BetaInterpretationService(interpreter, interpreter)).compare(input, route)
            if (status in setOf(ParseStatus.SUCCESS, ParseStatus.NON_FINANCIAL)) {
                assertEquals(EvidenceDisposition.COMPARISON_ONLY, comparison.disposition)
            } else {
                assertEquals(status.name, EvidenceDisposition.REVIEW, comparison.disposition)
                assertTrue("INTERPRETATION_REQUIRES_REVIEW" in comparison.reasons)
            }
        }
    }
}
