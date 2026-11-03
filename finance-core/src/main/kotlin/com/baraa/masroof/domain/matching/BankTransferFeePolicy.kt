package com.baraa.masroof.domain.matching

import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParsedEvent
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
    private val INCLUDED_FEES = setOf(BigDecimal("0.25"), BigDecimal("0.50"))

    /**
     * The fee inside an SNB transfer to another bank when no counterpart SMS shows it.
     * SNB adds 0.25 or 0.50 to every such transfer ("حوالة صادرة محلية ب100.25"), while
     * transfers inside SNB are whole amounts with no fee. Other banks: null.
     */
    fun includedFee(event: ParsedEvent): Money? {
        if (event.bank != BANK_SNB || event.messageFamily != MessageFamily.TRANSFER_OUT) return null
        if (event.bankNetworkType != BankNetworkType.INTER_BANK) return null
        val amount = event.amount ?: return null
        val cents = amount.amount.remainder(BigDecimal.ONE)
        val fee = INCLUDED_FEES.firstOrNull { it.compareTo(cents) == 0 } ?: return null
        return Money.of(fee, amount.currency).takeIf { amount.amount > fee }
    }

    /** A fee still at SNB's printed 0.25 or 0.50, before its VAT was added. */
    fun isBareFee(fee: Money): Boolean = INCLUDED_FEES.any { it.compareTo(fee.amount) == 0 }

    fun chargedFee(bank: Bank, inferredFee: Money): Money {
        if (bank != BANK_SNB) return inferredFee
        val charged = inferredFee.amount.multiply(BigDecimal(2))
        val vat = vat(charged) - vat(inferredFee.amount)
        return Money.of(inferredFee.amount + vat, inferredFee.currency)
    }

    /** VAT on one fee line, rounded half-up as the bank prints it. */
    fun vat(amount: BigDecimal): BigDecimal =
        amount.multiply(VAT_RATE).setScale(Money.SCALE, RoundingMode.HALF_UP)
}
