package com.baraa.masroof.lab.beta.verification

import com.baraa.masroof.lab.bank.*
import com.baraa.masroof.lab.bank.interpretation.RuleInterpreter
import com.baraa.masroof.lab.beta.application.evidence.InterpretationArchive
import com.baraa.masroof.lab.beta.application.interpretation.BetaInterpretationService

/** Hand-authored typed storage fixtures, not interpreter accuracy evidence. Dedicated cutover tests
 * below use real original messages. The one-character spans exercise storage boundary validation. */
internal object FinancialArchiveFixture {
    val VERSION = RuleInterpreter(BetaInterpretationService.currentSchema()).version
    fun encode(value: Interpretation): String = InterpretationArchive.encode(value.copy(evidence = value.evidence.ifEmpty {
        listOf(Evidence("fixture.family", "family", 0, 1), Evidence("fixture.amount", "AMOUNT", 0, 1),
            Evidence("fixture.time", "occurredAtLocal", 0, 1))
    }))
}
