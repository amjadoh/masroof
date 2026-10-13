package com.baraa.masroof.lab.copy.bank.aljazira.extraction

import com.baraa.masroof.lab.copy.parsing.normalizer.comparisonRegex
import com.baraa.masroof.lab.copy.parsing.model.NormalizedSms

class ReferenceExtractor {
    fun extract(sms: NormalizedSms): String? {
        val match = PATTERN.find(sms.comparisonBody) ?: return null
        val range = match.groups[1]?.range ?: return null
        return sms.normalizedSlice(range).trim().takeIf { it.isNotBlank() }
    }

    companion object {
        private val PATTERN = comparisonRegex("""رقم\s*المعاملة\s*:\s*([^\n]+)""")
    }
}
