package com.baraa.masroof.domain.matching

import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * What a bank really takes for a transfer fee its SMS shows only inside the amount.
 *
 * SNB's "حوالة صادرة محلية ب500.25" includes the 0.25 fee but not its VAT. The
 * statement charges twice the fee with 15% VAT, then reverses one fee and its VAT,
 * each VAT rounded half-up on its own line: 0.25 costs 0.29, 0.50 costs 0.57.
 *
 * A fee the bank states apart is already what it takes and never passes through here.
 */
object BankTransferFeePolicy {
    val BANK_SNB: Bank = Bank("BANK_SNB")
    private val VAT_RATE = BigDecimal("0.15")

    fun chargedFee(bank: Bank, inferredFee: Money): Money {
        if (bank != BANK_SNB) return inferredFee
        val charged = inferredFee.amount.multiply(BigDecimal(2))
        val vat = vat(charged) - vat(inferredFee.amount)
        return Money.of(inferredFee.amount + vat, inferredFee.currency)
    }

    private fun vat(amount: BigDecimal): BigDecimal =
        amount.multiply(VAT_RATE).setScale(Money.SCALE, RoundingMode.HALF_UP)
}
