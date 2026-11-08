package com.baraa.masroof.application.credit

import kotlinx.serialization.Serializable

/**
 * Per-credit-card companion data that SMS cannot provide: the user's limit, the statement
 * closing day, installment plans read from SAB confirmations, and verified statement figures.
 *
 * Kept outside `masroof.db` (schema v18 is frozen). Amounts are integer halalas.
 * The statement document itself is never stored — only the figures read from it.
 */
@Serializable
data class CardCompanionProfile(
    val limitHalalas: Long? = null,
    val closeDay: Int = 30,
    val plans: List<StoredPlan> = emptyList(),
    val bills: List<StoredBill> = emptyList(),
    /** Plan ids the user removed; rediscovery from the same SMS will not bring them back. */
    val dismissedPlanIds: Set<String> = emptySet(),
) {
    init {
        require(closeDay in 1..31)
        require(limitHalalas == null || limitHalalas > 0)
    }
}

@Serializable
data class StoredPlan(
    val id: String,
    val name: String,
    val principalHalalas: Long,
    val months: Int,
    /** ISO date of the first closing that bills this plan. */
    val firstDue: String,
    val createdAtEpochMillis: Long,
)

@Serializable
data class StoredBill(
    val id: String,
    val issued: String,
    val due: String,
    val limitHalalas: Long,
    val availableHalalas: Long,
    val unbilledPrincipalHalalas: Long,
    val installmentHalalas: Long,
    val totalDueHalalas: Long,
    val minimumDueHalalas: Long,
    val planId: String? = null,
    val futureMonths: Int? = null,
)

interface CardCompanionProfileRepository {
    /** [cardKey] is `bankId:last4`. */
    fun get(cardKey: String): CardCompanionProfile

    fun save(cardKey: String, profile: CardCompanionProfile)
}
