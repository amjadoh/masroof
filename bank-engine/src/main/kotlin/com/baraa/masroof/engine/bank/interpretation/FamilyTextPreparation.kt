package com.baraa.masroof.engine.bank.interpretation

/** Structural candidates only. Consumers must cleanse them before learning or inference. */
data class FamilyTextShape(val title: String, val fieldLabels: List<String>)

/** The interpreter owns normalization; ML never supplies a second bank normalizer. */
object FamilyTextPreparation {
    fun prepare(body: String): FamilyTextShape {
        val source = SourceText(body).segmentInlinePurchase().text
        val first = source.indexOfFirst(Char::isLetterOrDigit)
        val start = if (first < 0) 0 else source.lastIndexOf('\n', first) + 1
        val lines = source.substring(start).lineSequence().toList()
        return FamilyTextShape(lines.firstOrNull().orEmpty().trimStart(), lines.drop(1).mapNotNull {
            it.substringBefore(':', "").trim().takeIf { label ->
                label.isNotEmpty() && label.length <= 60 && label.any(Char::isLetter) && label.none(Char::isDigit)
            }
        })
    }
}
