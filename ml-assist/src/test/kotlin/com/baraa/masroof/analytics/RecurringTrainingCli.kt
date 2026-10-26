package com.baraa.masroof.analytics

import java.io.File

/** Offline-only tooling, excluded from the application; shares the phone's fitter and features. */
fun main(args: Array<String>) {
    require(args.size in 3..4) { "Usage: corpus.tsv model.json report.json [--check]" }
    val source = File(args[0]).readText()
    val partitions = mutableMapOf<String, MutableList<Pair<RecurringExample, RecurringLabel>>>()
    val seen = mutableSetOf<String>()
    val sourcePartitions = mutableMapOf<String, String>()
    source.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.forEach { line ->
        val columns = line.split('\t')
        require(columns.size == 13 && columns[2] in setOf("train", "validation", "test"))
        require(seen.add(columns[0])) { "A series may occur only once and cannot cross partitions" }
        require(columns[1].isNotBlank())
        require(sourcePartitions.putIfAbsent(columns[1], columns[2])?.let { it == columns[2] } != false) { "A contributor cannot cross partitions" }
        val row = RecurringExample(columns[0], columns.drop(5).map(String::toDouble), RecurringLabel.valueOf(columns[3]), 0)
        partitions.getOrPut(columns[2]) { mutableListOf() } += row to RecurringLabel.valueOf(columns[4])
    }
    val training = partitions["train"].orEmpty().map { it.first }
    val validation = partitions["validation"].orEmpty()
    val test = partitions["test"].orEmpty()
    val fitted = if (training.isEmpty()) RecurringModel("recurring-untrained-v1", List(3) { List(9) { 0.0 } }) else RecurringLearning.fit(training)
    fun metrics(model: RecurringModel, rows: List<Pair<RecurringExample, RecurringLabel>>): RecurringMetrics {
        val proposed = rows.mapNotNull { (row, rule) -> model.predict(row.features)?.takeIf { it.label != RecurringLabel.NONE }?.let { Triple(row, rule, it.label) } }
        val positives = rows.count { it.first.label != RecurringLabel.NONE }
        val negatives = rows.size - positives
        val correct = proposed.count { it.first.label == it.third }
        val ruleCorrect = rows.count { it.first.label != RecurringLabel.NONE && it.first.label == it.second }
        return RecurringMetrics(proposed.size, correct, positives, negatives,
            proposed.count { it.first.label == RecurringLabel.NONE }, rows.count { it.first.label == RecurringLabel.NONE && it.second != RecurringLabel.NONE },
            ruleCorrect, proposed.count { it.third == RecurringLabel.WEEKLY }, proposed.count { it.third == RecurringLabel.MONTHLY })
    }
    // Validation selects a threshold; the final test only vetoes release.
    val chosen = (80..99).map { fitted.copy(threshold = it / 100.0) }.filter { metrics(it, validation).eligible() }
        .maxWithOrNull(compareBy<RecurringModel> { metrics(it, validation).correct }.thenBy { it.threshold }) ?: fitted
    val validationMetrics = metrics(chosen, validation)
    val testMetrics = metrics(chosen, test)
    val accepted = training.isNotEmpty() && validationMetrics.eligible() && testMetrics.eligible()
    val safety = validation.joinToString(",") { (row, _) ->
        "{\"seriesKey\":\"${hash(row.seriesKey)}\",\"features\":${row.features},\"label\":\"${row.label}\",\"decidedAt\":0}"
    }
    val version = "recurring-logistic-v1-${hash(source).take(16)}"
    val model = """{"task":"recurring","version":"$version","featureVersion":"${RecurringLearning.FEATURE_VERSION}","weights":${chosen.weights},"threshold":${chosen.threshold},"accepted":$accepted,"safety":[$safety]}""" + "\n"
    val report = """{"version":"$version","accepted":$accepted,"reason":"${if (accepted) "QUALIFIED" else "INSUFFICIENT_DATA_OR_NO_IMPROVEMENT"}","trainingSeries":${training.size},"validation":${validationMetrics.json()},"test":${testMetrics.json()},"sourceSha256":"${hash(source)}"}""" + "\n"
    for ((path, content) in listOf(args[1] to model, args[2] to report)) {
        val file = File(path)
        if (args.getOrNull(3) == "--check") require(file.isFile && file.readText() == content) { "Recurring model drift" }
        else { file.parentFile?.mkdirs(); file.writeText(content) }
    }
    println("Recurring seed: accepted=$accepted training=${training.size} validation=${validation.size} test=${test.size}")
}

private data class RecurringMetrics(val proposed: Int, val correct: Int, val positives: Int, val negatives: Int,
    val falsePositives: Int, val ruleFalsePositives: Int, val ruleCorrect: Int, val weekly: Int, val monthly: Int) {
    fun eligible() = weekly >= 30 && monthly >= 30 && negatives >= 30 && proposed > 0 && positives > 0 &&
        correct.toDouble() / proposed >= .95 && (correct - ruleCorrect).toDouble() / positives >= .1 && falsePositives <= ruleFalsePositives
    fun json() = """{"proposed":$proposed,"correct":$correct,"positives":$positives,"negatives":$negatives,"falsePositives":$falsePositives,"ruleFalsePositives":$ruleFalsePositives,"ruleCorrect":$ruleCorrect,"weekly":$weekly,"monthly":$monthly}"""
}
