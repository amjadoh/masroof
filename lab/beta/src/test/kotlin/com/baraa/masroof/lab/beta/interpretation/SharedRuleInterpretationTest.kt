package com.baraa.masroof.lab.beta.interpretation

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.beta.application.interpretation.*
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.finance.value.Currency
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SharedRuleInterpretationTest {
    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val bank = BankId("BANK_ALJAZIRA")
    private val catalog = BankCatalog(listOf(BankDefinition(bank, setOf("AlJazira"))))
    private fun schema() = SharedRuleInterpretationAdapter.currentLabSchema()
    private fun input(body: String) = MessageInput("sample", "AlJazira", body, now)
    private fun parse(body: String, rules: BankRuleSchema = schema()) = SharedRuleInterpretationAdapter(rules, clock).interpret(input(body), catalog)

    @Test fun secondaryCurrenciesNeverUseThePrincipalCurrency() {
        listOf("USD" to "SAR", "SAR" to "USD").forEach { (principal, secondary) ->
            val result = parse("POS Purchase\nCard: 1234\nAmount: 42 $principal\nAvailable balance: 100 $secondary\nDue amount: 20 $secondary")
            assertEquals(result.toString(), ParseStatus.SUCCESS, result.status)
            assertEquals(Currency.valueOf(principal), result.facts.amount!!.currency)
            assertEquals(Currency.valueOf(secondary), result.facts.availableBalance!!.currency)
            assertEquals(Currency.valueOf(secondary), result.facts.outstandingBalance!!.currency)
            assertTrue(result.blockers.isEmpty())
        }
        val partial = parse("POS Purchase\nCard: 1234\nAvailable balance: 100 USD")
        assertNotEquals(ParseStatus.SUCCESS, partial.status)
        assertNull(partial.facts.amount)
        assertEquals(Currency.USD, partial.facts.availableBalance!!.currency)
    }

    @Test fun originalEvidenceSurvivesCrLfWhitespaceArabicDigitsNfcAndEmoji() {
        val bodies = listOf(
            "  POS Purchase  \r\n\tCard： ١٢٣٤ \r\n Amount： ٤٢٫٠٠ SAR \r\n At: Cafe 😀  ",
            "شراء\nبطاقة: ۱۲۳۴\nبمبلغ: ٤٢.٠٠ SAR\nلدى: ا\u0654حمد 😀",
            "POS Purchase\nCard: 1234\nAmount: 1٬234٫00 SAR\nAt: SHOP")
        bodies.forEach { body ->
            val result = parse(body)
            assertEquals(result.toString(), ParseStatus.SUCCESS, result.status)
            assertFalse(Blocker.EVIDENCE_MAPPING_ERROR in result.blockers)
            assertTrue(result.evidence.isNotEmpty())
            val card = result.evidence.first { it.field == "card" }.slice(input(body))
            assertTrue(card == "١٢٣٤" || card == "۱۲۳۴" || card == "1234")
            result.evidence.forEach { assertTrue(it.start >= 0 && it.end <= body.length && it.slice(input(body)).isNotBlank()) }
        }
        assertNotEquals(ParseStatus.SUCCESS, parse("   \r\n ").status)
        assertNotEquals(ParseStatus.SUCCESS, parse("").status)
    }

    @Test fun otpRejectedPendingAndMultiplePrincipalAmountsNeverSucceed() {
        listOf("رمز التحقق لعملية الشراء 9876 بمبلغ 42 SAR", "declined", "rejected", "pending", "payment request").forEach { prefix ->
            assertNotEquals(ParseStatus.SUCCESS, parse("$prefix\nPOS Purchase\nCard: 1234\nAmount: 42 SAR").status)
        }
        val conflict = parse("POS Purchase\nCard: 1234\nAmount: 42 SAR\nAmount: 43 SAR")
        assertNotEquals(ParseStatus.SUCCESS, conflict.status)
        assertTrue(Blocker.VALIDATION_ERROR in conflict.blockers)
    }

    @Test fun unsupportedVersionAndRegexLookingTermsCannotExecute() {
        assertTrue(Blocker.UNSUPPORTED_RULE_SCHEMA in parse("anything", schema().copy(schema = 99)).blockers)
        val literal = BankRuleSchema(revision = 1, rules = listOf(BankRule("literal", MessageFamily.PURCHASE,
            listOf("(a+)+$"), labels = mapOf(ExtractionField.AMOUNT to listOf("amount")))))
        assertNotEquals(ParseStatus.SUCCESS, parse("a".repeat(1000) + "!\nAmount: 42 SAR", literal).status)
    }

    @Test fun shadowCorpusProducesAnHonestFieldDiffReportWithoutChangingTheReference() {
        val service = BetaInterpretationService.sharedRules(clock)
        val fixtures = File("../engine/src/test/resources/testdata/bank_aljazira").walkTopDown()
            .filter { it.extension == "json" && it.parentFile.name != "reference" }.sortedBy { it.path }.toList()
        val counts = sortedMapOf<String, Int>()
        var exact = 0; var accepted = 0; var acceptanceDifferences = 0
        val rows = fixtures.map { file ->
            val fixture = Json.parseToJsonElement(file.readText()).jsonObject
            val source = MessageInput(fixture.getValue("id").jsonPrimitive.content, fixture.getValue("sender").jsonPrimitive.content,
                fixture.getValue("body").jsonPrimitive.content, now)
            val comparison = service.compare(source, catalog)
            val diffs = buildList {
                if (comparison.baseline.status != comparison.candidate.status) add("status")
                Facts::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach { field ->
                    field.isAccessible = true
                    if (field.get(comparison.baseline.facts) != field.get(comparison.candidate.facts)) add(field.name)
                }
            }
            diffs.forEach { counts[it] = counts.getOrDefault(it, 0) + 1 }
            if (diffs.isEmpty()) exact++
            if (comparison.candidate.status == ParseStatus.SUCCESS) { accepted++; if (diffs.isNotEmpty()) acceptanceDifferences++ }
            source.evidenceId + ": " + if (diffs.isEmpty()) "equal" else diffs.joinToString(",")
        }
        assertTrue(fixtures.size >= 40)
        val report = File("build/reports/beta-interpretation/shared-shadow-diff.txt")
        report.parentFile.mkdirs()
        report.writeText("Reference: checked-in lab parser; not production or independent holdout\nFixtures: ${fixtures.size}\nExact status+all-facts matches: $exact\nCandidate successes: $accepted\nCandidate successes with reference differences: $acceptanceDifferences\nField differences: $counts\nNot a cutover acceptance gate: rules not yet migrated, independent holdout and timing gates pending\n" + rows.joinToString("\n"))
        println(report.readLines().take(7).joinToString("\n"))
    }
}
