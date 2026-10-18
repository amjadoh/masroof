package com.baraa.masroof.lab.beta.application.interpretation

import java.time.Clock

/** Preserved historical tooling, compiled only for JVM verification and never shipped by Beta. */
internal object HistoricalInterpretationService {
        /** Historical regression tooling only; never selected by the Beta composition root. */
        fun sharedRules(clock: Clock = Clock.systemUTC()) = BetaInterpretationService(
            SharedRuleInterpretationAdapter(SharedRuleInterpretationAdapter.currentLabSchema(), clock), LegacyInterpretationAdapter())
}
