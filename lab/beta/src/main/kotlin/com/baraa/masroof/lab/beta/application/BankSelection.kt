package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.lab.finance.value.Environment

/** User import scope, intentionally absent from BankCatalog/BankInterpreter parameters. */
data class BankSelection(val environment: Environment, val banks: Set<BankId>)
