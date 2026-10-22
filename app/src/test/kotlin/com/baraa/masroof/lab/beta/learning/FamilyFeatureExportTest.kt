package com.baraa.masroof.lab.beta.learning

import com.baraa.masroof.lab.ml.family.FamilyInputCleaner
import com.baraa.masroof.application.interpretation.LabFamilyAssist
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exports the frozen human-labelled training corpus; parser regression fixtures do not enroll themselves. */
class FamilyFeatureExportTest {
    @Test fun exportVerifiedCorpusFeatures() {
        val canonical = File("src/test/resources/testdata/bank_aljazira")
        val reported = File("src/test/resources/testdata/engine/reported")
        val approvedIds = Json.parseToJsonElement(File("../ml-assist/tools/corpus-splits.json").readText())
            .jsonObject.getValue("partitions").jsonObject.keys
        assertTrue("The reviewed training corpus must not be empty", approvedIds.isNotEmpty())
        val exportedIds = mutableSetOf<String>()
        val rows = mutableListOf<JsonObject>()
        val rejected = mutableListOf<JsonObject>()
        fun add(id: String, body: String, family: String) {
            if (id !in approvedIds) return
            assertTrue("Duplicate approved fixture: $id", exportedIds.add(id))
            assertNotEquals("Approved fixture needs an explicit human family: $id", "UNKNOWN", family)
            val cleaned = LabFamilyAssist.clean(body)
            val features = cleaned.features
            if (features == null) {
                rejected += buildJsonObject {put("id",id);put("reason",cleaned.rejection!!.name)}
                return
            }
            rows += buildJsonObject {
                put("id",id);put("family",family);put("title",features.title);put("template",features.template)
                put("features",buildJsonObject { features.terms.forEach { (key,value) -> put(key,value) } })
            }
        }
        (canonical.walkTopDown()+reported.walkTopDown()).filter {it.extension=="json" && !it.path.contains("/supported/") && !it.path.contains("/device-reported/")}.sortedBy {it.path}.forEach { file ->
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            val expected = json["expected"]?.jsonObject
            if (expected!=null) add(json.getValue("id").jsonPrimitive.content,json.getValue("body").jsonPrimitive.content,expected.getValue("messageFamily").jsonPrimitive.content)
        }
        val reference = Json.parseToJsonElement(File(canonical,"reference/jazira_sms_bodies.json").readText()).jsonObject.getValue("messages").jsonArray
        val families = listOf("TRANSFER_OUT","TRANSFER_IN","TRANSFER_IN","PURCHASE","PURCHASE","TRANSFER_OUT","PURCHASE","TRANSFER_OUT","TRANSFER_IN","FINANCING_INSTALLMENT")
        reference.forEachIndexed { index,element -> val row=element.jsonObject; add(row.getValue("id").jsonPrimitive.content,row.getValue("body").jsonPrimitive.content,families[index]) }
        val output = buildJsonObject { put("cleaningVersion",FamilyInputCleaner.VERSION);put("featuresVersion",FamilyInputCleaner.FEATURES_VERSION);put("bankId","BANK_ALJAZIRA");put("rows",JsonArray(rows.sortedBy {it.getValue("id").jsonPrimitive.content}));put("rejected",JsonArray(rejected)) }
        File("build/model-training/features.json").apply {parentFile!!.mkdirs();writeText(output.toString())}
        assertEquals("Every reviewed training fixture must still exist", approvedIds, exportedIds)
        assertEquals(approvedIds.size,rows.size+rejected.size)
    }
}
