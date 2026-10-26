package com.baraa.masroof.lab.bank.experiment

import com.baraa.masroof.lab.bank.patterns.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Private inputs and reports stay outside Git. Without them the same probe runs on synthetic fixtures. */
class ExampleTemplateProbeTest {
    @Test fun annotatedExamplesProducePreviewsAndAbstainOnStructuralNegatives() {
        val privatePath = System.getenv("MASROOF_PATTERN_INPUT")
        val examples = teachingExamples(privatePath)
        assertEquals(5, examples.size)
        var changedValueChecks = 0
        var negativeChecks = 0
        val rows = examples.mapIndexed { index, example ->
            val template = example.learn()
            val original = template.preview("STC Bank", example.body)
            assertTrue("example ${index + 1} did not reproduce its human selections", original != null &&
                original.fields.mapValues { it.value.raw } == example.selections.associate {
                    it.field to example.body.substring(it.start, it.end)
                })
            assertFalse(requireNotNull(original).financialPostingAllowed)
            for (variant in 1..2) {
                val (body, expected) = example.variant(variant)
                val result = template.preview("STC Bank", body)
                assertTrue("example ${index + 1}, synthetic probe $variant failed", result != null &&
                    result.fields.mapValues { it.value.raw } == expected && !result.financialPostingAllowed)
                changedValueChecks++
            }
            val amount = example.selections.single { it.field == "amount" }
            val before = example.body.lastIndexOf('\n', amount.start).let { if (it < 0) 0 else it + 1 }
            val after = example.body.indexOf('\n', amount.end).let { if (it < 0) example.body.length else it + 1 }
            val negatives = listOf(
                "STC Bank" to example.body.replaceFirst("\n", " pending\n"),
                "STC Bank" to example.body + "\nOTP: 123456",
                "Different sender" to example.body,
                "STC Bank" to example.body.removeRange(before, after),
                "STC Bank" to example.replace("amount", "1.234 SAR"),
            )
            negatives.forEachIndexed { number, (sender, body) ->
                assertTrue("example ${index + 1}, synthetic negative $number was accepted", template.preview(sender, body) == null)
                negativeChecks++
            }
            buildJsonObject {
                put("example", index + 1)
                put("humanLabel", example.label)
                put("status", "PREVIEW_ONLY")
                put("financialPostingAllowed", false)
                putJsonObject("fields") {
                    original.fields.forEach { (field, extracted) ->
                        putJsonObject(field) {
                            put("raw", extracted.raw)
                            put("start", extracted.start)
                            put("end", extracted.end)
                        }
                    }
                }
            }
        }
        val report = buildJsonObject {
            put("source", if (privatePath == null) "SYNTHETIC" else "USER_SUPPLIED_PRIVATE")
            put("trainingExamplesReproduced", examples.size)
            put("syntheticChangedValueChecks", changedValueChecks)
            put("syntheticNegativeChecks", negativeChecks)
            put("independentRealHoldouts", 0)
            put("financialWrites", 0)
            put("productionIntegrationTested", false)
            put("previews", JsonArray(rows))
        }
        System.getenv("MASROOF_PATTERN_REPORT")?.let { path ->
            require(privatePath != null) { "Report output requires explicit private input" }
            File(path).writeText(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), report))
        }
    }

    @Test fun frozenTemplatesEvaluateHeldOutMessagesWithoutLearningFromThem() {
        val teachers = teachingExamples(System.getenv("MASROOF_PATTERN_INPUT"))
        val templates = teachers.map { it.learn() }
        val privateHoldouts = System.getenv("MASROOF_PATTERN_HOLDOUT")
        val (syntheticBody, syntheticFields) = teachers.first().variant(1)
        val cases = heldOutCases(privateHoldouts, listOf(
            HeldOutCase("synthetic-known", "STC Bank", syntheticBody, teachers.first().label, syntheticFields),
            HeldOutCase("synthetic-unseen", "STC Bank", "Previously unseen transfer\nAmount:17SR", null, null),
        ))
        // Only original teachers reach learn(). Neither expected fields nor held-out text can alter a template.
        assertTrue(cases.none { sample -> teachers.any { it.body == sample.body } })
        var matched = 0
        var abstained = 0
        var mismatches = 0
        val rows = cases.map { sample ->
            val matches = templates.mapNotNull { it.preview(sample.sender, sample.body) }
            val preview = ExampleTemplate.previewUnique(templates, sample.sender, sample.body)
            if (preview == null) abstained++
            val correct = if (sample.expectedFields == null) matches.isEmpty() else preview != null &&
                preview.label == sample.expectedLabel && preview.fields.mapValues { it.value.raw } == sample.expectedFields &&
                preview.fields.values.all { it.raw == sample.body.substring(it.start, it.end) }
            if (!correct) mismatches++
            if (correct && preview != null) matched++
            assertFalse(preview?.financialPostingAllowed ?: false)
            buildJsonObject {
                put("example", sample.id)
                put("status", when (matches.size) {
                    0 -> "ABSTAINED_NO_MATCH"
                    1 -> "PREVIEW_ONLY"
                    else -> "ABSTAINED_AMBIGUOUS"
                })
                put("matchesExpected", correct)
                put("financialPostingAllowed", false)
                putJsonObject("fields") { preview?.fields?.forEach { (field, value) -> put(field, value.raw) } }
            }
        }
        val report = buildJsonObject {
            put("source", if (privateHoldouts == null) "SYNTHETIC" else "USER_SUPPLIED_REAL_HOLDOUTS")
            put("frozenTeachingExamples", teachers.size)
            put("heldOutMessages", cases.size)
            put("matchedWithVerifiedFields", matched)
            put("abstained", abstained)
            put("mismatches", mismatches)
            put("financialWrites", 0)
            put("productionIntegrationTested", false)
            put("results", JsonArray(rows))
        }
        System.getenv("MASROOF_PATTERN_HOLDOUT_REPORT")?.let { path ->
            require(privateHoldouts != null && System.getenv("MASROOF_PATTERN_INPUT") != null)
            File(path).writeText(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), report))
        }
        assertEquals("Held-out mismatch count; inspect private report without logging SMS data", 0, mismatches)
    }

    @Test fun oneAdditionalExampleHandlesAnUnseenLayoutWithoutLearningTheHoldout() {
        val originalTeachers = teachingExamples(System.getenv("MASROOF_PATTERN_INPUT"))
        val originalTemplates = originalTeachers.map { it.learn() }
        val privateTeacher = System.getenv("MASROOF_PATTERN_NEW_TEACHER")
        val teacher = if (privateTeacher == null) markedExample("TRANSFER_IN",
            "Previously unseen incoming transfer\n[[amount|MONEY|33.00 SAR]]\nReference:[[reference|TEXT|*AB12]]")
            else teachingExamples(privateTeacher).single()
        val (syntheticBody, syntheticFields) = teacher.variant(1)
        val privateHoldout = System.getenv("MASROOF_PATTERN_NEW_HOLDOUT")
        val cases = heldOutCases(privateHoldout, listOf(
            HeldOutCase("synthetic-new-layout", "STC Bank", syntheticBody, teacher.label, syntheticFields),
        ))
        assertNull(ExampleTemplate.previewUnique(originalTemplates, "STC Bank", teacher.body))
        assertTrue(cases.none { sample -> (originalTeachers + teacher).any { it.body == sample.body } })
        val extendedTemplates = originalTemplates + teacher.learn()
        val reproduced = requireNotNull(ExampleTemplate.previewUnique(extendedTemplates, "STC Bank", teacher.body))
        assertTrue("Additional teacher fields do not reproduce", reproduced.fields.mapValues { it.value.raw } ==
            teacher.selections.associate { it.field to teacher.body.substring(it.start, it.end) })
        val rows = cases.map { sample ->
            assertNull(ExampleTemplate.previewUnique(originalTemplates, sample.sender, sample.body))
            val result = ExampleTemplate.previewUnique(extendedTemplates, sample.sender, sample.body)
            assertTrue("Additional layout held-out fields differ; no SMS values logged", result != null &&
                result.label == sample.expectedLabel && result.fields.mapValues { it.value.raw } == sample.expectedFields &&
                result.fields.values.all { it.raw == sample.body.substring(it.start, it.end) })
            assertFalse(requireNotNull(result).financialPostingAllowed)
            buildJsonObject {
                put("example", sample.id)
                put("beforeTeaching", "ABSTAINED_NO_MATCH")
                put("afterTeaching", "PREVIEW_ONLY")
                put("matchesExpected", true)
                putJsonObject("fields") { result.fields.forEach { (field, value) -> put(field, value.raw) } }
            }
        }
        // Additional learning must retain all original templates and their meaning.
        originalTeachers.forEach { original ->
            assertEquals(original.learn().preview("STC Bank", original.body),
                ExampleTemplate.previewUnique(extendedTemplates, "STC Bank", original.body))
        }
        val report = buildJsonObject {
            put("source", if (privateHoldout == null) "SYNTHETIC" else "USER_SUPPLIED_REAL_HOLDOUT")
            put("originalTeachingExamples", originalTeachers.size)
            put("additionalTeachingExamples", 1)
            put("heldOutMessages", cases.size)
            put("unmatchedBeforeTeaching", cases.size)
            put("matchedWithVerifiedFieldsAfterTeaching", cases.size)
            put("financialWrites", 0)
            put("productionIntegrationTested", false)
            put("results", JsonArray(rows))
        }
        System.getenv("MASROOF_PATTERN_NEW_REPORT")?.let { path ->
            require(privateTeacher != null && privateHoldout != null)
            File(path).writeText(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), report))
        }
    }

    private fun heldOutCases(path: String?, fallback: List<HeldOutCase>): List<HeldOutCase> =
        if (path == null) fallback else Json.parseToJsonElement(File(path).readText()).jsonArray.map { value ->
            val row = value.jsonObject
            val expected = row.getValue("expected").takeUnless { it is JsonNull }?.jsonObject
            HeldOutCase(row.getValue("id").jsonPrimitive.content, row.getValue("sender").jsonPrimitive.content,
                row.getValue("body").jsonPrimitive.content, expected?.getValue("label")?.jsonPrimitive?.content,
                expected?.getValue("fields")?.jsonObject?.mapValues { it.value.jsonPrimitive.content })
        }

    private fun teachingExamples(path: String?): List<TeachingExample> = if (path == null) syntheticExamples() else {
        Json.parseToJsonElement(File(path).readText()).jsonArray.map { value ->
            val row = value.jsonObject
            TeachingExample(row.getValue("label").jsonPrimitive.content, row.getValue("body").jsonPrimitive.content,
                row.getValue("selections").jsonArray.map { element ->
                    val selected = element.jsonObject
                    Selection(selected.getValue("field").jsonPrimitive.content,
                        FieldKind.valueOf(selected.getValue("kind").jsonPrimitive.content),
                        selected.getValue("start").jsonPrimitive.int, selected.getValue("end").jsonPrimitive.int)
                })
        }
    }
}

private data class HeldOutCase(val id: String, val sender: String, val body: String,
    val expectedLabel: String?, val expectedFields: Map<String, String>?)
