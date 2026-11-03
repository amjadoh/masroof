package com.baraa.masroof.application.settings

import com.baraa.masroof.application.dashboard.AccountBalance
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

/** Where an unexplained difference may come from, all for the account's bank since the last check. */
data class BalanceCheckHints(
    /** One-time codes for an amount that no bank message confirmed, still unanswered in review. */
    val unconfirmedOtpCount: Int = 0,
    val unconfirmedOtpTotal: BigDecimal = BigDecimal.ZERO.setScale(Money.SCALE),
    /** Other bank messages waiting in review, so not counted yet. */
    val pendingReviewCount: Int = 0,
)

sealed interface BalanceCheckResult {
    /** The app agreed with the bank; that moment is the next check's starting point. */
    data object Matched : BalanceCheckResult

    /** [difference] is the app's balance minus the bank's: positive means the app shows more. */
    data class Differs(
        val appBalance: BigDecimal,
        val bankBalance: BigDecimal,
        val difference: BigDecimal,
        val since: Instant,
        val hints: BalanceCheckHints,
    ) : BalanceCheckResult

    /** The account has no balance yet; the bank's figure became its balance. */
    data object Started : BalanceCheckResult
}

/**
 * The monthly check: the user types the balance the bank shows now. A match is stored as the
 * new starting point, so a later difference is only what happened after it. A difference is
 * never fixed silently; the user may adopt the bank's balance with [adoptBankBalance].
 */
class AccountBalanceCheckWorkflow(
    private val balances: AccountBalanceSettingsWorkflow,
    private val hints: suspend (bank: Bank, since: Instant) -> BalanceCheckHints,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun check(account: AccountReference, current: AccountBalance?, bankBalance: BigDecimal): BalanceCheckResult {
        val typed = bankBalance.setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        val opening = balances.opening(account)
        if (current == null || opening == null) {
            balances.setNow(account, typed)
            return BalanceCheckResult.Started
        }
        val app = current.balance.amount.setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        if (app.compareTo(typed) == 0) {
            balances.setNow(account, typed)
            return BalanceCheckResult.Matched
        }
        val since = opening.movementStart(zoneId)
        return BalanceCheckResult.Differs(app, typed, app - typed, since, hints(account.bank, since))
    }

    fun adoptBankBalance(account: AccountReference, bankBalance: BigDecimal) {
        balances.setNow(account, bankBalance)
    }
}
