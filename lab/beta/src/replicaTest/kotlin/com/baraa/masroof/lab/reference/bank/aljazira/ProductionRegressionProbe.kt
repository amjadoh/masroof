package com.baraa.masroof.lab.reference.bank.aljazira

import com.baraa.masroof.lab.bank.interpretation.RuleInterpreter
import com.baraa.masroof.lab.bank.rules.AlJaziraRuleProfile
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.replica.bank.NewEngineSmsAdapter
import com.baraa.masroof.lab.replica.parsing.model.ParseResult
import com.baraa.masroof.lab.replica.parsing.model.SmsParseInput
import com.baraa.masroof.lab.beta.bank.productionParityErrors
import org.junit.Assert.assertTrue

/** Executes the unchanged production regression assertions AND compares successful parses with rules alone. */
class ProductionRegressionProbe {
    private val reference = AlJaziraParsingPipeline()
    private val engine = RuleInterpreter(AlJaziraRuleProfile.current())
    private val adapter = NewEngineSmsAdapter { engine.interpretRouted(it, BankId("BANK_ALJAZIRA")) }

    fun parse(input: SmsParseInput): ParseResult {
        val expected = reference.parse(input)
        val actual = adapter.parse(input)
        if (expected is ParseResult.Success) {
            assertTrue("Known production format must be automatic: $actual", actual is ParseResult.Success)
            actual as ParseResult.Success
            val errors = productionParityErrors(input.rawSmsId,expected.event,actual.event,expected.details,actual.details)
            assertTrue("${input.rawSmsId}:\n${errors.joinToString("\n")}",errors.isEmpty())
        } else if (expected is ParseResult.NonFinancial) {
            assertTrue("Known informational message must not enter review: $actual", actual is ParseResult.NonFinancial)
        }
        return expected
    }
}
