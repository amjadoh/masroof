package com.baraa.masroof.lab.replica.data.learning

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
        val cases = root.getValue("validation").jsonArray.map { val row=it.jsonObject; FamilyEvaluation(SmsFamilyLabel.valueOf(row.getValue("expected").jsonPrimitive.content),terms(row.getValue("features"))) }
        val manifest = ModelManifest("sms-family",model.version,root.getValue("cleaningVersion").jsonPrimitive.content,
            root.getValue("featuresVersion").jsonPrimitive.content,model.enabledFamilies,root.getValue("evaluationSource").jsonPrimitive.content)
        manifest.requireCompatible()
        return Seed(model,cases,manifest)
    }
    fun checked(seed: Seed, labels: List<HumanFamilyLabel>): FamilyClassifier {
        seed.manifest.requireCompatible()
        return FamilyClassifier.checked(seed.model,labels,seed.evaluation)
    }

    fun terms(element: JsonElement): Map<String,Int> = element.jsonObject.mapValues {it.value.jsonPrimitive.int}.also { map ->
        require(FamilyInputCleaner.validTerms(map))
    }
    fun termsJson(terms: Map<String,Int>) = buildJsonObject {terms.toSortedMap().forEach {(key,value)->put(key,value)}}
    fun labelJson(label: HumanFamilyLabel) = buildJsonObject {
        put("bank",label.bankId);put("raw",label.rawId);put("decision",label.decisionId);put("family",label.family.name)
        put("template",label.template);put("features",termsJson(label.terms));put("at",label.at);put("provenance",label.provenance)
    }
    fun label(element: JsonElement): HumanFamilyLabel {
        val row=element.jsonObject
        return HumanFamilyLabel(row.getValue("bank").jsonPrimitive.content,row.getValue("raw").jsonPrimitive.content,
            row.getValue("decision").jsonPrimitive.content,SmsFamilyLabel.valueOf(row.getValue("family").jsonPrimitive.content),
            row.getValue("template").jsonPrimitive.content,terms(row.getValue("features")),row.getValue("at").jsonPrimitive.long,row.getValue("provenance").jsonPrimitive.content)
    }
    fun cache(seed: Seed, classifier: FamilyClassifier): String {
        val latest = classifier.latestLabels
        val payload = buildJsonObject {
            put("schema",2);put("cleaningVersion",FamilyInputCleaner.VERSION);put("featuresVersion",FamilyInputCleaner.FEATURES_VERSION);put("seedVersion",seed.model.version);put("modelVersion",classifier.version)
            put("labels",JsonArray(latest.sortedBy {it.rawId}.map(::labelJson)))
            put("documents",buildJsonObject {classifier.model.documents.toSortedMap(compareBy {it.name}).forEach {(family,count)->put(family.name,count)}})
        }
        return buildJsonObject {put("checksum",FamilyInputCleaner.sha256(payload.toString()));put("payload",payload)}.toString()
    }
    fun cachedLabels(text: String, seed: Seed): List<HumanFamilyLabel> {
        require(text.length<=16*1024*1024)
        val root=Json.parseToJsonElement(text).jsonObject;val payload=root.getValue("payload").jsonObject
        require(root.getValue("checksum").jsonPrimitive.content==FamilyInputCleaner.sha256(payload.toString()))
        require(payload.getValue("cleaningVersion").jsonPrimitive.content==FamilyInputCleaner.VERSION)
        require(payload.getValue("schema").jsonPrimitive.int==2 && payload.getValue("seedVersion").jsonPrimitive.content==seed.model.version && payload.getValue("featuresVersion").jsonPrimitive.content==FamilyInputCleaner.FEATURES_VERSION)
        return payload.getValue("labels").jsonArray.map(::label).also { require(checked(seed,it).version==payload.getValue("modelVersion").jsonPrimitive.content) }
    }
}
