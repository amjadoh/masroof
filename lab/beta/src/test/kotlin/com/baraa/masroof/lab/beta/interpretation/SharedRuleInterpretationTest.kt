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

    @Test fun cardChannelRequiresEvidenceAndDebitMarkersWin() {
        val base = "POS Purchase\nCard: 1234\nAmount: 42 SAR"
        assertNull(parse(base).facts.cardChannel)
        assertNull(parse("$base\nالرصيد: 100 SAR").facts.cardChannel)
        assertNull(parse("$base\nAvailable balance unavailable\nBalance: 100 SAR").facts.cardChannel)
        assertEquals(CardChannel.CREDIT, parse("$base\nAvailable balance: 100 SAR").facts.cardChannel)
        assertEquals(CardChannel.DEBIT, parse("$base\nCredit card: 1234\nخصمت من حساب: 3001\nAvailable balance: 100 SAR").facts.cardChannel)
        assertEquals(CardChannel.DEBIT, parse("$base\nبطاقة مدى: 1234\nAvailable balance: 100 SAR").facts.cardChannel)
        assertEquals(CardChannel.DEBIT, parse("$base\nكشف حساب\nخصمت من حساب: 3001\nDue amount: 100 SAR\nتاريخ الاستحقاق: 2026-08-25").facts.cardChannel)
    }

    @Test fun foreignAmountNeedsItsOwnLabelAndCannotReplaceThePrincipal() {
        val noLabel = parse("POS Purchase\nCard: 1234\nAmount: 42 USD")
        assertNull(noLabel.facts.labeledForeignAmount)
        val separate = parse("POS Purchase\nCard: 1234\nAmount: 42 SAR\nForeign amount: 10 USD")
        assertEquals(separate.toString(), ParseStatus.SUCCESS, separate.status)
        assertEquals("42.00", separate.facts.amount!!.amount.toPlainString())
        assertEquals(Currency.SAR, separate.facts.amount!!.currency)
        assertEquals("10.00", separate.facts.labeledForeignAmount!!.amount.toPlainString())
        assertEquals(Currency.USD, separate.facts.labeledForeignAmount!!.currency)
        assertTrue(separate.evidence.any { it.field == "foreignAmount" })
        assertNotEquals(ParseStatus.SUCCESS, parse("POS Purchase\nCard: 1234\nAmount: 42 SAR\nForeign amount: 10 USD\nForeign amount: 11 USD").status)
    }

    @Test fun configuredObservationsAndLabelsWorkAcrossBanksWithoutParserBranches() {
        val alternate = BankCatalog(listOf(BankDefinition(BankId("EXAMPLE_BANK"), setOf("AlJazira"))))
        val rule = BankRule("custom", MessageFamily.PURCHASE, listOf("settled spend"),
            labels = mapOf(ExtractionField.AMOUNT to listOf("paid"), ExtractionField.CARD to listOf("token")),
            observations = mapOf("purchase.online" to listOf("web channel"), "card.debit" to listOf("debit product")))
        val result = SharedRuleInterpretationAdapter(BankRuleSchema(revision = 7, rules = listOf(rule)), clock)
            .interpret(input("settled spend\nweb channel\ndebit product\ntoken: 1234\npaid: 42 SAR"), alternate)
        assertEquals(ParseStatus.SUCCESS, result.status)
        assertEquals(PurchaseChannel.ONLINE, result.facts.purchaseChannel)
        assertEquals(CardChannel.DEBIT, result.facts.cardChannel)
        assertEquals(BankId("EXAMPLE_BANK"), result.facts.card!!.bank)
        assertTrue(result.evidence.any { it.field == "purchase.online" })
    }

    @Test fun conflictsAndInvalidDatesRemainBlockedWithoutInventingTimeOrOwnership() {
        listOf(
            "POS Purchase Online Purchase\nCard: 1234\nAmount: 42 SAR",
            "حوالة صادرة داخلية محلية\nمن حساب: 3001\nإلى حساب: 3002\nبمبلغ: 42 SAR",
            "POS Purchase\nCard: 1234\nAmount: 42 SAR\nفي: 14:32 30-02-2026",
            "POS Purchase\nCard: 1234\nAmount: 42 SAR\nفي: 14:32 03-08-2026\nDate: 2026-08-04 14:32",
        ).forEach { assertNotEquals(it, ParseStatus.SUCCESS, parse(it).status) }
        val parsed = parse("POS Purchase\nCard: 1234\nAmount: 42 SAR\nفي: 14:32 03-08-2026")
        assertEquals(java.time.LocalDateTime.parse("2026-08-03T14:32"), parsed.facts.occurredAtLocal)
        assertNull(parsed.facts.occurredAt)
    }

    @Test fun bankScopeNeedsExplicitIntraEvidenceAndNeverInfersFromTheTitle() {
        val outgoing = "حوالة مالية صادرة\nمن حساب: 3001\nإلى حساب: 3002\nبمبلغ: 42 SAR"
        val incoming = "حوالة واردة\nرقم حساب المرسل: 3001\nإلى حساب: 3002\nبمبلغ: 42 SAR"
        assertNull(parse(outgoing).facts.destinationAccount!!.bank)
        assertNull(parse(incoming).facts.sourceAccount!!.bank)
        assertEquals(BankNetwork.UNKNOWN, parse(outgoing).facts.bankNetwork)
        val explicit = parse("حوالة صادرة داخلية\nمن حساب: 3001\nإلى حساب: 3002\nبمبلغ: 42 SAR")
        assertEquals(BankNetwork.INTRA_BANK, explicit.facts.bankNetwork)
        assertEquals(bank, explicit.facts.sourceAccount!!.bank)
        assertEquals(bank, explicit.facts.destinationAccount!!.bank)
    }

    @Test fun shadowCorpusProducesAnHonestFieldDiffReportWithoutChangingTheReference() {
        val service = HistoricalInterpretationService.sharedRules(clock)
        val fixtures = File("../engine/src/test/resources/testdata/bank_aljazira").walkTopDown()
            .filter { it.extension == "json" && it.parentFile.name != "reference" }.sortedBy { it.path }.toList()
        val counts = sortedMapOf<String, Int>()
        // Preserve the correctly observed ISO date as an explicit additive
        // difference, not approval of financial equivalence or a discarded fact.
        val unported = mapOf(
            "collision_statement_amount_ar_001" to setOf("paymentDueDate"),
        )
        val violations = mutableListOf<String>()
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
            if (diffs.toSet() != unported[source.evidenceId].orEmpty()) violations += "${source.evidenceId}: $diffs"
            if (diffs.isEmpty()) exact++
            if (comparison.candidate.status == ParseStatus.SUCCESS) { accepted++; if (diffs.isNotEmpty()) acceptanceDifferences++ }
            source.evidenceId + ": " + if (diffs.isEmpty()) "equal" else diffs.joinToString(",")
        }
        assertTrue(fixtures.size >= 40)
        val report = File("build/reports/beta-interpretation/shared-shadow-diff.txt")
        report.parentFile.mkdirs()
        report.writeText("Reference: checked-in lab parser; not production or independent holdout\nFixtures: ${fixtures.size}\nExact status+all-facts matches: $exact\nCandidate successes: $accepted\nCandidate successes with reference differences: $acceptanceDifferences\nField differences: $counts\nEnforced gate: all Facts/status exact outside one named additive ISO due-date observation\nISO extension is not approved financial equivalence; independent reviewed real holdout and promotion gates pending\n" + rows.joinToString("\n"))
        println(report.readLines().take(7).joinToString("\n"))
        assertTrue("Unexpected field/status regression: $violations", violations.isEmpty())
    }
}
