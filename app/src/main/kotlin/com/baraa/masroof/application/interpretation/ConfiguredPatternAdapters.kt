package com.baraa.masroof.application.interpretation

import com.baraa.masroof.bank.BankSmsAdapter
import com.baraa.masroof.bank.MessagePatternSmsAdapter
import com.baraa.masroof.data.patterns.MessagePatternStore
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter

/** Each routing attempt sees one complete document. */
class ConfiguredPatternAdapters(private val store: MessagePatternStore,
    private val alJaziraInterpret: ((com.baraa.masroof.engine.bank.MessageInput, PatternFactInterpreter) -> com.baraa.masroof.engine.bank.Interpretation)? = null) {
    private var configurationSnapshot: com.baraa.masroof.engine.bank.patterns.MessagePatternConfiguration? = null
    private var adapterSnapshot: List<BankSmsAdapter> = emptyList()

    @Synchronized
    fun snapshot(): List<BankSmsAdapter> {
        val configuration = store.load()
        if (configuration === configurationSnapshot) return adapterSnapshot
        val interpreter = PatternFactInterpreter(configuration)
        val adapters = configuration.banks.sortedBy { it.bankId }.map {
            MessagePatternSmsAdapter(it, interpreter, alJaziraInterpret.takeIf { _ -> it.bankId == "BANK_ALJAZIRA" })
        }
        configurationSnapshot = configuration
        adapterSnapshot = adapters
        return adapters
    }
}
