package com.baraa.masroof.lab.bank.learning

import com.baraa.masroof.lab.bank.interpretation.SourceText
import java.security.MessageDigest

/** Uses the engine's normalization/inline segmentation; never includes monetary values. */
object FamilyFeatures {
    const val VERSION = "bank-family-title-v1"
    data class Features(val title: String, val terms: Map<String, Int>, val template: String)
    fun extract(body: String): Features {
        val source = SourceText(body).segmentInlinePurchase().text
        val first = source.indexOfFirst(Char::isLetterOrDigit)
        val start = if (first<0) 0 else source.lastIndexOf('\n',first)+1
        val title = source.substring(start).substringBefore('\n').trimStart().take(240)
        val words = Regex("[\\p{L}]+").findAll(title).map { it.value }.take(64).toList()
        val terms = mutableListOf<String>()
        terms += words.map { "w:$it" }
        terms += words.zipWithNext { a,b -> "b:$a $b" }
        val letters = words.joinToString(" ")
        for (n in 3..5) if (letters.length>=n) terms += letters.windowed(n).map { "c$n:$it" }
        val labels = source.substring(start).lineSequence().drop(1).mapNotNull {
            it.substringBefore(':', "").trim().takeIf { label -> label.isNotEmpty() && label.length<=60 && label.any(Char::isLetter) && label.none(Char::isDigit) }
        }.sorted().joinToString("|")
        return Features(title,terms.groupingBy { it }.eachCount().mapValues { it.value.coerceAtMost(5) }.toSortedMap(),sha256("$VERSION|$title|$labels"))
    }
    fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
