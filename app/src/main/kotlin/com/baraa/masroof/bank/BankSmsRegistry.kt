package com.baraa.masroof.bank

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.parsing.model.BankDetectionResult

/**
 * Routes incoming SMS by evaluating every registered bank adapter.
 *
 * Exactly one [BankDetectionResult.Detected] adapter → [BankRoutingResult.Matched];
 * more than one → [BankRoutingResult.Ambiguous] (candidates sorted by bank id, so
 * registration order never decides the bank); no claim but at least one suspicion →
 * [BankRoutingResult.SuspectedBank]; otherwise [BankRoutingResult.NotMatched].
 */
class BankSmsRegistry(
    private val adapters: List<BankSmsAdapter>,
    private val configuredAdapters: () -> List<BankSmsAdapter> = { emptyList() },
) {
    private fun snapshot(): List<BankSmsAdapter> = (adapters + configuredAdapters()).also {
        require(it.map { adapter -> adapter.bank }.distinct().size == it.size) { "Duplicate bank adapters" }
    }
    fun route(sender: String, body: String): BankRoutingResult {
        val matches = mutableListOf<BankRoutingResult.Matched>()
        val suspicions = mutableListOf<String>()
        var unmatchedReason: String? = null
        for (adapter in snapshot()) {
            when (val detection = adapter.detect(sender, body)) {
                is BankDetectionResult.Detected ->
                    matches += BankRoutingResult.Matched(adapter, detection)
                is BankDetectionResult.Suspected ->
                    suspicions += detection.evidence + detection.reasons
                is BankDetectionResult.Unknown ->
                    unmatchedReason = detection.reasons.firstOrNull() ?: unmatchedReason
            }
        }
        return when (matches.size) {
            0 ->
                if (suspicions.isNotEmpty()) {
                    BankRoutingResult.SuspectedBank(evidence = suspicions.distinct())
                } else {
                    BankRoutingResult.NotMatched(reason = unmatchedReason ?: "sender_not_in_scope")
                }
            1 -> matches.single()
            else -> BankRoutingResult.Ambiguous(candidates = matches.sortedBy { it.adapter.bank.id })
        }
    }

    fun adapterFor(bank: Bank): BankSmsAdapter? =
        snapshot().singleOrNull { it.bank == bank }

    fun singleAdapterOrNull(): BankSmsAdapter? =
        snapshot().singleOrNull()

    /** Registered banks, sorted by id. Registration order is not an identity. */
    fun banks(): List<Bank> =
        snapshot().map { it.bank }.sortedBy { it.id }
}
