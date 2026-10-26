package com.baraa.masroof.application.patterns

import com.baraa.masroof.bank.BankSmsAdapter
import com.baraa.masroof.bank.MessagePatternSmsAdapter
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter

/** Each routing attempt sees one complete document. Existing primary parsers retain their bank identities. */
class ConfiguredPatternAdapters(private val store: MessagePatternStore, private val reservedBankIds: Set<String> = emptySet()) {
    fun snapshot(): List<BankSmsAdapter> {
        val configuration = store.load()
        val interpreter = PatternFactInterpreter(configuration)
        return configuration.banks.filterNot { it.bankId in reservedBankIds }.sortedBy { it.bankId }.map {
            MessagePatternSmsAdapter(it, interpreter)
        }
    }
}
