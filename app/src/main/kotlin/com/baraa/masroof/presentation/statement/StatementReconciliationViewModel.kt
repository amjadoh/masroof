package com.baraa.masroof.presentation.statement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.application.statement.LedgerLineComparison
import com.baraa.masroof.application.statement.StatementCurrencyTotal
import com.baraa.masroof.application.statement.StatementImportResult
import com.baraa.masroof.application.statement.StatementLineComparison
import com.baraa.masroof.application.statement.StatementEntryPosting
import com.baraa.masroof.application.statement.StatementReconciliationWorkflow
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.statement.ParsedBankStatement
import com.baraa.masroof.domain.statement.StatementComparisonStatus
import com.baraa.masroof.domain.statement.StatementRejection
import java.io.InputStream
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatementReconciliationUiState(
    val busy: Boolean = false,
    val rejection: StatementRejection? = null,
    val unreadable: Boolean = false,
    val hasReport: Boolean = false,
    val matchedCount: Int = 0,
    val statementOnlyCount: Int = 0,
    val ledgerOnlyCount: Int = 0,
    val ambiguousCount: Int = 0,
    val unsupportedCount: Int = 0,
    val matchedFleetPaymentCount: Int = 0,
    val matchedSelfTransferCount: Int = 0,
    val balanceUnsupported: Boolean = false,
    val coverageStart: String = "",
    val coverageEnd: String = "",
    val totals: List<StatementTotalUi> = emptyList(),
    val matchedLines: List<StatementLineUi> = emptyList(),
    val statementOnlyLines: List<StatementLineUi> = emptyList(),
    val ledgerOnlyLines: List<StatementLineUi> = emptyList(),
    val ambiguousLines: List<StatementLineUi> = emptyList(),
    val unsupportedLines: List<StatementLineUi> = emptyList(),
    /** Statement openings the user may start an account's balance from. */
    val openings: List<StatementOpeningUi> = emptyList(),
)

data class StatementOpeningUi(
    val bankId: String,
    val accountMasked: String,
    val amountLabel: String,
    val firstDay: String,
    val adopted: Boolean = false,
)

data class StatementTotalUi(
    val bankId: String,
    val accountMasked: String,
    val periodStart: String,
    val periodEnd: String,
    val currencyCode: String,
    val matchedDebit: String,
    val matchedCredit: String,
    val statementOnlyDebit: String,
    val statementOnlyCredit: String,
    val ledgerOnlyDebit: String,
    val ledgerOnlyCredit: String,
)

data class StatementLineUi(
    val title: String,
    val amountLabel: String,
    val detail: String,
    /** Statement line number of a statement-only movement the user can post; null otherwise. */
    val postableLine: Int? = null,
    val posted: Boolean = false,
)

fun statementReconciliationUiState(result: StatementImportResult): StatementReconciliationUiState =
    when (result) {
        is StatementImportResult.Rejected ->
            StatementReconciliationUiState(
                rejection = result.reason,
                unreadable = result.reason == StatementRejection.UNREADABLE,
            )

        is StatementImportResult.Compared -> {
            val report = result.report
            StatementReconciliationUiState(
                hasReport = true,
                matchedCount = report.counts.matched,
                statementOnlyCount = report.counts.statementOnly,
                ledgerOnlyCount = report.counts.ledgerOnly,
                ambiguousCount = report.counts.ambiguous,
                unsupportedCount = report.counts.unsupported,
                matchedFleetPaymentCount = report.matchedFleetPaymentCount,
                matchedSelfTransferCount = report.matchedSelfTransferCount,
                balanceUnsupported = report.balanceCheck == StatementComparisonStatus.UNSUPPORTED,
                coverageStart = report.coverage.periodStart.toString(),
                coverageEnd = report.coverage.periodEnd.toString(),
                totals = report.totals.map { it.toUi() },
                matchedLines = report.statementLines
                    .filter { it.status == StatementComparisonStatus.MATCHED }
                    .map { it.toUi() },
                statementOnlyLines = report.statementLines
                    .filter { it.status == StatementComparisonStatus.STATEMENT_ONLY }
                    .map { it.toUi().copy(postableLine = it.entry.lineNumber.takeIf { result.statement != null }) },
                ledgerOnlyLines = report.ledgerLines
                    .filter { it.status == StatementComparisonStatus.LEDGER_ONLY }
                    .map { it.toUi() },
                ambiguousLines = report.statementLines
                    .filter { it.status == StatementComparisonStatus.AMBIGUOUS }
                    .map { it.toUi() } +
                    report.ledgerLines
                        .filter { it.status == StatementComparisonStatus.AMBIGUOUS }
                        .map { it.toUi() },
                unsupportedLines = report.statementLines
                    .filter { it.status == StatementComparisonStatus.UNSUPPORTED }
                    .map { it.toUi() } +
                    report.ledgerLines
                        .filter { it.status == StatementComparisonStatus.UNSUPPORTED }
                        .map { it.toUi() },
                openings = result.statement?.let { statement ->
                    statement.balances.mapNotNull { balance ->
                        val opening = balance.opening ?: return@mapNotNull null
                        StatementOpeningUi(balance.bank.id, balance.accountMasked,
                            plain(opening.amount, opening.currency.name), statement.coverage.periodStart.toString())
                    }
                }.orEmpty(),
            )
        }
    }

class StatementReconciliationViewModel(
    private val workflow: StatementReconciliationWorkflow,
    private val posting: StatementEntryPosting? = null,
) : ViewModel() {
    private var statement: ParsedBankStatement? = null
    private val _uiState = MutableStateFlow(StatementReconciliationUiState())
    val uiState: StateFlow<StatementReconciliationUiState> = _uiState.asStateFlow()
    private var comparison: kotlinx.coroutines.Job? = null
    private var selectedBankId: String? = null

    fun selectBank(bankId: String?) {
        if (selectedBankId == bankId) return
        selectedBankId = bankId
        comparison?.cancel()
        statement = null
        _uiState.value = StatementReconciliationUiState()
    }

    fun compare(stream: InputStream, bankId: String? = null) {
        selectBank(bankId)
        comparison?.cancel()
        comparison = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, rejection = null, unreadable = false)
            val result = try {
                withContext(Dispatchers.IO) {
                    stream.use { workflow.compare(it, bankId) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: Exception) {
                StatementImportResult.Rejected(StatementRejection.UNREADABLE)
            }
            // Without posting there is nothing to tap, so the statement itself is not kept.
            val shown = if (posting == null && result is StatementImportResult.Compared) result.copy(statement = null) else result
            statement = (shown as? StatementImportResult.Compared)?.statement
            _uiState.value = statementReconciliationUiState(shown)
        }
    }

    /** Posts one statement-only line on the user's tap. */
    fun postLine(lineNumber: Int) {
        val current = statement ?: return
        val entry = current.entries.find { it.lineNumber == lineNumber } ?: return
        val posting = posting ?: return
        viewModelScope.launch {
            val saved = try {
                withContext(Dispatchers.IO) { posting.post(current, entry) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: Exception) {
                false
            }
            if (saved) {
                _uiState.value = _uiState.value.copy(
                    statementOnlyLines = _uiState.value.statementOnlyLines.map {
                        if (it.postableLine == lineNumber) it.copy(posted = true) else it
                    },
                )
            }
        }
    }

    fun adoptOpening(bankId: String, accountMasked: String) {
        val current = statement ?: return
        val posting = posting ?: return
        val balance = current.balances.find { it.bank.id == bankId && it.accountMasked == accountMasked } ?: return
        val opening = balance.opening ?: return
        posting.adoptOpening(
            AccountReference(balance.bank, balance.accountMasked),
            SignedMoneyAmount(opening.amount, opening.currency),
            current.coverage.periodStart,
        )
        _uiState.value = _uiState.value.copy(
            openings = _uiState.value.openings.map {
                if (it.bankId == bankId && it.accountMasked == accountMasked) it.copy(adopted = true) else it
            },
        )
    }

    fun onUnreadable() {
        statement = null
        _uiState.value = StatementReconciliationUiState(unreadable = true)
    }
}

fun statementReconciliationViewModelFactory(
    workflow: StatementReconciliationWorkflow,
    posting: StatementEntryPosting? = null,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        StatementReconciliationViewModel(workflow, posting)
    }
}

private fun StatementCurrencyTotal.toUi(): StatementTotalUi =
    StatementTotalUi(
        bankId = bankId,
        accountMasked = accountMasked,
        periodStart = periodStart.toString(),
        periodEnd = periodEnd.toString(),
        currencyCode = currency.name,
        matchedDebit = plain(matchedDebit, currency.name),
        matchedCredit = plain(matchedCredit, currency.name),
        statementOnlyDebit = plain(statementOnlyDebit, currency.name),
        statementOnlyCredit = plain(statementOnlyCredit, currency.name),
        ledgerOnlyDebit = plain(ledgerOnlyDebit, currency.name),
        ledgerOnlyCredit = plain(ledgerOnlyCredit, currency.name),
    )

private fun StatementLineComparison.toUi(): StatementLineUi =
    StatementLineUi(
        title = entry.description,
        amountLabel = plain(entry.amount.amount, entry.amount.currency.name),
        detail = "${entry.bank.id} · ${entry.accountMasked} · ${entry.bookedDate} · ${entry.direction.name}",
    )

private fun LedgerLineComparison.toUi(): StatementLineUi =
    StatementLineUi(
        title = transactionId,
        amountLabel = plain(amount, currency.name),
        detail = "${bank.id} · $accountMasked · $civilDate · ${direction.name}",
    )

private fun plain(amount: BigDecimal, currencyCode: String): String =
    amount.stripTrailingZeros().let { stripped ->
        val scaled = if (stripped.scale() < 2) amount.setScale(2) else amount
        scaled.toPlainString() + " " + currencyCode
    }
