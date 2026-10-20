package com.baraa.masroof.lab.replica.data.learning

import com.baraa.masroof.lab.bank.MessageFamily
import com.baraa.masroof.lab.bank.learning.*
import kotlinx.serialization.json.*

/** Data-only codec. No model/configuration can supply code, monetary facts or ownership. */
object FamilyModelCodec {
    data class Evaluation(val family: MessageFamily,val terms: Map<String,Int>)
    data class Seed(val model: FamilyModel,val evaluation: List<Evaluation>)
    fun seed(text: String): Seed {
        require(text.length<=4*1024*1024)
        val root = Json.parseToJsonElement(text).jsonObject
        require(root.getValue("schema").jsonPrimitive.int==1 && root.getValue("featuresVersion").jsonPrimitive.content==FamilyFeatures.VERSION)
        val policy = root.getValue("policy").jsonObject
        val model = FamilyModel(root.getValue("bankId").jsonPrimitive.content,root.getValue("version").jsonPrimitive.content,
            root.getValue("vocabulary").jsonArray.map {it.jsonPrimitive.content}.toSet(),
            root.getValue("documents").jsonObject.mapKeys {MessageFamily.valueOf(it.key)}.mapValues {it.value.jsonPrimitive.long},
            root.getValue("counts").jsonObject.mapKeys {MessageFamily.valueOf(it.key)}.mapValues {(_,value)->value.jsonObject.mapValues {it.value.jsonPrimitive.long}},
            root.getValue("enabledFamilies").jsonArray.map {MessageFamily.valueOf(it.jsonPrimitive.content)}.toSet(),
            policy.getValue("scoreThreshold").jsonPrimitive.double,policy.getValue("marginThreshold").jsonPrimitive.double,policy.getValue("coverageThreshold").jsonPrimitive.double)
        val cases = root.getValue("validation").jsonArray.map { val row=it.jsonObject; Evaluation(MessageFamily.valueOf(row.getValue("expected").jsonPrimitive.content),terms(row.getValue("features"))) }
        return Seed(model,cases)
    }
    fun checked(seed: Seed, labels: List<HumanFamilyLabel>): FamilyClassifier {
        val candidate = FamilyClassifier(seed.model,labels)
        val unsafe = seed.evaluation.any { test -> candidate.model.predict(test.terms)?.let { candidate.model.accepts(it) && it.family!=test.family }==true }
        return if (unsafe) FamilyClassifier(seed.model.copy(enabledFamilies=emptySet()),labels) else candidate
    }
    fun terms(element: JsonElement): Map<String,Int> = element.jsonObject.mapValues {it.value.jsonPrimitive.int}.also { map ->
        require(map.size<=2048 && map.all {it.key.length<=260 && it.value in 0..5})
    }
    fun termsJson(terms: Map<String,Int>) = buildJsonObject {terms.toSortedMap().forEach {(key,value)->put(key,value)}}
    fun labelJson(label: HumanFamilyLabel) = buildJsonObject {
        put("bank",label.bankId);put("raw",label.rawId);put("decision",label.decisionId);put("family",label.family.name)
        put("template",label.template);put("features",termsJson(label.terms));put("at",label.at);put("provenance",label.provenance)
    }
    fun label(element: JsonElement): HumanFamilyLabel {
        val row=element.jsonObject
        return HumanFamilyLabel(row.getValue("bank").jsonPrimitive.content,row.getValue("raw").jsonPrimitive.content,
            row.getValue("decision").jsonPrimitive.content,MessageFamily.valueOf(row.getValue("family").jsonPrimitive.content),
            row.getValue("template").jsonPrimitive.content,terms(row.getValue("features")),row.getValue("at").jsonPrimitive.long,row.getValue("provenance").jsonPrimitive.content)
    }
    fun cache(seed: Seed, labels: List<HumanFamilyLabel>, classifier: FamilyClassifier): String {
        val latest = labels.groupBy {it.rawId}.values.map {it.maxWith(compareBy<HumanFamilyLabel>{row->row.at}.thenBy{row->if(row.provenance=="USER_MESSAGE_FAMILY")1 else 0}.thenBy{row->row.decisionId})}
        val payload = buildJsonObject {
            put("schema",1);put("featuresVersion",FamilyFeatures.VERSION);put("seedVersion",seed.model.version);put("modelVersion",classifier.version)
            put("labels",JsonArray(latest.sortedBy {it.rawId}.map(::labelJson)))
            put("documents",buildJsonObject {classifier.model.documents.toSortedMap(compareBy {it.name}).forEach {(family,count)->put(family.name,count)}})
        }
        return buildJsonObject {put("checksum",FamilyFeatures.sha256(payload.toString()));put("payload",payload)}.toString()
    }
    fun cachedLabels(text: String, seed: Seed): List<HumanFamilyLabel> {
        require(text.length<=16*1024*1024)
        val root=Json.parseToJsonElement(text).jsonObject;val payload=root.getValue("payload").jsonObject
        require(root.getValue("checksum").jsonPrimitive.content==FamilyFeatures.sha256(payload.toString()))
        require(payload.getValue("schema").jsonPrimitive.int==1 && payload.getValue("seedVersion").jsonPrimitive.content==seed.model.version && payload.getValue("featuresVersion").jsonPrimitive.content==FamilyFeatures.VERSION)
        return payload.getValue("labels").jsonArray.map(::label).also { require(checked(seed,it).version==payload.getValue("modelVersion").jsonPrimitive.content) }
    }
}
