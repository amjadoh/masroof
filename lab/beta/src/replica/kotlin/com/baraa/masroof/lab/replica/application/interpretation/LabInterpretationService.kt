package com.baraa.masroof.lab.replica.application.interpretation

import android.content.Context
import com.baraa.masroof.lab.bank.MessageInput
import com.baraa.masroof.lab.bank.interpretation.RuleInterpreter
import com.baraa.masroof.lab.bank.rules.AlJaziraRuleProfile
import com.baraa.masroof.lab.finance.value.BankId

/** Application facade. Rules first; operational learning is added at the same boundary. */
class LabInterpretationService(@Suppress("UNUSED_PARAMETER") context: Context) {
    private val engine = RuleInterpreter(AlJaziraRuleProfile.current())
    fun interpret(input: MessageInput) = engine.interpretRouted(input,BankId("BANK_ALJAZIRA"))
}
