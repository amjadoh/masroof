package com.baraa.masroof.lab.engine

import kotlinx.serialization.Serializable

/** Data only. Saved JSON without this configuration stays readable with no invented bank identity data. */
@Serializable
data class NetworkRuleConfig(
    val bankAliases: Map<String, List<String>> = emptyMap(),
    val sourceBankLabels: List<String> = emptyList(),
    val destinationBankLabels: List<String> = emptyList(),
    val standaloneDestinationBrackets: Boolean = false,
    val networkLabels: List<String> = emptyList(),
) {
    fun validate() {
        require(bankAliases.size <= 100)
        require(bankAliases.keys.all { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) })
        require(bankAliases.values.all { it.isNotEmpty() })
        (bankAliases.values + listOf(sourceBankLabels, destinationBankLabels, networkLabels)).forEach { terms ->
            require(terms.size <= 20 && terms.all { it.any(Char::isLetterOrDigit) && it.length <= 100 && '\n' !in it && '\r' !in it })
        }
    }
}

/** Reviewed against the preserved regression samples, not an independent bank identity authority. */
object SharedNetworkDefaults {
    fun create() = NetworkRuleConfig(
        bankAliases = mapOf(
            "BANK_ALJAZIRA" to listOf("بنك الجزيرة"),
            "BANK_RIYAD" to listOf("بنك الرياض"),
            "BANK_ANB" to listOf("البنك العربي الوطني"),
            "BANK_SNB" to listOf("البنك الأهلي السعودي"),
        ),
        sourceBankLabels = listOf("البنك المرسل", "sender bank", "source bank"),
        destinationBankLabels = listOf("البنك المستقبل", "البنك المستفيد", "destination bank", "recipient bank"),
        standaloneDestinationBrackets = true,
        networkLabels = listOf("شبكة التحويل", "transfer network", "network"),
    )
}
