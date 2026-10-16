package com.baraa.masroof.lab.copy.parsing.parser

import com.baraa.masroof.lab.copy.parsing.model.ParseResult
import com.baraa.masroof.lab.copy.parsing.model.SmsParseInput

/**
 * Narrow parse entry used by SMS ingestion and bank pipelines.
 */
fun interface SmsParseGateway {
    fun parse(input: SmsParseInput): ParseResult
}
