package com.baraa.masroof.bank

import com.baraa.masroof.lab.bank.Interpretation
import com.baraa.masroof.lab.bank.MessageInput
import com.baraa.masroof.bank.aljazira.AlJaziraBankDetector
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.parsing.model.*

/** Bank routing contract; interpretation is supplied by the production composition root. */
class NewEngineSmsAdapter(private val interpret: (MessageInput) -> Interpretation) : BankSmsAdapter {
    override val bank = Bank.BANK_ALJAZIRA
    private val detector = AlJaziraBankDetector()
    override fun detect(sender: String, body: String) = detector.detect(sender, body)
    override fun parse(input: SmsParseInput): ParseResult {
        val message = MessageInput(input.rawSmsId,input.sender,input.body,input.receivedAt,durableSmsReceipt = true)
        return InterpretationDraftMapper().map(message,interpret(message),bank)
    }
}
