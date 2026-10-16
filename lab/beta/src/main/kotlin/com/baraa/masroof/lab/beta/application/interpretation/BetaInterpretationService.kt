package com.baraa.masroof.lab.beta.application.interpretation

import com.baraa.masroof.lab.bank.BankCatalog
import com.baraa.masroof.lab.bank.BankInterpreter
import com.baraa.masroof.lab.bank.Interpretation
import com.baraa.masroof.lab.bank.MessageInput
import java.time.Clock

/** Explicit caller-supplied samples only: no inbox access, repository writes, corrections or posting. */
class BetaInterpretationService(private val candidate: BankInterpreter, private val baseline: BankInterpreter = LegacyInterpretationAdapter()) {
    data class ShadowComparison(val baseline: Interpretation, val candidate: Interpretation)

    fun compare(input: MessageInput, catalog: BankCatalog): ShadowComparison =
        ShadowComparison(baseline.interpret(input, catalog), candidate.interpret(input, catalog))
    companion object {
        fun sharedRules(clock: Clock = Clock.systemUTC()) = BetaInterpretationService(
            SharedRuleInterpretationAdapter(SharedRuleInterpretationAdapter.currentLabSchema(), clock))
    }

}
