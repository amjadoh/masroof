package com.baraa.masroof.application.statement

import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.application.settings.AccountBalanceSettingsWorkflow
import com.baraa.masroof.application.settings.StatedAccountBalance
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.FinancialTransactionSaveResult
import com.baraa.masroof.domain.statement.BankStatementEntry
import com.baraa.masroof.domain.statement.ParsedBankStatement
import com.baraa.masroof.domain.statement.StatementDirection
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The two writes a statement comparison may make, each only on the user's tap: post a
 * statement line that no SMS reported, or start the account's balance at the statement's
 * opening. A posted line is an ordinary transaction with no SMS link; posting the same
 * line again (from the same or a re-imported statement) changes nothing.
 */
class StatementEntryPosting(
    private val transactions: FinancialTransactionRepository,
    private val balances: AccountBalanceSettingsWorkflow,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun post(statement: ParsedBankStatement, entry: BankStatementEntry): Boolean {
        val account = FinancialContainerIdFactory.accountId(entry.bank, entry.accountMasked)
        val debit = entry.direction == StatementDirection.DEBIT
        val tx = FinancialTransaction(
            id = transactionId(statement, entry),
            type = if (debit) FinancialTransactionType.EXPENSE else FinancialTransactionType.INCOME,
            amount = entry.amount,
            occurredAt = (entry.bookedAtTime ?: entry.bookedDate.atTime(NOON)).atZone(zoneId).toInstant(),
            sourceContainerId = account.takeIf { debit },
            destinationContainerId = account.takeUnless { debit },
            merchant = readable(entry.description).take(MERCHANT_LENGTH).ifBlank { null },
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = emptyList(),
            occurredAtZone = zoneId.id,
        )
        return when (transactions.saveWithoutSms(tx)) {
            is FinancialTransactionSaveResult.Conflict -> false
            else -> true
        }
    }

    /** The account's balance starts at the statement's opening, before its first day's movement. */
    fun adoptOpening(account: AccountReference, opening: SignedMoneyAmount, firstDay: LocalDate) {
        balances.adopt(account, StatedAccountBalance(opening, firstDay.atStartOfDay(zoneId).toInstant()))
    }

    companion object {
        private val NOON: LocalTime = LocalTime.NOON
        private const val MERCHANT_LENGTH = 80

        /**
         * Same line, same id: bank, account, day, direction, amount and text, plus its place
         * among identical lines of the statement so two equal withdrawals stay two.
         */
        fun transactionId(statement: ParsedBankStatement, entry: BankStatementEntry): String {
            fun key(e: BankStatementEntry) = listOf(e.bank.id, e.accountMasked.trim(), e.bookedDate, e.direction,
                e.amount.amount.toPlainString(), e.amount.currency, readable(e.description)).joinToString("|")
            val own = key(entry)
            val occurrence = statement.entries.filter { key(it) == own }.indexOfFirst { it.lineNumber == entry.lineNumber }
            val digest = MessageDigest.getInstance("SHA-256").digest("$own|$occurrence".toByteArray())
            return "statement:" + digest.joinToString("") { "%02x".format(it) }
        }

        /** PDF text keeps Arabic presentation forms; folding them gives plain, searchable letters. */
        fun readable(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFKC).replace(Regex("\\s+"), " ").trim()
    }
}
