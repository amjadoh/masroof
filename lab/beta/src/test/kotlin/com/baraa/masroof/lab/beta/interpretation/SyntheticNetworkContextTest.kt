package com.baraa.masroof.lab.beta.interpretation

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.beta.application.interpretation.SharedRuleInterpretationAdapter
import com.baraa.masroof.lab.finance.value.BankId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

/** New synthetic contexts, not reviewed real SMS or an independent promotion holdout. */
class SyntheticNetworkContextTest {
    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val bank = BankId("LOCAL")
    private val catalog = BankCatalog(listOf(BankDefinition(bank, setOf("SYNTHETIC"))))
    private val schema = SharedRuleInterpretationAdapter.currentLabSchema().copy(network = NetworkRules(
        mapOf("LOCAL" to listOf("Orchid Bank", "مصرف زهر"), "EXTERNAL" to listOf("Cedar Bank"), "OTHER" to listOf("Maple Bank")),
        sourceBankLabels = listOf("Sender bank"), destinationBankLabels = listOf("Destination bank"), standaloneDestinationBrackets = true,
        networkLabels = listOf("Network")))
    private val incoming = "Incoming transfer\nFrom account: 1111\nTo account: 2222\nAmount: 67 SAR"
    private val outgoing = "Outgoing transfer\nFrom account: 1111\nTo account: 2222\nAmount: 67 SAR"
    private fun parse(body: String, rules: BankRuleSchema = schema) = SharedRuleInterpretationAdapter(rules, Clock.fixed(now, ZoneOffset.UTC))
        .interpret(MessageInput("synthetic", "SYNTHETIC", body, now), catalog)

    @Test fun roleBoundNamesKeepReferenceScopeWithoutAssigningOwnership() {
        val local = parse("$incoming\nSender bank: Orchid Bank")
        assertEquals(local.toString(), ParseStatus.SUCCESS, local.status)
        assertEquals(BankNetwork.INTRA_BANK, local.facts.bankNetwork)
        assertEquals(AccountReference(bank, "1111"), local.facts.sourceAccount)
        assertEquals(AccountReference(bank, "2222"), local.facts.destinationAccount)
        val external = parse("$outgoing\n[Cedar Bank]")
        assertEquals(ParseStatus.SUCCESS, external.status)
        assertEquals(BankNetwork.INTER_BANK, external.facts.bankNetwork)
        assertEquals(AccountReference(bank, "1111"), external.facts.sourceAccount)
        assertEquals(AccountReference(null, "2222"), external.facts.destinationAccount)
        assertEquals("67.00", external.facts.amount!!.amount.toPlainString())
        assertTrue(external.evidence.any { it.field == "bankNetwork" && it.slice(MessageInput("synthetic", "SYNTHETIC", "$outgoing\n[Cedar Bank]", now)) == "Cedar Bank" })
    }

    @Test fun unknownAmbiguousAndConflictingNamesFailClosedWithObservedFacts() {
        listOf("Sender bank: Orchid Bank Extra", "Sender bank: Unknown Bank",
            "Sender bank: Orchid Bank\nSender bank: Cedar Bank", "Sender bank: Cedar Bank\nSender bank: Maple Bank").forEach {
            val result = parse("$incoming\n$it")
            assertEquals(it, ParseStatus.REVIEW_REQUIRED, result.status)
            assertEquals(BankNetwork.UNKNOWN, result.facts.bankNetwork)
            assertNull(result.facts.sourceAccount!!.bank)
            assertEquals("67.00", result.facts.amount!!.amount.toPlainString())
            assertTrue(Blocker.VALIDATION_ERROR in result.blockers)
        }
        val collision = schema.copy(network = schema.network.copy(bankAliases = schema.network.bankAliases + ("COLLISION" to listOf("Orchid Bank"))))
        assertNotEquals(ParseStatus.SUCCESS, parse("$incoming\nSender bank: Orchid Bank", collision).status)
        assertNotEquals(ParseStatus.SUCCESS, parse("$outgoing\n[Unknown Bank]").status)
    }

    @Test fun quotationsCannotSupplyNamesOrNetworkAndNegationNeverConfirmsInternal() {
        listOf("Note: '[Orchid Bank]'", "> Sender bank: Orchid Bank", "Sender bank: 'Orchid Bank'",
            "Quote:\nSender bank: Orchid Bank", "```\nSender bank: Orchid Bank\n```", "Remark: 'داخلية'").forEach {
            val result = parse("$incoming\n$it")
            assertEquals(it, BankNetwork.UNKNOWN, result.facts.bankNetwork)
            assertNull(result.facts.sourceAccount!!.bank)
        }
        val negated = parse("$incoming\nليست حوالة داخلية\nSender bank: Orchid Bank")
        assertEquals(ParseStatus.REVIEW_REQUIRED, negated.status)
        assertEquals(BankNetwork.UNKNOWN, negated.facts.bankNetwork)
        assertNull(negated.facts.sourceAccount!!.bank)
        listOf("المستفيد: شركة داخلية للتجارة", "المستفيد: شركة محلية", "Counterparty: داخلية").forEach {
            val result = parse("$outgoing\n$it")
            assertEquals(it, BankNetwork.UNKNOWN, result.facts.bankNetwork)
            assertNull(result.facts.destinationAccount!!.bank)
        }
    }

    @Test fun namedBankDoesNotLiftOtpOrUnexecutedGates() {
        val otp = parse("One Time Password: 987654\n$incoming\nSender bank: Orchid Bank")
        assertEquals(ParseStatus.NON_FINANCIAL, otp.status)
        assertEquals(MessageFamily.OTP, otp.facts.family)
        assertNull(otp.facts.bankNetwork)
        listOf("pending", "declined", "payment request", "قيد التنفيذ", "لم تتم").forEach {
            assertNotEquals(it, ParseStatus.SUCCESS, parse("$it\n$incoming\nSender bank: Orchid Bank").status)
        }
    }

    @Test fun adapterCapturesAnImmutableAliasSnapshot() {
        val names = mutableListOf("Orchid Bank")
        val aliases = mutableMapOf("LOCAL" to names)
        val captured = SharedRuleInterpretationAdapter(schema.copy(network = schema.network.copy(bankAliases = aliases)), Clock.fixed(now, ZoneOffset.UTC))
        names.clear(); aliases.clear()
        val result = captured.interpret(MessageInput("snapshot", "SYNTHETIC", "$incoming\nSender bank: Orchid Bank", now), catalog)
        assertEquals(ParseStatus.SUCCESS, result.status)
        assertEquals(BankNetwork.INTRA_BANK, result.facts.bankNetwork)
    }

    @Test fun aliasEvidenceKeepsOriginalUtf16RangesAcrossEmojiAndArabicMarks() {
        val body = "$incoming\nNote: 😀\nSender bank: مَصرف زَهر"
        val source = MessageInput("original-evidence", "SYNTHETIC", body, now)
        val result = SharedRuleInterpretationAdapter(schema, Clock.fixed(now, ZoneOffset.UTC)).interpret(source, catalog)
        assertEquals(ParseStatus.SUCCESS, result.status)
        assertEquals(BankNetwork.INTRA_BANK, result.facts.bankNetwork)
        assertTrue(result.evidence.any { it.field == "bankNetwork" && it.slice(source) == "مَصرف زَهر" })
        assertFalse(Blocker.EVIDENCE_MAPPING_ERROR in result.blockers)
    }
}
