package com.baraa.masroof.lab.beta.learning

import com.baraa.masroof.lab.ml.family.FamilyInputCleaner
import com.baraa.masroof.lab.replica.application.interpretation.LabFamilyAssist
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exports Kotlin-normalized human fixture labels, not parser/model predictions. */
class FamilyFeatureExportTest {
    @Test fun exportVerifiedCorpusFeatures() {
        val canonical = File("../../app/src/test/resources/testdata/bank_aljazira")
        val reported = File("testdata/reported")
        val rows = mutableListOf<JsonObject>()
        val rejected = mutableListOf<JsonObject>()
        fun add(id: String, body: String, family: String) {
            if (family=="UNKNOWN") return
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
        (canonical.walkTopDown()+reported.walkTopDown()).filter {it.extension=="json"}.sortedBy {it.path}.forEach { file ->
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            val expected = json["expected"]?.jsonObject
            if (expected!=null) add(json.getValue("id").jsonPrimitive.content,json.getValue("body").jsonPrimitive.content,expected.getValue("messageFamily").jsonPrimitive.content)
        }
        val reference = Json.parseToJsonElement(File(canonical,"reference/jazira_sms_bodies.json").readText()).jsonObject.getValue("messages").jsonArray
        val families = listOf("TRANSFER_OUT","TRANSFER_IN","TRANSFER_IN","PURCHASE","PURCHASE","TRANSFER_OUT","PURCHASE","TRANSFER_OUT","TRANSFER_IN","FINANCING_INSTALLMENT")
        reference.forEachIndexed { index,element -> val row=element.jsonObject; add(row.getValue("id").jsonPrimitive.content,row.getValue("body").jsonPrimitive.content,families[index]) }
        val output = buildJsonObject { put("cleaningVersion",FamilyInputCleaner.VERSION);put("featuresVersion",FamilyInputCleaner.FEATURES_VERSION);put("bankId","BANK_ALJAZIRA");put("rows",JsonArray(rows.sortedBy {it.getValue("id").jsonPrimitive.content}));put("rejected",JsonArray(rejected)) }
        File("build/model-training/features.json").apply {parentFile!!.mkdirs();writeText(output.toString())}
        assertEquals(50,rows.size+rejected.size)
    }
}
