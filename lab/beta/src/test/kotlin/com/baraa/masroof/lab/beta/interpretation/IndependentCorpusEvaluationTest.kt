package com.baraa.masroof.lab.beta.interpretation

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.bank.interpretation.RuleInterpreter
import com.baraa.masroof.lab.bank.rules.RuleLibrary
import com.baraa.masroof.lab.finance.value.BankId
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Known development regressions; frozen pre-M6 annotations, no legacy parser oracle. */
class IndependentCorpusEvaluationTest {
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    @Test fun frozenExternalToEngineFixturesReportCoverageAndRejectUnsafeSuccess() {
        val root = File("../../app/src/test/resources/testdata/bank_aljazira")
        val files = root.walkTopDown().filter { it.extension == "json" && it.parentFile?.name != "reference" }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }.toList()
        val manifest = files.joinToString("\n") { it.relativeTo(root).invariantSeparatorsPath + ":" + hash(it.readText().removePrefix("\uFEFF").replace("\r\n", "\n")) }
        assertEquals(40, files.size)
        assertEquals("37f77445edd69905393428bb1a4848d884ffddaea6c70d348fa40e4e6479a35b", hash(manifest))
        val interpreter = RuleInterpreter(com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService.currentSchema())
        val bank = BankId("BANK_ALJAZIRA")
        val counts = sortedMapOf<String, Int>()
        val unsafe = mutableListOf<String>()
        val statusMismatches = mutableListOf<String>()
        val rows = files.map { file ->
            val sample = Json.parseToJsonElement(file.readText()).jsonObject
            fun JsonObject.text(key: String) = this[key]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
            val input = MessageInput(sample.text("id")!!, sample.text("sender")!!, sample.text("body")!!, Instant.parse("2026-10-18T00:00:00Z"))
            val result = interpreter.interpret(input, BankCatalog(listOf(BankDefinition(bank, setOf(input.sender)))))
            val expected = sample.getValue("expected").jsonObject
            val f = result.facts
            val observed = mapOf("bank" to result.bank?.value, "messageFamily" to f.family.name,
                "direction" to f.direction?.name, "amount" to f.amount?.amount?.toPlainString(), "currency" to f.amount?.currency?.name,
                "cardLast4" to f.card?.last4, "sourceAccountLast4" to f.sourceAccount?.maskedNumber,
                "destinationAccountLast4" to f.destinationAccount?.maskedNumber, "merchant" to f.merchant,
                "counterparty" to f.counterparty, "occurredAt" to f.occurredAtLocal?.toString(),
                "transactionReference" to f.transactionReference, "salaryIncomeWording" to f.salaryIncomeWording?.toString(),
                "bankNetworkType" to f.bankNetwork?.name, "cardSmsChannel" to f.cardChannel?.name,
                "debitSourceAccountLast4" to f.debitSourceAccountLast4,
                "purchaseChannel" to f.purchaseChannel?.name, "availableBalance" to f.availableBalance?.amount?.toPlainString(),
                "outstandingBalance" to f.outstandingBalance?.amount?.toPlainString(), "biller" to f.biller,
                "billerCode" to f.billerCode, "paymentDueDate" to f.paymentDueDate?.toString(), "loanType" to f.loanType?.name)
            val differences = expected.filter { it.key != "parseStatus" && it.value != JsonNull }.mapNotNull { (key, value) ->
                val actual = observed[key]
                val target = value.jsonPrimitive.content
                val equal = when (key) {
                    "amount", "availableBalance", "outstandingBalance" -> actual?.toBigDecimalOrNull()?.compareTo(target.toBigDecimal()) == 0
                    "occurredAt" -> actual?.let { java.time.LocalDateTime.parse(it) == java.time.LocalDateTime.parse(target) } == true
                    else -> actual == target
                }
                if (equal) null else key
            }
            if (result.status == ParseStatus.SUCCESS) {
                val core = setOf("bank", "messageFamily", "direction", "amount", "currency", "cardLast4", "sourceAccountLast4", "destinationAccountLast4", "occurredAt")
                if (expected.text("parseStatus") != "SUCCESS" || differences.any { it in core || observed[it] != null }) unsafe += input.evidenceId
            }
            if (result.status.name != expected.text("parseStatus")) statusMismatches += input.evidenceId
            counts[result.status.name] = counts.getOrDefault(result.status.name, 0) + 1
            result.evidence.forEach { assertTrue(it.start >= 0 && it.end <= input.body.length && it.end > it.start) }
            "${input.evidenceId}: ${result.status}; differences=${differences.sorted()}; diagnostics=${result.diagnostics.map { it.code }}"
        }
        val report = File("build/reports/beta-interpretation/independent-corpus.txt")
        requireNotNull(report.parentFile).mkdirs()
        report.writeText("Source: frozen checked-in Production fixture annotations, no Production DB or parser reads\n" +
            "Manifest SHA256: ${hash(manifest)}\nEngine: ${interpreter.version}\nFixtures: ${files.size}\nStatuses: $counts\nUnsafe successes: $unsafe\n" +
            "Known development regression set, used for rule refinement in this increment. New held-out examples are evaluated separately; not a general accuracy estimate.\n" + rows.joinToString("\n"))
        println(report.readLines().take(7).joinToString("\n"))
        assertTrue("Unsafe successes: $unsafe", unsafe.isEmpty())
        assertTrue("Known status coverage gaps: $statusMismatches", statusMismatches.isEmpty())
    }
}
