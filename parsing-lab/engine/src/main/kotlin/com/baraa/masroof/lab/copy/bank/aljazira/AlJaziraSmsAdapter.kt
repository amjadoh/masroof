package com.baraa.masroof.lab.copy.bank.aljazira

import com.baraa.masroof.lab.copy.bank.BankSmsAdapter
import com.baraa.masroof.lab.copy.domain.model.Bank
import com.baraa.masroof.lab.copy.parsing.detector.BankDetector
import com.baraa.masroof.lab.copy.parsing.model.BankDetectionResult
import com.baraa.masroof.lab.copy.parsing.model.ParseResult
import com.baraa.masroof.lab.copy.parsing.model.SmsParseInput
import com.baraa.masroof.lab.copy.parsing.parser.SmsParseGateway

/**
 * Bank AlJazira SMS adapter wrapping the existing detector and parse pipeline.
 *
 * [detect] is the only AlJazira sender check; [parse] runs after routing and does
 * not re-detect.
 */
class AlJaziraSmsAdapter(
    private val detector: BankDetector = AlJaziraBankDetector(),
    private val pipeline: SmsParseGateway = AlJaziraParsingPipeline(),
) : BankSmsAdapter {
    override val bank: Bank = Bank.BANK_ALJAZIRA

    override fun detect(sender: String, body: String): BankDetectionResult =
        detector.detect(sender, body)

    override fun parse(input: SmsParseInput): ParseResult =
        pipeline.parse(input)
}
