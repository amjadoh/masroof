package com.baraa.masroof.lab.finance.dashboard.producer

import com.baraa.masroof.lab.finance.asset.*
import com.baraa.masroof.lab.finance.classification.*
import com.baraa.masroof.lab.finance.dashboard.*
import com.baraa.masroof.lab.finance.posting.*
import com.baraa.masroof.lab.finance.value.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class BetaLoanAsOfTest {
    @Test fun futurePaymentDoesNotMarkCurrentLoanObligationPaid() {
        val zone = ZoneId.of("Asia/Riyadh")
        val from = Instant.parse("2026-08-01T00:00:00Z")
        val query = DashboardQuery(Environment.BETA, from, from.plusSeconds(5 * 86400), zone, Currency.SAR)
        val bank = BankId("BANK_ALJAZIRA")
        val account = OwnedAsset(AssetId(Environment.BETA, bank, "account"), AssetKind.ACCOUNT)
        val loan = OwnedAsset(AssetId(Environment.BETA, bank, "loan"), AssetKind.LOAN)
        val input = FinancialClassificationInput(PostingKey(Environment.BETA, "future"), PostingRevision(0, 1), FinancialType.LOAN_REPAYMENT,
            Money.of("50", Currency.SAR), TransactionTime(from.plusSeconds(4 * 86400), zone), FinancialEndpoint.Owned(account.id),
            FinancialEndpoint.External, listOf(account, loan).associateBy { it.id }, setOf("synthetic"), true, financingLoan = loan.id)
        val posting = (FinancialPostingRules.prepare(input) as PostingPreparation.Ready).posting
        val batch = DashboardReadBatch(query, ReadRevision("synthetic", from.plusSeconds(2 * 86400)),
            listOf(DashboardLedgerRow(posting, true)), listOf(account, loan), DashboardCoverage(true, true, true, 0, completeUserCommitments = true),
            DashboardPeriods(salary = DisplayPeriod(query.from, query.until, zone, PeriodKind.SALARY, null)),
            assetFacts = listOf(DashboardAssetFacts(loan.id, loanRemaining = ObservedMoney(Money.of("1000", Currency.SAR), from),
                loanInstallment = ObservedMoney(Money.of("100", Currency.SAR), from))))
        val result = BetaDashboardBuilder.build(batch).snapshot.commitments!!
        assertEquals(PaymentStatus.UNPAID, result.rows.single().status)
        assertEquals(Money.of("100", Currency.SAR), result.remaining)
        assertEquals(Money.of("0", Currency.SAR), result.paid)
    }
}
