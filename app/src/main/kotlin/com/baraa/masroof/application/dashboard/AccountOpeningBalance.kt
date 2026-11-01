package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A balance the user confirmed for one owned account at the start of [asOfDate]
 * (the dashboard zone). SMS never states it; it is the movement base, not a fact to post.
 */
@Serializable
data class AccountOpeningBalance(
    val bankId: String,
    val maskedNumber: String,
    val amount: String,
    val currency: String,
    val asOfDate: String,
) {
    init {
        require(bankId.isNotBlank() && bankId != Bank.UNKNOWN.id && maskedNumber.isNotBlank())
        require(BigDecimal(amount).scale() <= Money.SCALE)
        Currency.valueOf(currency)
        LocalDate.parse(asOfDate)
    }

    val date: LocalDate get() = LocalDate.parse(asOfDate)

    val opening: SignedMoneyAmount
        get() = SignedMoneyAmount(BigDecimal(amount).setScale(Money.SCALE, RoundingMode.HALF_EVEN), Currency.valueOf(currency))

    val key: String get() = "$bankId:$maskedNumber"
}

/** Read-only source; dashboard loads never write it. */
fun interface AccountOpeningBalanceSource {
    fun load(): List<AccountOpeningBalance>
}

object AccountOpeningBalanceCodec {
    private val serializer = ListSerializer(AccountOpeningBalance.serializer())

    fun encode(balances: List<AccountOpeningBalance>): String {
        require(balances.map { it.key }.distinct().size == balances.size) { "One opening balance per account" }
        return Json.encodeToString(serializer, balances)
    }

    /** A corrupt value shows no balance rather than a wrong one. */
    fun decodeOrEmpty(raw: String?): List<AccountOpeningBalance> =
        raw?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }
            ?.takeIf { list -> list.map { it.key }.distinct().size == list.size }
            .orEmpty()
}

/** Current balance: the confirmed opening plus the account's cash position since that day. */
data class AccountBalance(
    val bank: Bank,
    val maskedNumber: String,
    val opening: SignedMoneyAmount,
    val asOfDate: LocalDate,
    val movementSinceOpening: SignedMoneyAmount,
) {
    init { require(opening.currency == movementSinceOpening.currency) }

    val balance: SignedMoneyAmount
        get() = SignedMoneyAmount(opening.amount.add(movementSinceOpening.amount).setScale(Money.SCALE, RoundingMode.HALF_EVEN), opening.currency)
}

object AccountBalanceCalculator {
    /** [summaries] cover the transactions since [opening]'s day for the same account. */
    fun balances(openings: List<AccountOpeningBalance>, summaries: List<OwnedAccountPeriodSummary>): List<AccountBalance> {
        val byKey = summaries.associateBy { "${it.bank.id}:${it.maskedNumber}" }
        return openings.mapNotNull { opening ->
            val movement = byKey[opening.key]?.summary?.cashPosition()?.remaining ?: return@mapNotNull null
            if (movement.currency != opening.opening.currency) return@mapNotNull null
            AccountBalance(Bank(opening.bankId), opening.maskedNumber, opening.opening, opening.date, movement)
        }
    }
}
