package com.baraa.masroof.engine.bank.patterns

/** Editor snapshots; the store serializes/validates a complete document before replacing durable state. */
data class MessagePatternConfiguration(val generation: Long = 0, val banks: List<BankMessagePatterns> = emptyList())

data class BankMessagePatterns(
    val bankId: String,
    val senders: List<String>,
    val displayName: String? = null,
    /** Immutable revisions are retained. Only the latest revision of each id can be active. */
    val patterns: List<SavedMessagePattern> = emptyList(),
) {
    val currentPatterns: List<SavedMessagePattern>
        get() = patterns.groupBy { it.id }.values.map { versions -> versions.maxBy { it.revision } }.sortedBy { it.id }
}
