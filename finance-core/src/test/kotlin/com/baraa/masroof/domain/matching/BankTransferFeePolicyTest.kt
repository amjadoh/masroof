package com.baraa.masroof.domain.matching

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Amounts are the fee lines of a real SNB statement (charge, VAT, reversal, VAT reversal). */
class BankTransferFeePolicyTest {
    private val snb = BankTransferFeePolicy.BANK_SNB

    @Test
    fun snbQuarterFeeCostsItsVatToo() {
        // -0.50 -0.08 +0.25 +0.04
        assertEquals(sar("0.29"), BankTransferFeePolicy.chargedFee(snb, sar("0.25")))
    }

    @Test
    fun snbHalfFeeRoundsEachVatLineOnItsOwn() {
        // -1.00 -0.15 +0.50 +0.08
        assertEquals(sar("0.57"), BankTransferFeePolicy.chargedFee(snb, sar("0.50")))
    }

    @Test
    fun otherBanksKeepTheInferredFee() {
        assertEquals(sar("0.25"), BankTransferFeePolicy.chargedFee(Bank.BANK_ALJAZIRA, sar("0.25")))
    }

    @Test
    fun snbTransferToAnotherBankCarriesItsFeeInTheAmount() {
        assertEquals(sar("0.25"), BankTransferFeePolicy.includedFee(transfer("100.25")))
        assertEquals(sar("0.50"), BankTransferFeePolicy.includedFee(transfer("6000.50")))
    }

    @Test
    fun wholeAmountsInsideSnbAndOtherBanksHaveNoIncludedFee() {
        assertEquals(null, BankTransferFeePolicy.includedFee(transfer("100.00")))
        assertEquals(null, BankTransferFeePolicy.includedFee(transfer("100.25", network = BankNetworkType.INTRA_BANK)))
        assertEquals(null, BankTransferFeePolicy.includedFee(transfer("100.25", bank = Bank.BANK_ALJAZIRA)))
    }

    private fun transfer(amount: String, bank: Bank = snb, network: BankNetworkType = BankNetworkType.INTER_BANK) =
        ParsedEvent("e", "r", bank, MessageFamily.TRANSFER_OUT, MoneyDirection.OUTGOING, sar(amount), null, null, null,
            null, null, null, null, network, Confidence(1.0), ParseStatus.SUCCESS)

    private fun sar(amount: String) = Money.of(amount, Currency.SAR)
}
