package com.baraa.masroof.bank.patterns

import com.baraa.masroof.bank.MessagePatternSmsAdapter
import com.baraa.masroof.data.patterns.MessagePatternConfigurationCodec
import com.baraa.masroof.engine.bank.patterns.PatternFactInterpreter
import java.io.File

internal fun bundledAlJaziraPatternAdapter(): MessagePatternSmsAdapter {
    val file = File("src/main/assets/bank-patterns/BANK_ALJAZIRA.patterns.json")
    check(file.isFile) { "Shared AlJazira definitions are missing" }
    val configuration = MessagePatternConfigurationCodec.decode(file.readText())
    return MessagePatternSmsAdapter(configuration.banks.single(), PatternFactInterpreter(configuration))
}
