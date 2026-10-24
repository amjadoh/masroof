package com.baraa.masroof.data.learning

import com.baraa.masroof.lab.ml.catalog.ModelManifest
import com.baraa.masroof.lab.ml.family.*
import kotlinx.serialization.json.*

/** Data-only codec. No model/configuration can supply code, monetary facts or ownership. */
object FamilyModelCodec {
    data class Seed(val model: FamilyModel,val evaluation: List<FamilyEvaluation>,val manifest: ModelManifest)
    fun seed(text: String): Seed {
        require(text.length<=4*1024*1024)
        val root = Json.parseToJsonElement(text).jsonObject
        require(root.getValue("schema").jsonPrimitive.int==2 && root.getValue("featuresVersion").jsonPrimitive.content==FamilyInputCleaner.FEATURES_VERSION)
        require(root.getValue("cleaningVersion").jsonPrimitive.content==FamilyInputCleaner.VERSION)
        val policy = root.getValue("policy").jsonObject
        val model = FamilyModel(root.getValue("bankId").jsonPrimitive.content,root.getValue("version").jsonPrimitive.content,
            root.getValue("vocabulary").jsonArray.map {it.jsonPrimitive.content}.toSet(),
            root.getValue("documents").jsonObject.mapKeys {SmsFamilyLabel.valueOf(it.key)}.mapValues {it.value.jsonPrimitive.long},
            root.getValue("counts").jsonObject.mapKeys {SmsFamilyLabel.valueOf(it.key)}.mapValues {(_,value)->value.jsonObject.mapValues {it.value.jsonPrimitive.long}},
            root.getValue("enabledFamilies").jsonArray.map {SmsFamilyLabel.valueOf(it.jsonPrimitive.content)}.toSet(),
            policy.getValue("scoreThreshold").jsonPrimitive.double,policy.getValue("marginThreshold").jsonPrimitive.double,policy.getValue("coverageThreshold").jsonPrimitive.double)
        val cases = root.getValue("validation").jsonArray.map { val row=it.jsonObject; FamilyEvaluation(SmsFamilyLabel.valueOf(row.getValue("expected").jsonPrimitive.content),terms(row.getValue("features")),
            row["runtimeEligible"]?.jsonPrimitive?.booleanOrNull ?: false, row["template"]?.jsonPrimitive?.contentOrNull,
            row["passesValidation"]?.jsonPrimitive?.booleanOrNull ?: false) }
        val manifest = ModelManifest("sms-family",model.version,root.getValue("cleaningVersion").jsonPrimitive.content,
            root.getValue("featuresVersion").jsonPrimitive.content,model.enabledFamilies,root.getValue("evaluationSource").jsonPrimitive.content)
        manifest.requireCompatible()
        return Seed(model,cases,manifest)
    }
    fun checked(seed: Seed, labels: List<HumanFamilyLabel>, blocks: List<HumanTemplateBlock> = emptyList()): FamilyClassifier {
        seed.manifest.requireCompatible()
        return FamilyClassifier.checked(seed.model,labels,seed.evaluation,blocks)
    }

    fun terms(element: JsonElement): Map<String,Int> = element.jsonObject.mapValues {it.value.jsonPrimitive.int}.also { map ->
        require(FamilyInputCleaner.validTerms(map))
    }
    fun termsJson(terms: Map<String,Int>) = buildJsonObject {terms.toSortedMap().forEach {(key,value)->put(key,value)}}
}
