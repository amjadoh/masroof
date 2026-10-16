package com.baraa.masroof.lab.finance.dashboard

import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.value.Environment
import com.baraa.masroof.lab.finance.value.Money
import java.time.Instant

data class DashboardQuery(val environment: Environment, val from: Instant, val until: Instant) {
    init { require(from < until) }
}

/** Null means unavailable, not zero. Card dues belong to the facility, never to a debit card. */
data class AccountSummary(val id: AssetId, val balance: Money?)
data class DebitSpending(val cardId: AssetId, val spending: Money?)
data class FacilitySummary(val facilityId: AssetId, val statementDue: Money?)

/** Computed and persisted-fact-backed by application builders; presentation only displays it. */
data class DashboardSnapshot(
    val query: DashboardQuery,
    val accounts: List<AccountSummary>,
    val debitCards: List<DebitSpending>,
    val creditFacilities: List<FacilitySummary>,
) {
    init {
        val ids = accounts.map { it.id } + debitCards.map { it.cardId } + creditFacilities.map { it.facilityId }
        require(ids.all { it.environment == query.environment })
        require(ids.distinct().size == ids.size)
    }
}

/** No writes, SMS text or bank parsing API. Period is [from, until). */
fun interface DashboardReader {
    suspend fun read(query: DashboardQuery): DashboardSnapshot
}
