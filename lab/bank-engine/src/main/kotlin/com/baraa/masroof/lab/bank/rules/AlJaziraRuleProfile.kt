package com.baraa.masroof.lab.bank.rules

/** Literal bank identity vocabulary; not a second parser or ownership resolver. */
object AlJaziraRuleProfile {
    fun current() = RuleLibrary.current().let { schema -> schema.copy(network = schema.network.copy(
        standaloneDestinationBrackets = true,standaloneSourceBrackets = true,
        unidentifiedExternalBankMarkers = listOf("بنك","bank"),
        bankAliases = mapOf(
            "BANK_ALJAZIRA" to listOf("AlJazira","Bank AlJazira","AlJazira Bank","بنك الجزيرة","بنك الجزيره"),
            "BANK_D360" to listOf("بنك دال ثلاثمائة وستون","D360 Bank","D360 بنك"),
            "BANK_RIYAD" to listOf("بنك الرياض","Riyad Bank"),
            "BANK_ANB" to listOf("البنك العربي الوطني","Arab National Bank"),
            "BANK_SNB" to listOf("البنك الاهلي السعودي","Saudi National Bank")))) }
}
