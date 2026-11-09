package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.RegistryEntityIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.model.CommitmentPauseInterval
import com.baraa.masroof.domain.model.CommitmentRecurrence
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CommitmentsOverviewBuilderTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private val period = FinancialPeriod(
        startDate = LocalDate.parse("2026-07-27"),
        endDateExclusive = LocalDate.parse("2026-08-27"),
    )

    @Test
    fun userCommitmentWithoutRecurrence_countsAsPaidInSourcePeriod() {
        val start = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone)
        val tx = transaction(
            id = "tx-netflix",
            merchant = "Netflix",
            amount = "71",
            at = start.plusSeconds(60),
        )
        val commitment = commitment(
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            sourceTransactionId = tx.id,
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(tx),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(Money.of("71.00", Currency.SAR), overview.total)
        assertEquals(Money.of("71.00", Currency.SAR), overview.paid)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
    }

    @Test
    fun oneTimeCommitment_outsideSalaryPeriod_isExcluded() {
        val previousPeriod = FinancialPeriod(
            startDate = LocalDate.parse("2026-06-27"),
            endDateExclusive = LocalDate.parse("2026-07-27"),
        )
        val commitment = commitment(
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            sourceTransactionId = "tx-netflix",
            transactionDate = LocalDate.parse("2026-07-01"),
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertTrue(overview.rows.isEmpty())
        assertEquals(Money.zero(Currency.SAR), overview.total)

        val inPreviousPeriod = CommitmentsOverviewBuilder.build(
            salaryPeriod = previousPeriod,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )
        assertEquals(1, inPreviousPeriod.rows.size)
        assertEquals(CommitmentPaymentStatus.UNPAID, inPreviousPeriod.rows.single().status)
    }

    @Test
    fun oneTimeCommitment_editedTransactionDate_appearsOnlyInEditedPeriod() {
        val previousPeriod = FinancialPeriod(
            startDate = LocalDate.parse("2026-06-27"),
            endDateExclusive = LocalDate.parse("2026-07-27"),
        )
        val sourceTx = transaction(
            id = "tx-netflix",
            merchant = "Netflix",
            amount = "71",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(previousPeriod.startDate, zone).plusSeconds(60),
        )
        val commitment = commitment(
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            sourceTransactionId = sourceTx.id,
            transactionDate = LocalDate.parse("2026-08-01"),
        )

        val inSourcePeriod = CommitmentsOverviewBuilder.build(
            salaryPeriod = previousPeriod,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(sourceTx),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )
        assertTrue(inSourcePeriod.rows.isEmpty())

        val inEditedPeriod = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(sourceTx),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )
        assertEquals(1, inEditedPeriod.rows.size)
        assertEquals(CommitmentPaymentStatus.UNPAID, inEditedPeriod.rows.single().status)
    }

    @Test
    fun recurringCommitment_withoutPaymentInPeriod_isUnpaid() {
        val commitment = commitment(
            name = "STC",
            amount = Money.of("173.00", Currency.SAR),
            sourceTransactionId = "tx-old",
            recurrence = CommitmentRecurrence.MONTHLY,
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(Money.of("173.00", Currency.SAR), overview.total)
        assertEquals(Money.zero(Currency.SAR), overview.paid)
        assertEquals(Money.of("173.00", Currency.SAR), overview.remaining)
        assertEquals(CommitmentPaymentStatus.UNPAID, overview.rows.single().status)
    }

    @Test
    fun yearlyCommitment_outsideAnniversaryPeriod_isExcluded() {
        val commitment = commitment(
            name = "Insurance",
            amount = Money.of("1200.00", Currency.SAR),
            sourceTransactionId = "tx-insurance",
            recurrence = CommitmentRecurrence.YEARLY,
            transactionDate = LocalDate.parse("2026-01-15"),
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertTrue(overview.rows.isEmpty())
    }

    @Test
    fun weeklyCommitment_countsEachDueOccurrenceAndPayment() {
        val commitment = commitment(
            name = "Cleaning",
            amount = Money.of("100.00", Currency.SAR),
            sourceTransactionId = "tx-cleaning",
            recurrence = CommitmentRecurrence.WEEKLY,
            transactionDate = LocalDate.parse("2026-07-28"),
        )
        val payment = transaction(
            id = "payment-cleaning",
            merchant = "Cleaning",
            amount = "100",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone).plusSeconds(60),
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(payment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(5, overview.rows.size)
        assertEquals(Money.of("500.00", Currency.SAR), overview.total)
        assertEquals(Money.of("100.00", Currency.SAR), overview.paid)
        assertEquals(Money.of("400.00", Currency.SAR), overview.remaining)
        assertEquals(1, overview.rows.count { it.status == CommitmentPaymentStatus.PAID })
    }

    @Test
    fun weeklyCommitment_assignsPaidAmountsInPaymentChronologicalOrder() {
        val commitment = commitment(
            name = "Cleaning",
            amount = Money.of("100.00", Currency.SAR),
            sourceTransactionId = "tx-cleaning",
            recurrence = CommitmentRecurrence.WEEKLY,
            transactionDate = LocalDate.parse("2026-07-28"),
        )
        val earlierPayment = transaction(
            id = "payment-cleaning-earlier",
            merchant = "Cleaning",
            amount = "100",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(LocalDate.parse("2026-07-28"), zone)
                .plusSeconds(60),
        )
        val laterPayment = transaction(
            id = "payment-cleaning-later",
            merchant = "Cleaning",
            amount = "120",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(LocalDate.parse("2026-08-04"), zone)
                .plusSeconds(60),
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(laterPayment, earlierPayment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        val paidRows = overview.rows.filter { it.status == CommitmentPaymentStatus.PAID }
        assertEquals(2, paidRows.size)
        assertEquals(Money.of("100.00", Currency.SAR), paidRows[0].amount)
        assertEquals(Money.of("120.00", Currency.SAR), paidRows[1].amount)
        assertEquals(LocalDate.parse("2026-07-28"), paidRows[0].expectedDate)
        assertEquals(LocalDate.parse("2026-08-04"), paidRows[1].expectedDate)
    }

    @Test
    fun recurringCommitment_paidRowUsesPaymentDateNotOccurrence() {
        val paymentAt = FinancialPeriodPolicy.toInclusiveStartInstant(
            LocalDate.parse("2026-08-05"),
            zone,
        ).plusSeconds(60)
        val payment = transaction(
            id = "tx-stc-payment",
            merchant = "STC",
            amount = "165",
            at = paymentAt,
        )
        val commitment = commitment(
            name = "STC",
            amount = Money.of("200.00", Currency.SAR),
            sourceTransactionId = "tx-stc-original",
            recurrence = CommitmentRecurrence.MONTHLY,
            transactionDate = LocalDate.parse("2026-08-01"),
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(payment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
        assertEquals(Money.of("165.00", Currency.SAR), overview.rows.single().amount)
        assertEquals(LocalDate.parse("2026-08-05"), overview.rows.single().expectedDate)
    }

    @Test
    fun monthlyCommitment_withoutDueDate_usesOccurrenceAsExpectedDate() {
        val commitment = commitment(
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            sourceTransactionId = "tx-netflix",
            recurrence = CommitmentRecurrence.MONTHLY,
            transactionDate = LocalDate.parse("2026-08-01"),
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(LocalDate.parse("2026-08-01"), overview.rows.single().expectedDate)
    }

    @Test
    fun monthlyCommitment_appearsInFollowingSalaryPeriod() {
        val commitment = commitment(
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            sourceTransactionId = "tx-netflix",
            recurrence = CommitmentRecurrence.MONTHLY,
            transactionDate = LocalDate.parse("2026-08-01"),
        )
        val nextPeriod = FinancialPeriodPolicy.next(period)

        val inMarkedPeriod = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )
        assertEquals(1, inMarkedPeriod.rows.size)

        val inFollowingPeriod = CommitmentsOverviewBuilder.build(
            salaryPeriod = nextPeriod,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )
        assertEquals(1, inFollowingPeriod.rows.size)
        assertEquals(CommitmentPaymentStatus.UNPAID, inFollowingPeriod.rows.single().status)
    }

    @Test
    fun disabledCommitment_staysVisibleInEarlierPeriodsOnly() {
        val pausedAt = Instant.parse("2026-08-15T00:00:00Z")
        val commitment = commitment(
            name = "STC",
            amount = Money.of("173.00", Currency.SAR),
            sourceTransactionId = "tx-stc",
            recurrence = CommitmentRecurrence.MONTHLY,
            active = false,
            transactionDate = LocalDate.parse("2026-07-01"),
            pauseIntervals = listOf(CommitmentPauseInterval(pausedAt = pausedAt, resumedAt = null)),
        )
        val previousPeriod = FinancialPeriodPolicy.previous(period)

        val inPreviousPeriod = CommitmentsOverviewBuilder.build(
            salaryPeriod = previousPeriod,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )
        assertEquals(1, inPreviousPeriod.rows.size)

        val inDisabledPeriod = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )
        assertTrue(inDisabledPeriod.rows.isEmpty())
    }

    @Test
    fun refundDoesNotCountAsRecurringCommitmentPayment() {
        val refund = transaction(
            id = "refund-stc",
            merchant = "STC",
            amount = "173",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone).plusSeconds(60),
            type = FinancialTransactionType.REFUND,
        )
        val commitment = commitment(
            name = "STC",
            amount = Money.of("173.00", Currency.SAR),
            sourceTransactionId = "tx-stc",
            recurrence = CommitmentRecurrence.MONTHLY,
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(refund),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.UNPAID, overview.rows.single().status)
    }

    @Test
    fun paidCreditCardStatement_isMarkedPaid() {
        val facility = creditFacility(
            due = Money.of("500.00", Currency.SAR),
            last4 = "1234",
        )
        val payment = transaction(
            id = "payment-card",
            merchant = "Card payment",
            amount = "500",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone).plusSeconds(60),
            type = FinancialTransactionType.CREDIT_CARD_PAYMENT,
            destinationContainerId = "card:${Bank.BANK_ALJAZIRA.id}:1234",
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = emptyList(),
            creditFacilities = CreditFacilitiesOverview(listOf(facility), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(payment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
        assertEquals(Money.of("500.00", Currency.SAR), overview.paid)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
    }

    @Test
    fun paidCreditCardStatement_doesNotReappearUnpaidInLaterSalaryPeriod() {
        val facility = creditFacility(
            due = Money.of("500.00", Currency.SAR),
            last4 = "1234",
        )
        val payment = transaction(
            id = "payment-card",
            merchant = "Card payment",
            amount = "500",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone).plusSeconds(60),
            type = FinancialTransactionType.CREDIT_CARD_PAYMENT,
            destinationContainerId = "card:${Bank.BANK_ALJAZIRA.id}:1234",
        )
        val laterPeriod = FinancialPeriod(
            startDate = LocalDate.parse("2026-08-27"),
            endDateExclusive = LocalDate.parse("2026-09-27"),
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = laterPeriod,
            commitments = emptyList(),
            creditFacilities = CreditFacilitiesOverview(listOf(facility), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(payment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertTrue(overview.rows.isEmpty())
        assertEquals(Money.zero(Currency.SAR), overview.total)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
    }

    @Test
    fun paidCreditCardStatement_countsPaymentBeforeSalaryPeriodStart() {
        val facility = creditFacility(
            due = Money.of("500.00", Currency.SAR),
            last4 = "1234",
        )
        val payment = transaction(
            id = "payment-card",
            merchant = "Card payment",
            amount = "500",
            at = Instant.parse("2026-07-26T12:00:00Z"),
            type = FinancialTransactionType.CREDIT_CARD_PAYMENT,
            destinationContainerId = "card:${Bank.BANK_ALJAZIRA.id}:1234",
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = emptyList(),
            creditFacilities = CreditFacilitiesOverview(listOf(facility), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(payment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
    }

    @Test
    fun duplicateRecurringCommitments_shareSingleInPeriodPayment() {
        val start = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone)
        val payment = transaction(
            id = "payment-netflix",
            merchant = "Netflix",
            amount = "71",
            at = start.plusSeconds(120),
        )
        val first = commitment(
            id = "cmt_first",
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            sourceTransactionId = "tx-netflix-1",
            recurrence = CommitmentRecurrence.MONTHLY,
        )
        val second = commitment(
            id = "cmt_second",
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            sourceTransactionId = "tx-netflix-2",
            recurrence = CommitmentRecurrence.MONTHLY,
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(first, second),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(payment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(2, overview.rows.size)
        assertEquals(0, overview.rows.count { it.status == CommitmentPaymentStatus.PAID })
        assertEquals(2, overview.rows.count { it.status == CommitmentPaymentStatus.UNPAID })
        assertEquals(Money.zero(Currency.SAR), overview.paid)
        assertEquals(Money.of("142.00", Currency.SAR), overview.remaining)
        assertTrue(overview.rows.all { it.paymentMatchIssue == CommitmentMatchIssue.AMBIGUOUS_PAYMENT })
    }

    @Test
    fun inactiveCommitments_areExcluded() {
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(
                commitment(
                    name = "School",
                    amount = Money.of("3000.00", Currency.SAR),
                    sourceTransactionId = "tx-school",
                    active = false,
                    pauseIntervals = listOf(
                        CommitmentPauseInterval(
                            pausedAt = Instant.parse("2026-08-01T00:00:00Z"),
                            resumedAt = null,
                        ),
                    ),
                ),
            ),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertTrue(overview.rows.isEmpty())
        assertEquals(Money.zero(Currency.SAR), overview.total)
    }

    @Test
    fun loanWithPeriodPayment_countsAsPaid() {
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = emptyList(),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(
                loans = listOf(
                    LoanOverview(
                        bank = Bank.BANK_ALJAZIRA,
                        loanType = LoanType.PERSONAL,
                        displayLabel = "Personal loan",
                        remainingBalance = Money.of("12000.00", Currency.SAR),
                        remainingBalanceAsOf = Instant.parse("2026-08-01T00:00:00Z"),
                        salaryPeriodPayment = SignedMoneyAmount.of(Money.of("900.00", Currency.SAR)),
                        salaryPeriodLabel = "27 July",
                    ),
                ),
                salaryPeriodLabel = "27 July",
                currency = Currency.SAR,
            ),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
        assertEquals(Money.of("900.00", Currency.SAR), overview.total)
        assertEquals(Money.of("900.00", Currency.SAR), overview.paid)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
    }

    @Test
    fun loanWithoutPeriodPayment_usesInstallmentNotRemainingBalance() {
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = emptyList(),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(
                loans = listOf(
                    LoanOverview(
                        bank = Bank.BANK_ALJAZIRA,
                        loanType = LoanType.PERSONAL,
                        displayLabel = "Personal loan",
                        remainingBalance = Money.of("12000.00", Currency.SAR),
                        remainingBalanceAsOf = Instant.parse("2026-08-01T00:00:00Z"),
                        latestInstallmentAmount = Money.of("900.00", Currency.SAR),
                        latestInstallmentAsOf = Instant.parse("2026-07-27T00:00:00Z"),
                        salaryPeriodPayment = SignedMoneyAmount.zero(Currency.SAR),
                        salaryPeriodLabel = "27 July",
                    ),
                ),
                salaryPeriodLabel = "27 July",
                currency = Currency.SAR,
            ),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.UNPAID, overview.rows.single().status)
        assertEquals(Money.of("900.00", Currency.SAR), overview.total)
        assertEquals(Money.of("900.00", Currency.SAR), overview.remaining)
    }

    @Test
    fun paidOffLoanWithoutPeriodPayment_isOmitted() {
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = emptyList(),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(
                loans = listOf(
                    LoanOverview(
                        bank = Bank.BANK_ALJAZIRA,
                        loanType = LoanType.PERSONAL,
                        displayLabel = "Personal loan",
                        remainingBalance = Money.zero(Currency.SAR),
                        remainingBalanceAsOf = Instant.parse("2026-08-01T00:00:00Z"),
                        latestInstallmentAmount = Money.of("900.00", Currency.SAR),
                        latestInstallmentAsOf = Instant.parse("2026-07-27T00:00:00Z"),
                        salaryPeriodPayment = SignedMoneyAmount.zero(Currency.SAR),
                        salaryPeriodLabel = "27 July",
                    ),
                ),
                salaryPeriodLabel = "27 July",
                currency = Currency.SAR,
            ),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertTrue(overview.rows.isEmpty())
        assertEquals(Money.zero(Currency.SAR), overview.total)
    }

    @Test
    fun paidCommitment_usesActualPaymentAmountNotStoredCommitmentAmount() {
        val start = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone)
        val tx = transaction(
            id = "tx-stc",
            merchant = "STC",
            amount = "173",
            at = start.plusSeconds(60),
        )
        val commitment = commitment(
            name = "STC",
            amount = Money.of("200.00", Currency.SAR),
            sourceTransactionId = tx.id,
            recurrence = CommitmentRecurrence.MONTHLY,
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(tx),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(Money.of("173.00", Currency.SAR), overview.rows.single().amount)
        assertEquals(Money.of("173.00", Currency.SAR), overview.total)
        assertEquals(Money.of("173.00", Currency.SAR), overview.paid)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
    }

    @Test
    fun recurringCommitment_marksPaidWhenPaymentAmountDiffersFromCommitmentAmount() {
        val start = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone)
        val payment = transaction(
            id = "tx-stc-payment",
            merchant = "STC",
            amount = "165",
            at = start.plusSeconds(120),
        )
        val commitment = commitment(
            name = "STC",
            amount = Money.of("200.00", Currency.SAR),
            sourceTransactionId = "tx-stc-original",
            recurrence = CommitmentRecurrence.MONTHLY,
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(payment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(Money.of("165.00", Currency.SAR), overview.rows.single().amount)
        assertEquals(Money.of("165.00", Currency.SAR), overview.total)
        assertEquals(Money.of("165.00", Currency.SAR), overview.paid)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
    }

    @Test
    fun editedCommitmentAmount_marksPaidWhenSourceTransactionMatchesEditedAmount() {
        val start = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone)
        val tx = transaction(
            id = "tx-stc",
            merchant = "STC",
            amount = "200",
            at = start.plusSeconds(60),
        )
        val commitment = commitment(
            name = "STC",
            amount = Money.of("200.00", Currency.SAR),
            sourceTransactionId = tx.id,
            recurrence = CommitmentRecurrence.MONTHLY,
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(tx),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
    }

    @Test
    fun foreignCurrencyCommitment_usesSarEquivalentWhenSourceTxMissingFromPeriodList() {
        val commitment = commitment(
            name = "Amazon",
            amount = Money.of("25.00", Currency.USD),
            sourceTransactionId = "tx-usd",
            recurrence = CommitmentRecurrence.MONTHLY,
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = mapOf("tx-usd" to Money.of("93.75", Currency.SAR)),
            zoneId = zone,
        )

        assertEquals(Money.of("93.75", Currency.SAR), overview.rows.single().amount)
        assertEquals(Money.of("93.75", Currency.SAR), overview.total)
    }

    @Test
    fun foreignCurrencyCommitment_matchesLaterPaymentUsingEditedAmount() {
        val start = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone)
        val sourceTx = FinancialTransaction(
            id = "tx-usd",
            type = FinancialTransactionType.EXPENSE,
            amount = Money.of("25.00", Currency.USD),
            occurredAt = start.minusSeconds(3600),
            sourceContainerId = null,
            destinationContainerId = null,
            merchant = "Amazon",
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = emptyList(),
        )
        val paymentTx = transaction(
            id = "tx-payment",
            merchant = "Amazon",
            amount = "112.50",
            at = start.plusSeconds(120),
        )
        val commitment = commitment(
            name = "Amazon",
            amount = Money.of("30.00", Currency.USD),
            sourceTransactionId = sourceTx.id,
            recurrence = CommitmentRecurrence.MONTHLY,
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(sourceTx, paymentTx),
            primaryCurrency = Currency.SAR,
            sarEquivalents = mapOf(sourceTx.id to Money.of("93.75", Currency.SAR)),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
        assertEquals(Money.of("112.50", Currency.SAR), overview.rows.single().amount)
    }

    @Test
    fun foreignCurrencyCommitment_scalesSourceSarWithoutRoundingTheRatio() {
        val start = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone)
        val sourceTx = transaction(id = "tx-usd", merchant = "Amazon", amount = "30", at = start.minusSeconds(3600))
            .copy(amount = Money.of("30.00", Currency.USD))
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment(name = "Amazon", amount = Money.of("20.00", Currency.USD),
                sourceTransactionId = sourceTx.id, recurrence = CommitmentRecurrence.MONTHLY)),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(sourceTx),
            primaryCurrency = Currency.SAR,
            sarEquivalents = mapOf(sourceTx.id to Money.of("112.50", Currency.SAR)),
            zoneId = zone,
        )

        // 112.50 * 20 / 30 = 75.00; rounding the ratio to 0.67 first gave 75.38.
        assertEquals(Money.of("75.00", Currency.SAR), overview.rows.single().amount)
    }

    @Test
    fun recurringCommitment_unpaidRowUsesThisPeriodsOccurrenceNotTheFixedDueDate() {
        val later = FinancialPeriod(LocalDate.parse("2026-09-27"), LocalDate.parse("2026-10-27"))
        val monthly = commitment(name = "Gym", amount = Money.of("100.00", Currency.SAR), sourceTransactionId = "tx-gym",
            recurrence = CommitmentRecurrence.MONTHLY).copy(dueDate = LocalDate.parse("2026-08-05"))
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = later,
            commitments = listOf(monthly),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(listOf(LocalDate.parse("2026-10-01")), overview.rows.map { it.expectedDate })
    }

    @Test
    fun recurringCommitment_unpaidRowShowsDueDateInThePeriodThatHoldsIt() {
        val monthly = commitment(name = "Gym", amount = Money.of("100.00", Currency.SAR), sourceTransactionId = "tx-gym",
            recurrence = CommitmentRecurrence.MONTHLY).copy(dueDate = LocalDate.parse("2026-08-05"))
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(monthly),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(listOf(LocalDate.parse("2026-08-05")), overview.rows.map { it.expectedDate })
    }

    @Test
    fun weeklyCommitment_showsDueDateOnlyOnTheFirstUnpaidRow() {
        val weekly = commitment(name = "Cleaner", amount = Money.of("100.00", Currency.SAR), sourceTransactionId = "tx-clean",
            recurrence = CommitmentRecurrence.WEEKLY).copy(dueDate = LocalDate.parse("2026-08-05"))
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(weekly),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        val dates = overview.rows.map { it.expectedDate }
        assertTrue("rows: $dates", dates.size > 1)
        assertEquals(LocalDate.parse("2026-08-05"), dates.first())
        assertEquals(1, dates.count { it == LocalDate.parse("2026-08-05") })
    }

    @Test
    fun weeklyCommitment_dueDateReplacesTheOccurrenceWhoseWeekHoldsIt() {
        val weekly = commitment(name = "Cleaner", amount = Money.of("100.00", Currency.SAR), sourceTransactionId = "tx-clean",
            recurrence = CommitmentRecurrence.WEEKLY).copy(dueDate = LocalDate.parse("2026-08-17"))
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(weekly),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        val dates = overview.rows.mapNotNull { it.expectedDate }.sorted()
        assertEquals(LocalDate.parse("2026-08-01"), dates[0])
        assertEquals(LocalDate.parse("2026-08-08"), dates[1])
        assertEquals(LocalDate.parse("2026-08-17"), dates[2])
        assertTrue("rows: $dates", LocalDate.parse("2026-08-15") !in dates)
        assertEquals("rows: $dates", dates.size, dates.toSet().size)
    }

    @Test
    fun oneTimeCommitment_unpaidRowKeepsItsDueDate() {
        val oneTime = commitment(name = "Insurance", amount = Money.of("100.00", Currency.SAR), sourceTransactionId = "tx-ins")
            .copy(dueDate = LocalDate.parse("2026-08-20"))
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(oneTime),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(listOf(LocalDate.parse("2026-08-20")), overview.rows.map { it.expectedDate })
    }

    @Test
    fun monthlyCommitment_countsEveryPaymentInSalaryPeriodWhenAmountIncreases() {
        val salaryPeriod = FinancialPeriod(
            startDate = LocalDate.parse("2026-09-27"),
            endDateExclusive = LocalDate.parse("2026-10-27"),
        )
        val septemberPayment = transaction(
            id = "chatgpt-september",
            merchant = "Google ChatGPT",
            amount = "267.93",
            at = Instant.parse("2026-09-29T18:21:00Z"),
        )
        val octoberPayment = transaction(
            id = "chatgpt-october",
            merchant = "Google ChatGPT",
            amount = "429.99",
            at = Instant.parse("2026-10-21T18:21:00Z"),
        )
        val beforePeriod = septemberPayment.copy(
            id = "before-period",
            occurredAt = FinancialPeriodPolicy.toInclusiveStartInstant(salaryPeriod.startDate, zone).minusSeconds(1),
        )
        val afterPeriod = octoberPayment.copy(
            id = "after-period",
            occurredAt = FinancialPeriodPolicy.toExclusiveEndInstant(salaryPeriod.endDateExclusive, zone),
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = salaryPeriod,
            commitments = listOf(
                commitment(
                    name = "Google ChatGPT",
                    amount = septemberPayment.amount,
                    sourceTransactionId = septemberPayment.id,
                    recurrence = CommitmentRecurrence.MONTHLY,
                    transactionDate = LocalDate.parse("2026-09-29"),
                ),
            ),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(afterPeriod, octoberPayment, beforePeriod, septemberPayment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(listOf(septemberPayment.id, octoberPayment.id), overview.rows.map { it.paymentTransactionId })
        assertEquals(listOf(septemberPayment.amount, octoberPayment.amount), overview.rows.map { it.amount })
        assertEquals(
            listOf(LocalDate.parse("2026-09-29"), LocalDate.parse("2026-10-21")),
            overview.rows.map { it.expectedDate },
        )
        assertTrue(overview.rows.all { it.status == CommitmentPaymentStatus.PAID })
        assertEquals(Money.of("697.92", Currency.SAR), overview.total)
        assertEquals(overview.total, overview.paid)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
    }

    @Test
    fun recurringCommitment_keepsSeparatePaymentsOnSameDayWithUniqueKeys() {
        val firstPayment = transaction(
            id = "first-payment",
            merchant = "Subscription",
            amount = "100",
            at = Instant.parse("2026-08-01T10:00:00Z"),
        )
        val secondPayment = firstPayment.copy(id = "second-payment", amount = Money.of("150", Currency.SAR))
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(
                commitment(
                    name = "Subscription",
                    amount = firstPayment.amount,
                    sourceTransactionId = firstPayment.id,
                    recurrence = CommitmentRecurrence.MONTHLY,
                ),
            ),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(firstPayment, secondPayment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(2, overview.rows.size)
        assertEquals(2, overview.rows.map { it.key }.distinct().size)
        assertEquals(Money.of("250", Currency.SAR), overview.paid)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
    }

    private fun buildWith(commitments: List<Commitment>, transactions: List<FinancialTransaction>,
        history: List<FinancialTransaction> = emptyList()) = CommitmentsOverviewBuilder.build(
        salaryPeriod = period,
        commitments = commitments,
        creditFacilities = CreditFacilitiesOverview(facilities = emptyList(), debitCards = emptyList(), currency = Currency.SAR),
        loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
        transactions = transactions,
        primaryCurrency = Currency.SAR,
        sarEquivalents = emptyMap(),
        zoneId = zone,
        paymentHistory = history,
    )

    @Test
    fun subscription_expectsWhatItLastCost_andShowsThePriceChange() {
        val first = transaction("tx-claude-1", "Anthropic", "400", Instant.parse("2026-06-05T09:00:00Z"))
        val second = transaction("tx-claude-2", "Anthropic", "100", Instant.parse("2026-07-05T09:00:00Z"))
        val claude = commitment("Anthropic", Money.of("400.00", Currency.SAR), first.id, CommitmentRecurrence.MONTHLY,
            transactionDate = LocalDate.parse("2026-06-05"), updatedAt = Instant.parse("2026-06-05T10:00:00Z"))

        val row = buildWith(listOf(claude), listOf(first), history = listOf(second)).rows.single()

        assertEquals(CommitmentPaymentStatus.UNPAID, row.status)
        assertEquals(Money.of("100.00", Currency.SAR), row.amount)
        assertEquals(Money.of("400.00", Currency.SAR), row.previousAmount)
    }

    @Test
    fun subscription_smallExchangeRateMoves_areNotAPriceChange() {
        val first = transaction("tx-gpt-1", "OpenAI", "77.40", Instant.parse("2026-06-10T09:00:00Z"))
        val second = transaction("tx-gpt-2", "OpenAI", "78.10", Instant.parse("2026-07-10T09:00:00Z"))
        val gpt = commitment("OpenAI", Money.of("77.40", Currency.SAR), first.id, CommitmentRecurrence.MONTHLY,
            transactionDate = LocalDate.parse("2026-06-10"), updatedAt = Instant.parse("2026-06-10T10:00:00Z"))

        val row = buildWith(listOf(gpt), listOf(first), history = listOf(second)).rows.single()

        assertEquals(Money.of("78.10", Currency.SAR), row.amount)
        assertEquals(null, row.previousAmount)
    }

    @Test
    fun subscription_anAmountSavedAfterTheLastCharge_wins() {
        val first = transaction("tx-claude-1", "Anthropic", "400", Instant.parse("2026-07-05T09:00:00Z"))
        val claude = commitment("Anthropic", Money.of("100.00", Currency.SAR), first.id, CommitmentRecurrence.MONTHLY,
            transactionDate = LocalDate.parse("2026-07-05"), updatedAt = Instant.parse("2026-07-20T10:00:00Z"))

        val row = buildWith(listOf(claude), listOf(first)).rows.single()

        assertEquals(Money.of("100.00", Currency.SAR), row.amount)
    }

    @Test
    fun rows_listWhatIsLeftToPayNearestFirst_thenUndated_thenPaid() {
        val rows = listOf(
            CommitmentDashboardRow("a", CommitmentDashboardSource.USER, "A paid", Money.of("1.00", Currency.SAR),
                LocalDate.parse("2026-08-01"), CommitmentPaymentStatus.PAID),
            CommitmentDashboardRow("b", CommitmentDashboardSource.LOAN, "B undated", Money.of("1.00", Currency.SAR),
                null, CommitmentPaymentStatus.UNPAID),
            CommitmentDashboardRow("c", CommitmentDashboardSource.USER, "C later", Money.of("1.00", Currency.SAR),
                LocalDate.parse("2026-08-21"), CommitmentPaymentStatus.UNPAID),
            CommitmentDashboardRow("d", CommitmentDashboardSource.USER, "D soon", Money.of("1.00", Currency.SAR),
                LocalDate.parse("2026-08-06"), CommitmentPaymentStatus.UNPAID),
        ).sortedWith(DISPLAY_ORDER)

        assertEquals(listOf("d", "c", "b", "a"), rows.map { it.key })
        assertEquals(5L, rows.first().daysUntil(LocalDate.parse("2026-08-01")))
        assertEquals(-1L, rows.first().daysUntil(LocalDate.parse("2026-08-07")))
    }

    private fun commitment(
        name: String,
        amount: Money,
        sourceTransactionId: String,
        recurrence: CommitmentRecurrence? = null,
        active: Boolean = true,
        transactionDate: LocalDate = LocalDate.parse("2026-08-01"),
        id: String = RegistryEntityIdFactory.newCommitmentId(),
        updatedAt: Instant = Instant.parse("2026-08-01T00:00:00Z"),
        pauseIntervals: List<CommitmentPauseInterval> = emptyList(),
    ): Commitment {
        val now = Instant.parse("2026-08-01T00:00:00Z")
        return Commitment(
            id = id,
            name = name,
            amount = amount,
            transactionDate = transactionDate,
            recurrence = recurrence,
            dueDate = null,
            active = active,
            pauseIntervals = pauseIntervals,
            sourceTransactionId = sourceTransactionId,
            createdAt = now,
            updatedAt = updatedAt,
        )
    }

    private fun transaction(
        id: String,
        merchant: String,
        amount: String,
        at: Instant,
        type: FinancialTransactionType = FinancialTransactionType.EXPENSE,
        destinationContainerId: String? = null,
    ): FinancialTransaction =
        FinancialTransaction(
            id = id,
            type = type,
            amount = Money.of(amount, Currency.SAR),
            occurredAt = at,
            sourceContainerId = null,
            destinationContainerId = destinationContainerId,
            merchant = merchant,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = emptyList(),
        )

    private fun creditFacility(
        due: Money,
        last4: String,
    ): CreditFacilityOverview =
        CreditFacilityOverview(
            bank = Bank.BANK_ALJAZIRA,
            primary = CreditCardDashboardRow(
                bank = Bank.BANK_ALJAZIRA,
                last4 = last4,
                statementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
                salaryPeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
                statementPeriodLabel = null,
                snapshot = null,
            ),
            supplementaries = emptyList(),
            facilityDue = StatementDueSnapshot(
                amount = due,
                updatedAt = Instant.parse("2026-07-25T00:00:00Z"),
                dueDate = LocalDate.parse("2026-08-15"),
            ),
            facilitySalaryPeriodSpending = SignedMoneyAmount.zero(Currency.SAR),
            facilityStatementSpending = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementPeriodLabel = null,
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )
}
