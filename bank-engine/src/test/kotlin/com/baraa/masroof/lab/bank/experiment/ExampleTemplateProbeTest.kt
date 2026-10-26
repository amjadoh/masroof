package com.baraa.masroof.lab.bank.experiment

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Private inputs and reports stay outside Git. Without them the same probe runs on synthetic fixtures. */
class ExampleTemplateProbeTest {
    @Test fun annotatedExamplesProducePreviewsAndAbstainOnStructuralNegatives() {
        val privatePath = System.getenv("MASROOF_PATTERN_INPUT")
        val examples = if (privatePath == null) syntheticExamples() else {
            Json.parseToJsonElement(File(privatePath).readText()).jsonArray.map { value ->
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
}
