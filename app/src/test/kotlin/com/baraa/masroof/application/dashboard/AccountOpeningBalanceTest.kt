package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.OwnershipStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Fictional accounts: a confirmed opening plus the account's cash position since that day. */
class AccountOpeningBalanceTest {
    private val opening = AccountOpeningBalance("BANK_SNB", "4321", "100.25", "SAR", "2026-09-01")

    @Test fun balanceIsTheOpeningPlusEveryInflowMinusEveryOutflowSinceItsDay() {
        val account = AccountRegistryEntry("a", Bank("BANK_SNB"), "4321", OwnershipStatus.OWNED, firstSeenRawSmsId = null, lastSeenRawSmsId = null)
        val summaries = OwnedAccountPeriodSummaryCalculator.summarize(
            ownedAccounts = listOf(account),
            transactions = listOf(
                tx("in", FinancialTransactionType.EXTERNAL_TRANSFER_IN, "50.00", destination = "account:BANK_SNB:4321"),
                tx("out", FinancialTransactionType.EXTERNAL_TRANSFER_OUT, "30.50", source = "account:BANK_SNB:4321"),
                tx("fee", FinancialTransactionType.FEE, "0.25", source = "account:BANK_SNB:4321"),
                tx("other", FinancialTransactionType.EXTERNAL_TRANSFER_IN, "999.00", destination = "account:BANK_SNB:9999"),
            ),
            parsedRecords = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
        )
        val balance = AccountBalanceCalculator.balances(listOf(opening), summaries).single()
        assertEquals(LocalDate.of(2026, 9, 1), balance.asOfDate)
        assertEquals(SignedMoneyAmount(java.math.BigDecimal("19.25"), Currency.SAR), balance.movementSinceOpening)
        assertEquals(SignedMoneyAmount(java.math.BigDecimal("119.50"), Currency.SAR), balance.balance)
    }

    @Test fun anAccountWithoutAConfirmedOpeningShowsNoBalance() {
        assertTrue(AccountBalanceCalculator.balances(emptyList(), emptyList()).isEmpty())
        assertTrue(AccountBalanceCalculator.balances(listOf(opening), emptyList()).isEmpty())
    }

    @Test fun storedValuesRoundTripAndCorruptOrDuplicateValuesShowNothing() {
        val negative = AccountOpeningBalance("BANK_SAB", "8765", "-20.07", "SAR", "2026-09-01")
        val encoded = AccountOpeningBalanceCodec.encode(listOf(opening, negative))
        assertEquals(listOf(opening, negative), AccountOpeningBalanceCodec.decodeOrEmpty(encoded))
        assertEquals("-20.07", negative.opening.amount.toPlainString())
        assertTrue(AccountOpeningBalanceCodec.decodeOrEmpty(null).isEmpty())
        assertTrue(AccountOpeningBalanceCodec.decodeOrEmpty("{broken").isEmpty())
        assertTrue(AccountOpeningBalanceCodec.decodeOrEmpty(encoded.replace("8765", "4321").replace("BANK_SAB", "BANK_SNB")).isEmpty())
        assertTrue(runCatching { AccountOpeningBalance("BANK_SNB", "4321", "1.001", "SAR", "2026-09-01") }.isFailure)
    }

    private fun tx(id: String, type: FinancialTransactionType, amount: String, source: String? = null, destination: String? = null) =
        FinancialTransaction(id, type, Money.of(amount, Currency.SAR), Instant.parse("2026-09-15T09:00:00Z"),
            source, destination, null, null, null, emptyList())
}
