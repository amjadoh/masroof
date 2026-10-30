package com.baraa.masroof.engine.bank.patterns

/** Editor snapshots; the store serializes/validates a complete document before replacing durable state. */
data class MessagePatternConfiguration(
    val generation: Long = 0,
    val banks: List<BankMessagePatterns> = emptyList(),
    /** Explicit user treatment, consumed by the application; never inferred by the interpreter. */
    val confirmedLoanPayments: List<ConfirmedLoanPayment> = emptyList(),
) {
    init {
        require(confirmedLoanPayments.size <= 64)
        require(confirmedLoanPayments.map { it.billerCode }.distinct().size == confirmedLoanPayments.size)
    }
}

data class BankMessagePatterns(
    val bankId: String,
    val senders: List<String>,
    val displayName: String? = null,
    /** Immutable revisions are retained. Only the latest revision of each id can be active. */
    val patterns: List<SavedMessagePattern> = emptyList(),
    /** Identifiers only: prevents bundled definitions from returning after an explicit permanent deletion. */
    val deletedPatternIds: Set<String> = emptySet(),
) {
    init {
        require(deletedPatternIds.size <= 512 && deletedPatternIds.all { it.matches(Regex("[A-Za-z0-9_.-]{1,64}")) })
        require(patterns.none { it.id in deletedPatternIds }) { "Deleted patterns cannot be restored as revisions" }
    }
    val currentPatterns: List<SavedMessagePattern>
        get() = patterns.groupBy { it.id }.values.map { versions -> versions.maxBy { it.revision } }.sortedBy { it.id }
}

data class ConfirmedLoanPayment(
    val billerCode: String,
    val loanBankId: String,
    val loanType: String,
    /** Exact existing rows whose correction the user approved; recovery never scans posted history. */
    val confirmedTransactionIds: Set<String> = emptySet(),
) {
    init {
        require(billerCode.matches(Regex("[0-9]{1,16}")))
        require(loanBankId.matches(Regex("[A-Za-z0-9_-]{1,64}")) && !loanBankId.equals("UNKNOWN", true))
        require(loanType in setOf("PERSONAL", "AUTO", "MORTGAGE"))
        require(confirmedTransactionIds.size <= 512 && confirmedTransactionIds.all {
            it.matches(Regex("[A-Za-z0-9_:.\\-]{1,160}"))
        })
    }
}
