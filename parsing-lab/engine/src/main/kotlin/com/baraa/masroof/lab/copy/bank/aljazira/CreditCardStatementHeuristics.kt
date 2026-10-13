package com.baraa.masroof.lab.copy.bank.aljazira

import com.baraa.masroof.lab.copy.parsing.normalizer.containsComparison

/**
 * Detects credit-card statement notices (typically issued around the 10th).
 */
object CreditCardStatementHeuristics {
    fun isStatementSms(body: String): Boolean {
        val text = body.replace('\n', ' ')
        if (text.containsComparison("إصدار كشف حساب", ignoreCase = true)) return true
        return text.containsComparison("كشف حساب", ignoreCase = true) &&
            text.containsComparison("المبلغ المستحق", ignoreCase = true)
    }
}
