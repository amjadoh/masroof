package com.baraa.masroof.lab.bank.learning

import com.baraa.masroof.lab.bank.MessageFamily
import com.baraa.masroof.lab.bank.interpretation.FamilyOrigin
import com.baraa.masroof.lab.bank.interpretation.FamilySuggestion

/** Latest human labels are authoritative examples, never the classifier's predictions. */
data class HumanFamilyLabel(val bankId: String,val rawId: String,val decisionId: String,val family: MessageFamily,
    val template: String,val terms: Map<String,Int>,val at: Long,val provenance: String)

class FamilyClassifier(seed: FamilyModel, labels: List<HumanFamilyLabel> = emptyList()) {
    val seed = seed.copy(vocabulary=seed.vocabulary.toSet(),documents=seed.documents.toMap(),counts=seed.counts.mapValues {it.value.toMap()},enabledFamilies=seed.enabledFamilies.toSet())
    private val latest = labels.filter {it.bankId==seed.bankId}.groupBy {it.rawId}.map { (_,rows) -> rows.maxWith(compareBy<HumanFamilyLabel>{it.at}.thenBy{if(it.provenance=="USER_MESSAGE_FAMILY") 1 else 0}.thenBy{it.decisionId}) }
    val version = if (latest.isEmpty()) seed.version else seed.version+"-human-"+FamilyFeatures.sha256(latest.sortedBy {it.rawId}.joinToString("|") {"${it.rawId}:${it.decisionId}:${it.family}:${it.template}"}).take(16)
    private val templates = latest.groupBy {it.template}.mapValues { (_,rows) -> rows.map {it.family}.toSet() }
    val model: FamilyModel = if (latest.isEmpty()) seed else {
        val vocabulary = (seed.vocabulary+latest.flatMap {it.terms.keys}.distinct().sorted().take((4096-seed.vocabulary.size).coerceAtLeast(0))).toSet()
        val documents = seed.documents.toMutableMap()
        val counts = seed.counts.mapValues {it.value.toMutableMap()}.toMutableMap()
        latest.forEach { row ->
            documents[row.family]=(documents[row.family]?:0L)+1
            val target = counts.getOrPut(row.family) {mutableMapOf()}
            row.terms.filterKeys {it in vocabulary}.forEach { (term,count) -> target[term]=(target[term]?:0L)+count.coerceIn(0,5) }
        }
        seed.copy(version=version,vocabulary=vocabulary,documents=documents,counts=counts)
    }
    fun suggest(bankId: String, body: String, normalizedTitle: String): FamilySuggestion? {
        if (bankId!=model.bankId) return null
        val features = FamilyFeatures.extract(body)
        if (features.title!=normalizedTitle) return null
        val choices = templates[features.template]
        if (choices!=null) {
            if (choices.size!=1 || choices.single() !in FamilyModel.FINANCIAL) return null
            return FamilySuggestion(choices.single(),1.0,version,FamilyOrigin.HUMAN_TEMPLATE)
        }
        val prediction = model.predict(features.terms)?:return null
        if (!model.accepts(prediction)) return null
        return FamilySuggestion(prediction.family,prediction.score,version,FamilyOrigin.ML)
    }
}
