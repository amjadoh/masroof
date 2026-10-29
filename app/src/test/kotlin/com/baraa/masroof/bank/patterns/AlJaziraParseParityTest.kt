package com.baraa.masroof.bank.patterns

import com.baraa.masroof.integration.bank.NewEngineFixtureTest
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.*
import com.baraa.masroof.parsing.finalize.ParseFinalizer
import com.baraa.masroof.parsing.model.ParsedEventDraft
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.model.SmsParseInput
import com.baraa.masroof.parsing.validator.DefaultParsedEventValidator
import com.baraa.masroof.testsupport.engineFixtureAdapter
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class AlJaziraParseParityTest {
    @Test fun defaultIntegrationFixtureUsesBundledProductionDefinitions() {
        val result = engineFixtureAdapter().parse(SmsParseInput("default-fixture", "AlJazira",
            "Purchase\nAmount:10 SAR\nFrom account:4321\nMerchant:SHOP\nOn:2026-10-15 12:30",
            Instant.parse("2026-10-20T12:00:00Z")))
        assertTrue(result is com.baraa.masroof.parsing.model.ParseResult.Success)
        val parsed = result as com.baraa.masroof.parsing.model.ParseResult.Success
        assertTrue("engine:message-pattern-roles-v1" in parsed.event.confidence.reasons)
    }

    @Test fun allCurrentFixturesMatchFrozenProductionFacts() {
        val adapter = bundledAlJaziraPatternAdapter()
        val cases = NewEngineFixtureTest.cases().map { files ->
            val row = Json.parseToJsonElement(files.single().readText()).jsonObject
            SmsParseInput(row.getValue("id").jsonPrimitive.content, row.getValue("sender").jsonPrimitive.content,
                row.getValue("body").jsonPrimitive.content, Instant.parse("2026-10-20T12:00:00Z"))
        }
        require(cases.map { it.rawSmsId }.distinct().size == cases.size)
        val actual = buildJsonObject {
            cases.sortedBy { it.rawSmsId }.forEach { input ->
                put(input.rawSmsId, buildJsonObject {
                    put("input", ParseParity.digest(listOf(input.sender, input.body, input.receivedAt)))
                    put("detection", ParseParity.digest(adapter.detect(input.sender, input.body)))
                    ParseParity.fields(adapter.parse(input)).forEach { (field, value) -> put(field, value) }
                })
            }
        }
        // Export never updates the checked-in baseline. Capturing a new reference requires explicit review.
        val output = File("build/reports/pattern-parity/current-parse.json")
        output.parentFile.mkdirs()
        output.writeText(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), actual))
        val baseline = File("src/test/resources/testdata/pattern-parity/aljazira-parse-v1.json")
        assertTrue("Frozen baseline missing; current reference exported to build/reports/pattern-parity", baseline.isFile)
        val expected = Json.parseToJsonElement(baseline.readText()).jsonObject
        assertEquals("Corpus changed; audit additions/removals", expected.keys, actual.keys)
        val changes = actual.keys.flatMap { id ->
            ParseParity.differences(expected.getValue(id).jsonObject.mapValues { it.value.jsonPrimitive.content },
                actual.getValue(id).jsonObject.mapValues { it.value.jsonPrimitive.content }).map { "$id:$it" }
        }
        assertTrue("Changed fields (values masked): ${changes.joinToString()}", changes.isEmpty())
    }

    @Test fun comparatorDetectsChangedAmountsDetailsGatesAndRemovedFields() {
        val draft = ParsedEventDraft("probe", Bank.BANK_ALJAZIRA, MessageFamily.PURCHASE, MoneyDirection.OUTGOING,
            Money.of("1", Currency.SAR), merchant = "SYNTHETIC", confidence = Confidence(1.0, listOf("engine:old", "rules:old")),
            details = ParsedEventDetails(paymentDueDate = LocalDate.of(2026, 8, 1)))
        val finalizer = ParseFinalizer(DefaultParsedEventValidator())
        fun snapshot(value: ParsedEventDraft) = ParseParity.fields(finalizer.finalize(value, "evt-probe"))
        val original = snapshot(draft)
        assertTrue(ParseParity.differences(original, snapshot(draft.copy(
            confidence = Confidence(1.0, listOf("engine:new", "rules:new"))))).isEmpty())
        val changes = listOf(
            "event.amount" to draft.copy(amount = Money.of("2", Currency.SAR)),
            "details.paymentDueDate" to draft.copy(details = draft.details.copy(paymentDueDate = LocalDate.of(2026, 9, 1))),
            "event.parseStatus" to draft.copy(confidence = Confidence(0.0, emptyList())),
            "event.amount" to draft.copy(amount = null),
            "details.negativeAvailableBalance" to draft.copy(details = draft.details.copy(negativeAvailableBalance = Money.of("2", Currency.SAR))),
        )
        for ((field, changed) in changes) {
            assertTrue("Missed change to $field", field in ParseParity.differences(original, snapshot(changed)))
        }
    }
}
