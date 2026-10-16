package com.baraa.masroof.lab.engine

import com.baraa.masroof.lab.copy.core.text.ArabicTextFolding
import org.junit.Assert.*
import org.junit.Test

/** Authored synthetic adversarial contexts; NOT independently reviewed real bank holdout data. */
class SharedNetworkResolverTest {
    private val config = NetworkRuleConfig(
        mapOf("LOCAL" to listOf("Orchid Bank", "مصرف زهر"), "CEDAR" to listOf("Cedar Bank", "مصرف أرز"),
            "CEDAR_EU" to listOf("Cedar Bank Europe", "مصرف سرو")),
        sourceBankLabels = listOf("Sender bank", "البنك المرسل"),
        destinationBankLabels = listOf("Destination bank", "البنك المستقبل"), standaloneDestinationBrackets = true,
        networkLabels = listOf("Network", "شبكة التحويل"))
    private fun resolve(body: String, incoming: Boolean = true, rules: NetworkRuleConfig = config, local: String = "LOCAL") =
        SharedNetworkResolver.resolve(ArabicTextFolding.foldForComparison(body), local, incoming, rules,
            listOf("internal transfer", "داخلية"), listOf("external transfer", "خارجية"),
            listOf("internal transfer", "external transfer", "incoming transfer", "outgoing transfer", "حوالة صادرة", "حوالة واردة"))

    @Test fun exactNamesDetermineNetworkWithoutInferringOwnership() {
        assertEquals("INTRA_BANK", resolve("Sender bank: Orchid Bank").network)
        assertEquals("INTRA_BANK", resolve("البنك المرسل: مصرف زهر").network)
        assertEquals("INTER_BANK", resolve("Sender bank: Cedar Bank").network)
        assertEquals("INTER_BANK", resolve("[Cedar Bank Europe]", incoming = false).network)
        assertEquals("CEDAR_EU", resolve("[Cedar Bank Europe]", incoming = false).evidence.single().identity)
        assertEquals("UNKNOWN", resolve("[Cedar Bank]", incoming = true).network)
        assertEquals("UNKNOWN", resolve("Sender bank: Cedar Bank", incoming = false).network)
        assertEquals("INTER_BANK", resolve("Destination bank: Cedar Bank", incoming = false).network)
    }

    @Test fun aliasOverlapAndConflictingNamedBanksNeverUseOrder() {
        val overlapping = config.copy(bankAliases = config.bankAliases + ("COLLISION" to listOf("Orchid Bank")))
        listOf(overlapping, overlapping.copy(bankAliases = overlapping.bankAliases.entries.reversed().associate { it.toPair() })).forEach {
            val result = resolve("Sender bank: Orchid Bank", rules = it)
            assertEquals("UNKNOWN", result.network)
            assertTrue("NETWORK_AMBIGUOUS_BANK_ALIAS" in result.errors)
        }
        listOf("Sender bank: Orchid Bank\nSender bank: Cedar Bank", "Sender bank: Cedar Bank\nSender bank: Cedar Bank Europe").forEach {
            val result = resolve(it)
            assertEquals("UNKNOWN", result.network)
            assertTrue("NETWORK_CONFLICTING_BANK_NAMES" in result.errors)
        }
        assertEquals("UNKNOWN", resolve("Sender bank: Cedar Bank\nNetwork: Internal transfer").network)
        assertEquals("UNKNOWN", resolve("Sender bank: Orchid Bank\nNetwork: External transfer").network)
    }

    @Test fun unknownAndSubstringNamesAreNotRecognizedAsExternalOrLocal() {
        listOf("Orchid Bank Extra", "New Orchid Bank", "Cedar", "Cedar Bank Europe Branch", "Unknown Bank").forEach {
            val result = resolve("Sender bank: $it")
            assertEquals(it, "UNKNOWN", result.network)
            assertTrue("NETWORK_UNKNOWN_BANK_NAME" in result.errors)
        }
        val missingLocal = resolve("Sender bank: Cedar Bank", local = "UNCONFIGURED")
        assertEquals("UNKNOWN", missingLocal.network)
        assertTrue("NETWORK_LOCAL_IDENTITY_UNCONFIGURED" in missingLocal.errors)
    }

    @Test fun quotesAndUnrelatedMentionsDoNotBecomeNetworkAuthority() {
        listOf(
            "> Sender bank: Orchid Bank", "Note: Sender bank: Orchid Bank", "Merchant: [Cedar Bank]",
            "\"Sender bank: Orchid Bank\"", "Sender bank: 'Orchid Bank'", "Remark: 'internal transfer'",
            "Remark: '[Cedar Bank]'", "```\nSender bank: Orchid Bank\n```", "Quote:\nSender bank: Orchid Bank",
            "'quoted block\nSender bank: Orchid Bank\nend quote'",
            "Outgoing transfer\nالمستفيد: شركة داخلية للتجارة", "Outgoing transfer\nالمستفيد: شركة محلية",
            "Outgoing transfer\nMerchant: internal transfer", "Outgoing transfer\nRecipient: external transfer",
        ).forEach { assertEquals(it, "UNKNOWN", resolve(it).network) }
        val afterQuote = resolve("'quoted block\nSender bank: Cedar Bank\nend quote'\nSender bank: Orchid Bank")
        assertEquals("INTRA_BANK", afterQuote.network)
    }

    @Test fun negatedObservationsBlockRatherThanOverrideExplicitBankEvidence() {
        listOf("not an internal transfer", "ليست حوالة داخلية", "no external transfer").forEach {
            val result = resolve(it)
            assertEquals(it, "UNKNOWN", result.network)
            assertTrue(it, "NETWORK_NEGATED_OBSERVATION" in result.errors)
            assertEquals("UNKNOWN", resolve("Sender bank: Orchid Bank\n$it").network)
        }
    }

    @Test fun evidencePointsToExactOriginalComparisonRangesAndLiteralNamesCannotExecute() {
        val body = "  Sender bank: Orchid Bank\nSender bank: Orchid Bank"
        val result = resolve(body)
        assertEquals("INTRA_BANK", result.network)
        assertEquals(2, result.evidence.size)
        result.evidence.forEach { assertEquals("orchid bank", body.lowercase().substring(it.range)) }
        val literal = config.copy(bankAliases = mapOf("LOCAL" to listOf("(a+)+$")))
        assertEquals("INTRA_BANK", resolve("Sender bank: (a+)+$", rules = literal).network)
        assertEquals("UNKNOWN", resolve("Sender bank: " + "a".repeat(80) + "!", rules = literal).network)
    }

    @Test fun aliasesAndRoleLabelsAreBounded() {
        listOf(config.copy(bankAliases = mapOf("bad id" to listOf("Bank"))),
            config.copy(bankAliases = mapOf("LOCAL" to emptyList())),
            config.copy(bankAliases = mapOf("LOCAL" to listOf("x".repeat(101)))),
            config.copy(sourceBankLabels = listOf("sender\nbank")),
            config.copy(destinationBankLabels = List(21) { "label$it" })).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validate() }
        }
    }
}
