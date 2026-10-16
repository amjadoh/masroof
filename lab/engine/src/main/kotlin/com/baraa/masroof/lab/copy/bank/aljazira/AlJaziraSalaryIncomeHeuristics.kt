package com.baraa.masroof.lab.copy.bank.aljazira

import com.baraa.masroof.lab.copy.parsing.normalizer.containsComparison

/**
 * Detects salary-like wording in Bank AlJazira transfer-in SMS at parse time.
 */
object AlJaziraSalaryIncomeHeuristics {
    private val salaryKeywords = listOf(
        "راتب",
        "رواتب",
        "salary",
        "payroll",
        "wage",
    )

    fun containsSalaryWording(text: String): Boolean {
        val normalized = text.lowercase()
        return salaryKeywords.any { keyword -> normalized.containsComparison(keyword) }
    }
}
