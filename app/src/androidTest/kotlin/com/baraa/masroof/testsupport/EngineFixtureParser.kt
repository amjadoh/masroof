package com.baraa.masroof.testsupport

import com.baraa.masroof.bank.BankSmsAdapter
import com.baraa.masroof.bank.NewEngineSmsAdapter
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.engine.bank.interpretation.RuleInterpreter
import com.baraa.masroof.engine.bank.rules.AlJaziraRuleProfile
import com.baraa.masroof.finance.value.BankId
import com.baraa.masroof.parsing.parser.SmsParseGateway

fun engineFixtureAdapter(pipeline: SmsParseGateway? = null, detector: com.baraa.masroof.parsing.detector.BankDetector? = null): BankSmsAdapter {
    val engine = RuleInterpreter(AlJaziraRuleProfile.current())
    val adapter = NewEngineSmsAdapter { engine.interpretRouted(it, BankId(Bank.BANK_ALJAZIRA.id)) }
    return object : BankSmsAdapter by adapter {
        override fun detect(sender: String, body: String) = detector?.detect(sender, body) ?: adapter.detect(sender, body)
        override fun parse(input: com.baraa.masroof.parsing.model.SmsParseInput) = pipeline?.parse(input) ?: adapter.parse(input)
    }
}

class EngineFixtureParser(
    private val validator: com.baraa.masroof.parsing.validator.ParsedEventValidator = com.baraa.masroof.parsing.validator.DefaultParsedEventValidator(),
) : SmsParseGateway {
    private val engine = RuleInterpreter(AlJaziraRuleProfile.current())
    override fun parse(input: com.baraa.masroof.parsing.model.SmsParseInput): com.baraa.masroof.parsing.model.ParseResult {
        val message = com.baraa.masroof.engine.bank.MessageInput(input.rawSmsId, input.sender, input.body, input.receivedAt, durableSmsReceipt = true)
        return com.baraa.masroof.bank.InterpretationDraftMapper(validator).map(message, engine.interpretRouted(message, BankId(Bank.BANK_ALJAZIRA.id)), Bank.BANK_ALJAZIRA)
    }
}
