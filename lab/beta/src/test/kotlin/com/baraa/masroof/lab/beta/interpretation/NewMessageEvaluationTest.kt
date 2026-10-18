package com.baraa.masroof.lab.beta.interpretation

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.bank.interpretation.RuleInterpreter
import com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService
import com.baraa.masroof.lab.finance.value.BankId
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/** Expected labels reviewed from source text/original assertions before executing the new engine. */
class NewMessageEvaluationTest {
    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.content
    @Test fun sourceChallengeHasNoUnsafeAcceptance() = evaluate("m6-unseen-split.json", "new-source-challenge.txt")

    @Test fun finalHeldOutSnippetsKeepMissingTimeReviewable() = evaluate("m6-final-heldout-split.json", "final-heldout-snippets.txt")
    @Test fun finalHeldOutMessagesHaveNoUnsafeAcceptance() = evaluate("m6-final-complete-split.json", "final-heldout-messages.txt")

    private fun body(row: JsonObject): String {
        val source = File("../..", row.text("source")!!).readText().replace("\r\n", "\n")
        val raw = row.text("referenceId")?.let { id -> Json.parseToJsonElement(source).jsonObject.getValue("messages").jsonArray
            .single { it.jsonObject.text("id") == id }.jsonObject.text("body")!! } ?: Regex("\"\"\"(.*?)\"\"\"", RegexOption.DOT_MATCHES_ALL)
            .findAll(source).toList()[row.getValue("index").jsonPrimitive.int].groupValues[1]
        assertEquals("Source drift: ${row.text("function")}", row.text("sha256"), sha(raw))
        return if (row.text("referenceId") == null) raw.trimIndent() else raw
    }

    @Test fun sourceSplitsExcludeExactNormalizedDuplicatesOfKnownFixturesAndEachOther() {
        fun key(body: String) = body.replace(Regex("\\s+"), " ").trim()
        val seen = File("../../app/src/test/resources/testdata/bank_aljazira").walkTopDown()
            .filter { it.extension == "json" && it.parentFile?.name != "reference" }
            .map { key(Json.parseToJsonElement(it.readText().removePrefix("\uFEFF")).jsonObject.text("body")!!) }.toMutableSet()
        assertEquals(40, seen.size)
        listOf("m6-unseen-split.json", "m6-final-heldout-split.json", "m6-final-complete-split.json").forEach { resource ->
            val rows = Json.parseToJsonElement(requireNotNull(javaClass.classLoader!!.getResource(resource)).readText().removePrefix("\uFEFF")).jsonArray
            rows.forEach { row -> assertTrue("Duplicate source example: $resource ${row.jsonObject.text("function")}", seen.add(key(body(row.jsonObject)))) }
        }
    }

    private fun evaluate(resource: String, reportName: String) {
        val manifest = requireNotNull(javaClass.classLoader!!.getResource(resource)).readText().removePrefix("\uFEFF").replace("\r\n", "\n")
        val rows = Json.parseToJsonElement(manifest).jsonArray
        val engine = RuleInterpreter(BetaInterpretationService.currentSchema())
        val bank = BankId("BANK_ALJAZIRA")
        val counts = sortedMapOf<String, Int>()
        val unsafe = mutableListOf<String>()
        var recognized = 0
        var coreMatches = 0
        val reports = rows.map { element ->
            val row = element.jsonObject
            val body = body(row)
            val id = row.text("function")!! + ":" + (row.text("index") ?: row.text("referenceId"))
            val result = engine.interpret(MessageInput(id, "AlJazira", body, Instant.parse("2026-10-18T00:00:00Z")),
                BankCatalog(listOf(BankDefinition(bank, setOf("AlJazira")))))
            val truth = row.getValue("expected").jsonObject
            val family = truth.text("family")!!
            val informational = family in setOf("INFORMATIONAL", "SECURITY")
            val purposeMatches = if (informational) result.status == ParseStatus.NON_FINANCIAL && result.facts.family in setOf(MessageFamily.OTP, MessageFamily.NON_FINANCIAL, MessageFamily.BALANCE_NOTICE)
                else result.facts.family.name == family
            if (purposeMatches) recognized++
            val f = result.facts
            val values = mapOf("amount" to f.amount?.amount?.toPlainString(), "currency" to f.amount?.currency?.name,
                "source" to f.sourceAccount?.maskedNumber, "destination" to f.destinationAccount?.maskedNumber, "card" to f.card?.last4)
            val differences = truth.filterKeys { it != "family" }.keys.filter { key ->
                if (key == "amount") values[key]?.toBigDecimalOrNull()?.compareTo(truth.text(key)!!.toBigDecimal()) != 0
                else values[key] != truth.text(key)
            }
            if (purposeMatches && differences.isEmpty()) coreMatches++
            row.text("expectedStatus")?.let { assertEquals("Incomplete source snippet must stay reviewable: $id", it, result.status.name) }
            if (result.status == ParseStatus.SUCCESS && (informational || !purposeMatches || differences.isNotEmpty())) unsafe += id
            if (result.status == ParseStatus.NON_FINANCIAL && (!informational || result.facts.amount != null)) unsafe += id
            result.evidence.forEach { assertTrue(it.start >= 0 && it.end > it.start && it.end <= body.length) }
            counts[result.status.name] = counts.getOrDefault(result.status.name, 0) + 1
            "$id: ${result.status}, family=${f.family}; missing/different=$differences; diagnostics=${result.diagnostics.map { it.code }}"
        }
        val file = File("build/reports/beta-interpretation", reportName)
        requireNotNull(file.parentFile).mkdirs()
        file.writeText("Manifest: ${sha(manifest)}\nEngine: ${engine.version}\nSamples: ${rows.size}\nStatuses: $counts\nPurpose recognized: $recognized/${rows.size}\nCore labels matched: $coreMatches/${rows.size}\nUnsafe acceptance: $unsafe\n" +
            "Labels are source assertions/text review, not legacy/new parser predictions. Independent examples are not a fresh phone-message or template-isolated accuracy claim.\n" + reports.joinToString("\n"))
        println(file.readLines().take(8).joinToString("\n"))
        assertTrue("Unsafe acceptance: $unsafe", unsafe.isEmpty())
    }
}
