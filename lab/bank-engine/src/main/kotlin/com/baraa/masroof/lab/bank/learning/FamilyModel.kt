package com.baraa.masroof.lab.bank.learning

import com.baraa.masroof.lab.bank.MessageFamily
import kotlin.math.exp
import kotlin.math.ln

/** Sparse multinomial NB counts; trained offline, inference/update are entirely local. */
data class FamilyModel(
    val bankId: String,
    val version: String,
    val vocabulary: Set<String>,
    val documents: Map<MessageFamily, Long>,
    val counts: Map<MessageFamily, Map<String, Long>>,
    val enabledFamilies: Set<MessageFamily>,
    val scoreThreshold: Double = 0.98,
    val marginThreshold: Double = 0.2,
    val coverageThreshold: Double = 0.6,
) {
    init {
        require(bankId.isNotBlank() && version.isNotBlank())
        require(vocabulary.isNotEmpty() && vocabulary.size<=4096 && vocabulary.all {it.length<=260})
        require(documents.isNotEmpty() && documents.values.all {it in 1..100_000_000})
        require(documents.keys==counts.keys && enabledFamilies.all {it in FINANCIAL && it in documents})
        require(counts.values.all { row -> row.keys.all {it in vocabulary} && row.values.all {it in 0..100_000_000} })
        require(scoreThreshold.isFinite() && scoreThreshold in 0.8..1.0 && marginThreshold in 0.0..1.0 && coverageThreshold in 0.0..1.0)
    }
    data class Prediction(val family: MessageFamily, val score: Double, val margin: Double, val coverage: Double)
    fun predict(terms: Map<String, Int>): Prediction? {
        val known = terms.filterKeys {it in vocabulary}
        if (known.isEmpty()) return null
        val totalDocs = documents.values.sum().toDouble()
        val logScores = documents.mapValues { (family,docs) ->
            val row = counts.getValue(family)
            val denominator = row.values.sum().toDouble()+vocabulary.size
            ln(docs/totalDocs)+known.entries.sumOf { (term,count) -> count.coerceIn(0,5)*ln(((row[term]?:0L)+1.0)/denominator) }
        }
        val max = logScores.values.max()
        val normalized = logScores.mapValues { exp(it.value-max) }
        val sum = normalized.values.sum()
        val ranked = normalized.entries.sortedByDescending { it.value }
        val score = ranked.first().value/sum
        return Prediction(ranked.first().key,score,score-(ranked.getOrNull(1)?.value?:0.0)/sum,known.size.toDouble()/terms.size.coerceAtLeast(1))
    }
    fun accepts(prediction: Prediction) = prediction.family in enabledFamilies && prediction.score>=scoreThreshold && prediction.margin>=marginThreshold && prediction.coverage>=coverageThreshold
    companion object {
        val FINANCIAL = setOf(MessageFamily.PURCHASE,MessageFamily.TRANSFER_IN,MessageFamily.TRANSFER_OUT,MessageFamily.CARD_PAYMENT,
            MessageFamily.BILL_PAYMENT,MessageFamily.FINANCING_INSTALLMENT,MessageFamily.WITHDRAWAL,MessageFamily.REFUND,MessageFamily.FEE)
    }
}
