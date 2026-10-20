package com.baraa.masroof.lab.bank.interpretation

import com.baraa.masroof.lab.bank.MessageFamily
import com.baraa.masroof.lab.bank.MessageInput

/** Supplies a family only. Monetary fields and financial approval cannot cross this interface. */
fun interface FamilyAssist { fun suggest(input: MessageInput, normalizedTitle: String): FamilySuggestion? }
enum class FamilyOrigin { ML, HUMAN_TEMPLATE }
data class FamilySuggestion(val family: MessageFamily, val score: Double, val modelVersion: String, val origin: FamilyOrigin) {
    init { require(score.isFinite() && score in 0.0..1.0 && modelVersion.isNotBlank()) }
}
