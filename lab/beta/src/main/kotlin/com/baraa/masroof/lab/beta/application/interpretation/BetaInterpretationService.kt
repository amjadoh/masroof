package com.baraa.masroof.lab.beta.application.interpretation

import com.baraa.masroof.lab.bank.BankCatalog
import com.baraa.masroof.lab.bank.BankInterpreter
import com.baraa.masroof.lab.bank.Interpretation
import com.baraa.masroof.lab.bank.MessageInput
import java.time.Clock

/** Explicit caller-supplied samples only: no inbox access, repository writes, corrections or posting. */
class BetaInterpretationService(private val candidate: BankInterpreter, private val baseline: BankInterpreter? = null) {
    val isHistoricalComparison: Boolean get() = baseline != null
    data class ShadowComparison(val baseline: Interpretation, val candidate: Interpretation)

    fun compare(input: MessageInput, catalog: BankCatalog): ShadowComparison =
        ShadowComparison(requireNotNull(baseline) { "Historical comparison must be explicitly configured" }.interpret(input, catalog), candidate.interpret(input, catalog))

    /** Beta runtime: exactly one interpreter call, without a reference/fallback. */
    fun interpret(input: MessageInput, catalog: BankCatalog): Interpretation = candidate.interpret(input, catalog)
    companion object {
        fun independent() = BetaInterpretationService(com.baraa.masroof.lab.bank.interpretation.RuleInterpreter(
            currentSchema()))

        /** Bank identities are configuration, not a second parser or inferred ownership. */
        fun currentSchema() = com.baraa.masroof.lab.bank.rules.RuleLibrary.current().let { schema ->
            schema.copy(network = schema.network.copy(bankAliases = mapOf("BANK_ALJAZIRA" to
                listOf("AlJazira", "Bank AlJazira", "AlJazira Bank", "بنك الجزيرة", "بنك الجزيره"))))
        }

        /** Historical regression tooling only; never selected by the Beta composition root. */
        fun sharedRules(clock: Clock = Clock.systemUTC()) = BetaInterpretationService(
            SharedRuleInterpretationAdapter(SharedRuleInterpretationAdapter.currentLabSchema(), clock), LegacyInterpretationAdapter())
    }

}
