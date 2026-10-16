package com.baraa.masroof.lab.copy.domain.model

/**
 * Bank-scoped loan container identity for registry lookup and assembly.
 */
data class LoanReference(
    val bank: Bank,
    val loanType: LoanType,
)
