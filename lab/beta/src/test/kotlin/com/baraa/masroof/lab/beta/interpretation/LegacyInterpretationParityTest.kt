package com.baraa.masroof.lab.beta.interpretation

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.beta.application.interpretation.LegacyInterpretationAdapter
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.copy.bank.aljazira.AlJaziraSmsAdapter
import com.baraa.masroof.lab.copy.parsing.model.ParseResult
import com.baraa.masroof.lab.copy.parsing.model.ParsedEventDraft
import com.baraa.masroof.lab.copy.parsing.model.SmsParseInput
import com.baraa.masroof.lab.copy.domain.model.Bank
import com.baraa.masroof.lab.copy.domain.model.Confidence as OldConfidence
import com.baraa.masroof.lab.copy.domain.model.MessageFamily as OldFamily
import com.baraa.masroof.lab.copy.domain.model.MoneyDirection as OldDirection
import com.baraa.masroof.lab.copy.parsing.model.CardSmsChannel
import com.baraa.masroof.lab.copy.parsing.model.ParsedEventDetails
import java.io.File
import java.time.Instant
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LegacyInterpretationParityTest {
    private val bank = BankId("BANK_ALJAZIRA")
    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val adapter = LegacyInterpretationAdapter()
    private fun catalog(sender: String) = BankCatalog(listOf(BankDefinition(bank, setOf(sender))))

    @Test fun everyCheckedInFixtureRetainsGoldenFactsAndFullTypedLegacyFields() {
        val fixtures = File("../engine/src/test/resources/testdata/bank_aljazira").walkTopDown()
            .filter { it.extension == "json" && it.parentFile.name != "reference" }.sortedBy { it.path }.toList()
        assertTrue("Missing corpus", fixtures.size >= 40)
        val statuses = mutableMapOf<String, Int>()
        fixtures.forEach { file ->
            val fixture = Json.parseToJsonElement(file.readText()).jsonObject
            val input = MessageInput(fixture.getValue("id").jsonPrimitive.content, fixture.getValue("sender").jsonPrimitive.content,
                fixture.getValue("body").jsonPrimitive.content, now)
            val actual = adapter.interpret(input, catalog(input.sender))
            val legacy = AlJaziraSmsAdapter().parse(SmsParseInput(input.evidenceId, input.sender, input.body, now))
            val expected = fixture.getValue("expected").jsonObject
            val fields = contractFields(actual)
            expected.forEach { (field, value) ->
                if (value != JsonNull) assertEquals("${file.name}: $field", value.jsonPrimitive.content, fields[field])
            }
            assertFullLegacyFields(input.evidenceId, legacy, actual)
            assertEquals(bank, actual.bank)
            assertEquals(input.body, actual.evidence.single().slice(input))
            assertEquals("source_message", actual.evidence.single().field)
            statuses[actual.status.name] = statuses.getOrDefault(actual.status.name, 0) + 1
        }
        val report = File("build/reports/beta-interpretation/legacy-parity.txt")
        report.parentFile.mkdirs()
        report.writeText("Reference: preserved lab parser, not production cutover\nFixtures: ${fixtures.size}\nGolden and full typed-field mismatches: 0\nStatuses: $statuses\nHeld-out corpus: not supplied\n")
        println(report.readText())
    }

    @Test fun invalidDraftNeutralAndStatementAreRetainedWithoutLiftingTheGate() {
        val draft = ParsedEventDraft("invalid", Bank.BANK_ALJAZIRA, OldFamily.PURCHASE,
            OldDirection.NEUTRAL, confidence = OldConfidence(0.3),
            details = ParsedEventDetails(cardSmsChannel = CardSmsChannel.STATEMENT))
        val input = MessageInput("invalid", "AlJazira", "invalid draft", now)
        val result = adapter.map(input, bank, ParseResult.Invalid(emptyList(), draft))
        assertEquals(ParseStatus.INVALID, result.status)
        assertEquals(MoneyDirection.NEUTRAL, result.facts.direction)
        assertEquals(CardChannel.STATEMENT, result.facts.cardChannel)
        assertTrue(Blocker.VALIDATION_ERROR in result.blockers)
    }

    @Test fun diagnosticsAndProvenanceCannotBeDiscardedOrRelabeled() {
        val input = MessageInput("invalid", "AlJazira", "sample", now)
        val draft = ParsedEventDraft("invalid", bank = Bank.BANK_ALJAZIRA, messageFamily = OldFamily.UNKNOWN)
        val finding = com.baraa.masroof.lab.copy.parsing.validator.ValidationFinding("V-TEST", "conflicting amount",
            com.baraa.masroof.lab.copy.parsing.validator.ValidationSeverity.ERROR)
        val invalid = adapter.map(input, bank, ParseResult.Invalid(listOf(finding), draft))
        assertEquals(listOf(InterpretationDiagnostic("V-TEST", "conflicting amount", DiagnosticSeverity.ERROR)), invalid.diagnostics)
        val review = adapter.map(input, bank, ParseResult.ReviewRequired(draft, null, listOf(finding), listOf("needs review")))
        assertTrue(review.diagnostics.any { it.code == "REVIEW_REASON" && it.message == "needs review" })
        assertEquals("unsupported", adapter.map(input, bank, ParseResult.Unsupported("unsupported")).diagnostics.single().message)
        assertEquals("informational", adapter.map(input, bank, ParseResult.NonFinancial("informational")).diagnostics.single().message)
        assertThrows(IllegalArgumentException::class.java) { adapter.map(input.copy(evidenceId = "different"), bank, ParseResult.Invalid(emptyList(), draft)) }
        assertThrows(IllegalArgumentException::class.java) { adapter.map(input, BankId("other"), ParseResult.Invalid(emptyList(), draft)) }
    }

    @Test fun unknownAndAmbiguousCatalogNeverChooseAnAdapterByOrder() {
        val input = MessageInput("x", "AlJazira", "POS Purchase\nCard: 1234\nAmount: 42 SAR", now)
        assertTrue(Blocker.UNKNOWN_BANK in adapter.interpret(input, BankCatalog(emptyList())).blockers)
        val defs = listOf(BankDefinition(bank, setOf(input.sender)), BankDefinition(BankId("another"), setOf(input.sender)))
        val a = adapter.interpret(input, BankCatalog(defs))
        val b = adapter.interpret(input, BankCatalog(defs.reversed()))
        assertEquals(a, b)
        assertEquals(ParseStatus.REVIEW_REQUIRED, a.status)
        assertTrue(Blocker.AMBIGUOUS_BANK in a.blockers)
    }

    private fun assertFullLegacyFields(id: String, result: ParseResult, actual: Interpretation) {
        val event = when (result) {
            is ParseResult.Success -> result.event
            is ParseResult.Partial -> result.event
            is ParseResult.ReviewRequired -> result.event
            is ParseResult.NonFinancial -> result.event
            else -> null
        }
        val details = when (result) {
            is ParseResult.Success -> result.details
            is ParseResult.Partial -> result.details
            is ParseResult.ReviewRequired -> result.details
            is ParseResult.NonFinancial -> result.details
            is ParseResult.Invalid -> result.draft?.details ?: ParsedEventDetails()
            else -> ParsedEventDetails()
        }
        // Enumerating every details property makes future unrepresented facts fail this suite.
        val renamed = mapOf("cardSmsChannel" to "cardChannel")
        ParsedEventDetails::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach { old ->
            val target = Facts::class.java.getDeclaredField(renamed[old.name] ?: old.name)
            old.isAccessible = true; target.isAccessible = true
            assertEquals("$id details ${old.name}", canonical(old.get(details)), canonical(target.get(actual.facts)))
        }
        if (event != null) {
            assertEquals(id, actual.evidenceId)
            assertEquals(event.messageFamily.name, actual.facts.family.name)
            assertEquals(event.direction?.name, actual.facts.direction?.name)
            assertEquals(canonical(event.amount), canonical(actual.facts.amount))
            assertEquals(event.sourceAccountRef?.maskedNumber, actual.facts.sourceAccount?.maskedNumber)
            assertEquals(event.sourceAccountRef?.bank?.id?.takeUnless { it == "UNKNOWN" }, actual.facts.sourceAccount?.bank?.value)
            assertEquals(event.destinationAccountRef?.maskedNumber, actual.facts.destinationAccount?.maskedNumber)
            assertEquals(event.destinationAccountRef?.bank?.id?.takeUnless { it == "UNKNOWN" }, actual.facts.destinationAccount?.bank?.value)
            assertEquals(event.cardRef?.last4, actual.facts.card?.last4)
            assertEquals(event.cardRef?.bank?.id, actual.facts.card?.bank?.value)
            assertEquals(event.merchant, actual.facts.merchant)
            assertEquals(event.counterparty, actual.facts.counterparty)
            assertEquals(event.occurredAt, actual.facts.occurredAt)
            assertEquals(event.purchaseChannel?.name, actual.facts.purchaseChannel?.name)
            assertEquals(event.bankNetworkType?.name, actual.facts.bankNetwork?.name)
            assertEquals(event.confidence.score, actual.confidence!!.score, 0.0)
            assertEquals(event.confidence.reasons, actual.confidence!!.reasons)
            assertEquals(event.parseStatus.name, actual.status.name)
        }
    }

    private fun canonical(value: Any?): Any? = when (value) {
        is com.baraa.masroof.lab.copy.core.money.Money -> value.amount.toPlainString() + value.currency.name
        is com.baraa.masroof.lab.finance.value.Money -> value.amount.toPlainString() + value.currency.name
        is Enum<*> -> value.name
        else -> value
    }

    private fun contractFields(result: Interpretation): Map<String, String?> {
        val f = result.facts
        return mapOf("bank" to result.bank?.value, "messageFamily" to f.family.name, "direction" to f.direction?.name,
            "amount" to f.amount?.amount?.toPlainString(), "currency" to f.amount?.currency?.name,
            "purchaseChannel" to f.purchaseChannel?.name, "bankNetworkType" to f.bankNetwork?.name,
            "sourceAccountLast4" to f.sourceAccount?.maskedNumber, "destinationAccountLast4" to f.destinationAccount?.maskedNumber,
            "cardLast4" to f.card?.last4, "merchant" to f.merchant, "counterparty" to f.counterparty,
            "biller" to f.biller, "billerCode" to f.billerCode, "transactionReference" to f.transactionReference,
            "availableBalance" to f.availableBalance?.amount?.toPlainString(), "outstandingBalance" to f.outstandingBalance?.amount?.toPlainString(),
            "occurredAt" to f.occurredAtLocal?.let { java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(it) },
            "parseStatus" to result.status.name, "cardSmsChannel" to f.cardChannel?.name,
            "paymentDueDate" to f.paymentDueDate?.toString(), "exchangeRate" to f.exchangeRate?.toPlainString(),
            "internationalFee" to f.internationalFee?.amount?.toPlainString(), "labeledForeignAmount" to f.labeledForeignAmount?.amount?.toPlainString(),
            "labeledForeignCurrency" to f.labeledForeignAmount?.currency?.name, "loanType" to f.loanType?.name,
            "debitSourceAccountLast4" to f.debitSourceAccountLast4, "salaryIncomeWording" to f.salaryIncomeWording?.toString())
    }
}
