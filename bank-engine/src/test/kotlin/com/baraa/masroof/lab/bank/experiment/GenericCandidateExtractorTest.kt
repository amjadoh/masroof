package com.baraa.masroof.lab.bank.experiment

import org.junit.Assert.*
import org.junit.Test

class GenericCandidateExtractorTest {
    @Test fun discoversValuesInUnseenTextWithoutSenderTypeOrTraining() {
        val body = "صيغة تجريبية لم تُعلّم\nقيمة:٨٧٫٦٥SR\nطرف:اسم تجريبي\n2026-08-18 13:59\nمعرّف:*A9Z7"
        val candidates = GenericCandidateExtractor.extract(body)
        assertTrue(candidates.any { it.kind == CandidateKind.MONEY && it.text.raw == "٨٧٫٦٥SR" })
        assertTrue(candidates.any { it.kind == CandidateKind.DATE_TIME && it.text.raw == "2026-08-18 13:59" })
        assertTrue(candidates.any { it.kind == CandidateKind.IDENTIFIER && it.text.raw == "*A9Z7" })
        assertTrue(candidates.any { it.kind == CandidateKind.TEXT && it.text.raw == "اسم تجريبي" })
        candidates.forEach { assertEquals(it.text.raw, body.substring(it.text.start, it.text.end)) }
    }
}
