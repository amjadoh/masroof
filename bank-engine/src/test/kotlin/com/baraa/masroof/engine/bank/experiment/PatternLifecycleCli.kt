package com.baraa.masroof.engine.bank.experiment

import com.baraa.masroof.engine.bank.patterns.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Separate JVM entry points prove that read/match cannot depend on the writer's memory or teaching file. */
object PatternLifecycleCli {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val directory = Path.of(args[1])
        when (args[0]) {
            "teach" -> teach(directory)
            "verify" -> verify(directory)
            else -> error("Unknown lifecycle phase")
        }
    }

    private fun teach(directory: Path) {
        val rows = Json.parseToJsonElement(Files.readString(directory.resolve("teachers.json"))).jsonArray
        var bindings = 0
        val patterns = rows.map { value ->
            val row = value.jsonObject
            val choices = row.getValue("bindings").jsonArray.map { element ->
                val selection = element.jsonObject
                require(selection.keys.all { it in setOf("field", "start", "end", "ignored") }) { "Only human field choices are accepted; no types or templates" }
                bindings++
                FieldChoice(selection.text("field"),
                    selection.getValue("start").jsonPrimitive.int, selection.getValue("end").jsonPrimitive.int,
                    ignored = selection["ignored"]?.jsonPrimitive?.boolean ?: false)
            }
            SavedMessagePattern.fromChoices(row.text("id"), row.text("bankId"), row.text("sender"), row.text("label"), row.text("body"), choices)
        }
        val file = directory.resolve("patterns.json")
        PatternStore.save(file, patterns)
        write(directory.resolve("writer-report.json"), buildJsonObject {
            put("writerPid", ProcessHandle.current().pid()); put("patterns", patterns.size); put("bindings", bindings)
            put("definitionsSha256", sha(Files.readString(file)))
            put("automaticallyBuiltFromHumanBindings", true)
            put("primitiveTypesInferredFromSelections", true)
            putJsonArray("teachingBodyHashes") { rows.forEach { add(sha(it.jsonObject.text("body"))) } }
        })
        println("Lifecycle writer completed: ${patterns.size} automatically generated definitions; $bindings user bindings.")
    }

    private fun verify(directory: Path) {
        require(!Files.exists(directory.resolve("teachers.json"))) { "Teaching input must be removed before fresh-process verification" }
        val writer = Json.parseToJsonElement(Files.readString(directory.resolve("writer-report.json"))).jsonObject
        require(writer.getValue("writerPid").jsonPrimitive.long != ProcessHandle.current().pid())
        val file = directory.resolve("patterns.json")
        require(sha(Files.readString(file)) == writer.text("definitionsSha256"))
        val patterns = PatternStore.load(file)
        val trainingHashes = writer.getValue("teachingBodyHashes").jsonArray.map { it.jsonPrimitive.content }.toSet()
        val cases = Json.parseToJsonElement(Files.readString(directory.resolve("cases.json"))).jsonArray
        var negatives = 0
        var fields = 0
        val results = cases.mapIndexed { index, value ->
            val row = value.jsonObject
            val body = row.text("body"); val bank = row.text("bankId"); val sender = row.text("sender")
            require(sha(body) !in trainingHashes) { "A held-out case duplicates a teaching example" }
            val match = SavedMessagePattern.unique(patterns, bank, sender, body)
            requireNotNull(match) { "No unique pattern for held-out case ${index + 1}" }
            val expected = row.getValue("expected").jsonObject
            require(match.second.label == expected.text("label")) { "Wrong label for held-out case ${index + 1}" }
            val expectedFields = expected.getValue("fields").jsonObject.mapValues { it.value.jsonPrimitive.content }
            require(match.second.fields.mapValues { it.value.raw } == expectedFields) { "Wrong field map for held-out case ${index + 1}" }
            require(match.second.fields.values.all { it.raw == body.substring(it.start, it.end) })
            require(!match.second.financialPostingAllowed)
            fields += expectedFields.size
            listOf(Triple("OTHER_SCOPE", sender, body), Triple(bank, "OTHER_SENDER", body), Triple(bank, sender, "OTP\n$body")).forEach { (b, s, text) ->
                require(SavedMessagePattern.unique(patterns, b, s, text) == null) { "Negative control accepted" }
                negatives++
            }
            buildJsonObject {
                put("case", index + 1); put("source", row.text("source")); put("patternId", match.first.id)
                put("revision", match.first.revision); put("label", match.second.label)
                putJsonObject("fields") { match.second.fields.forEach { (key, value) -> put(key, value.raw) } }
            }
        }
        write(directory.resolve("reader-report.json"), buildJsonObject {
            put("writerPid", writer.getValue("writerPid")); put("readerPid", ProcessHandle.current().pid())
            put("freshProcess", true); put("teachingInputAbsent", true); put("patternsLoaded", patterns.size)
            put("casesVerified", cases.size); put("fieldsVerified", fields); put("negativeControlsRejected", negatives)
            put("financialWrites", 0); put("userBindingStepSimulated", true); put("productionIntegrationTested", false)
            put("results", JsonArray(results))
        })
        println("Lifecycle reader completed in a fresh JVM: ${cases.size} held-out cases, $fields fields, $negatives rejected controls.")
    }

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun write(path: Path, value: JsonElement) = Files.writeString(path, Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), value))
}
