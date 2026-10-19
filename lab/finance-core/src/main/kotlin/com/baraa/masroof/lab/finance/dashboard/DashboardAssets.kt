package com.baraa.masroof.lab.finance.dashboard

import com.baraa.masroof.lab.finance.asset.AssetId
import com.baraa.masroof.lab.finance.asset.CreditCardRole
import com.baraa.masroof.lab.finance.value.Currency
import com.baraa.masroof.lab.finance.value.Environment
import com.baraa.masroof.lab.finance.value.Money
import java.time.Instant
import java.time.LocalDate

enum class AccountCategory { CURRENT, SAVINGS, WALLET, OTHER }

data class AccountSummary(
    val id: AssetId,
    val balance: SignedAmount?,
    val label: String? = null,
    val category: AccountCategory = AccountCategory.CURRENT,
    val balanceAsOf: Instant? = null,
    val flow: AccountFlow? = null,
    val last4: String? = null,
) {
    init { require(balanceAsOf == null || balance != null) }
}

/** Debit has salary spending only, never a statement due. Unlinked cards remain visible. */
data class DebitSpending(
    val cardId: AssetId,
    val spending: SignedAmount?,
    val accountId: AssetId? = null,
    val label: String? = null,
    val last4: String? = null,
    val network: String? = null,
    val salaryPeriod: DisplayPeriod? = null,
) {
    init {
        require(accountId == null || (accountId.bank == cardId.bank && accountId.environment == cardId.environment))
        require(salaryPeriod == null || salaryPeriod.kind == PeriodKind.SALARY)
        requireLast4(last4)
    }
}

/** Due belongs to the facility once, never to each primary/supplementary member. */
data class FacilitySummary(
    val facilityId: AssetId,
    val statementDue: Money?,
    val dueDate: LocalDate? = null,
    val statementIssuedAt: Instant? = null,
    val salarySpending: SignedAmount? = null,
    val statementSpending: SignedAmount? = null,
    val cards: List<CreditCardSummary> = emptyList(),
) {
    init {
        require(dueDate == null || statementDue != null)
        require(statementIssuedAt == null || statementDue != null)
        require(cards.all { it.cardId.bank == facilityId.bank && it.cardId.environment == facilityId.environment })
        require(cards.count { it.role == CreditCardRole.PRIMARY } <= 1)
    }
}

data class PeriodSpending(val period: DisplayPeriod, val net: SignedAmount?)

data class CreditCardSummary(
    val cardId: AssetId,
    val role: CreditCardRole?,
    val label: String?,
    val last4: String?,
    val calendarSpending: PeriodSpending?,
    val salarySpending: PeriodSpending?,
    val statementSpending: PeriodSpending?,
    val availableBalance: SignedAmount?,
    val balanceAsOf: Instant?,
) {
    init {
        requireLast4(last4)
        require(calendarSpending == null || calendarSpending.period.kind == PeriodKind.CALENDAR_MONTH)
        require(salarySpending == null || salarySpending.period.kind == PeriodKind.SALARY)
        require(statementSpending == null || statementSpending.period.kind == PeriodKind.STATEMENT)
        require(balanceAsOf == null || availableBalance != null)
    }
}

data class LoanSummary(
    val id: AssetId,
    val type: String,
    val label: String,
    val remaining: Money?,
    val remainingAsOf: Instant?,
    val latestInstallment: Money?,
    val installmentAsOf: Instant?,
    val salaryPayment: SignedAmount?,
    val salaryPeriod: DisplayPeriod?,
) {
    init {
        require(type.isNotBlank())
        require(remainingAsOf == null || remaining != null)
        require(installmentAsOf == null || latestInstallment != null)
        require(salaryPeriod == null || salaryPeriod.kind == PeriodKind.SALARY)
    }
}

enum class PaymentStatus { PAID, UNPAID }

/** One occurrence per row, not one row per recurring commitment. */
data class CommitmentRow(
    val environment: Environment,
    val occurrenceId: String,
    val label: String,
    val amount: Money,
    val expectedDate: LocalDate?,
    val status: PaymentStatus,
    val userCommitmentId: String? = null,
    val facilityId: AssetId? = null,
    val loanId: AssetId? = null,
) {
    init {
        require(occurrenceId.isNotBlank())
        require(listOfNotNull(userCommitmentId, facilityId, loanId).size == 1)
        require(userCommitmentId == null || userCommitmentId.isNotBlank())
        require(listOfNotNull(facilityId, loanId).all { it.environment == environment })
    }
}

data class CommitmentsSummary(
    val currency: Currency,
    val rows: List<CommitmentRow>,
    val total: Money?,
    val paid: Money?,
    val remaining: Money?,
) {
    init {
        require(rows.map { it.occurrenceId }.distinct().size == rows.size)
        requireCurrency(currency, total, paid, remaining, *rows.map { it.amount }.toTypedArray())
    }
}

private fun requireLast4(last4: String?) {
    require(last4 == null || last4.matches(Regex("[0-9]{4}")))
}
