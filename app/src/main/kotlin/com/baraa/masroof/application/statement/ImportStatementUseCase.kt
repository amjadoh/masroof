package com.baraa.masroof.application.statement

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.statement.AccountStatementParser
import com.baraa.masroof.domain.statement.ParsedBankStatement
import com.baraa.masroof.domain.statement.StatementMatchPolicy
import com.baraa.masroof.domain.statement.StatementParseResult
import com.baraa.masroof.domain.statement.StatementParser
import com.baraa.masroof.domain.statement.StatementRejection
import java.io.InputStream

/**
 * Validates a canonical CSV or a bank PDF, then compares it. A rejected file never
 * reaches [StatementReconciliationService] and therefore never reads or writes Room.
 */
class ImportStatementUseCase(
    private val parser: StatementParser,
    private val reconciliation: StatementReconciliationService,
    private val pdfParser: AccountStatementParser? = null,
) {
    suspend fun import(
        stream: InputStream,
        knownBankIds: Set<String>,
        ownedAccounts: Set<StatementMatchPolicy.QualifiedAccount>,
    ): StatementImportResult {
        val input = stream.buffered()
        val parsed = if (pdfParser != null && isPdf(input)) {
            pdfParser.parse(input, ownedAccounts)
        } else {
            parser.parse(input, knownBankIds)
        }
        return when (parsed) {
            is StatementParseResult.Rejected -> StatementImportResult.Rejected(parsed.reason)
            is StatementParseResult.Accepted if !accountsAreExactOwned(parsed.statement, ownedAccounts) ->
                StatementImportResult.Rejected(StatementRejection.UNOWNED_ACCOUNT)
            is StatementParseResult.Accepted ->
                StatementImportResult.Compared(reconciliation.compare(parsed.statement), parsed.statement)
        }
    }

    private fun isPdf(input: java.io.BufferedInputStream): Boolean {
        input.mark(PDF_MAGIC.size)
        val head = ByteArray(PDF_MAGIC.size)
        val read = input.readNBytes(head, 0, head.size)
        input.reset()
        return read == head.size && head.contentEquals(PDF_MAGIC)
    }

    private companion object {
        val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    }
}

/**
 * Every statement account must be an exact owned registry identity.
 * A shared suffix on another bank, or a longer mask that merely ends with the
 * same digits, is not that account. External and unknown rows are omitted from
 * [ownedAccounts] by the caller.
 */
internal fun accountsAreExactOwned(
    statement: ParsedBankStatement,
    ownedAccounts: Set<StatementMatchPolicy.QualifiedAccount>,
): Boolean {
    val owned = ownedAccounts
        .filter { it.bank != Bank.UNKNOWN && it.accountMasked.isNotBlank() }
        .map { StatementMatchPolicy.QualifiedAccount(it.bank, it.accountMasked.trim()) }
        .toSet()
    val mentioned = statement.accounts.map { StatementMatchPolicy.QualifiedAccount(it.bank, it.accountMasked.trim()) } +
        statement.entries.map { StatementMatchPolicy.QualifiedAccount(it.bank, it.accountMasked.trim()) } +
        statement.balances.map { StatementMatchPolicy.QualifiedAccount(it.bank, it.accountMasked.trim()) }
    return mentioned.isNotEmpty() && mentioned.all { it in owned }
}

sealed interface StatementImportResult {
    data class Compared(
        val report: StatementReconciliationReport,
        val statement: ParsedBankStatement? = null,
    ) : StatementImportResult

    data class Rejected(val reason: StatementRejection) : StatementImportResult
}
