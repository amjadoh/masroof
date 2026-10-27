package com.baraa.masroof.application.statement

import com.baraa.masroof.domain.statement.StatementMatchPolicy
import java.io.InputStream

/**
 * User-initiated, read-only statement comparison.
 *
 * The caller supplies an open stream. This workflow does not take a bank
 * credential, call a bank API, or copy the statement into an unencrypted file.
 */
class StatementReconciliationWorkflow(
    private val importStatement: ImportStatementUseCase,
    private val knownBankIds: suspend () -> Set<String>,
    private val ownedAccounts: suspend () -> Set<StatementMatchPolicy.QualifiedAccount>,
) {
    suspend fun compare(stream: InputStream, bankId: String? = null): StatementImportResult {
        val known = knownBankIds().filter { bankId == null || it == bankId }.toSet()
        val owned = ownedAccounts().filter { bankId == null || it.bank.id == bankId }.toSet()
        return importStatement.import(stream, known, owned)
    }
}
