package com.baraa.masroof.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baraa.masroof.application.credit.CardCompanionWorkflow
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.domain.model.Bank
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CardCompanionUiState(
    val cardKey: String? = null,
    val loading: Boolean = false,
    val snapshot: CardCompanionWorkflow.Snapshot? = null,
    val failed: Boolean = false,
)

/** Drives the SAB-Visa-style companion on a credit card's detail screen. */
class CardCompanionViewModel(
    private val workflow: CardCompanionWorkflow,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CardCompanionUiState())
    val uiState: StateFlow<CardCompanionUiState> = _uiState.asStateFlow()

    private var lastRow: CreditCardDashboardRow? = null

    fun load(row: CreditCardDashboardRow) {
        lastRow = row
        val key = CardCompanionWorkflow.cardKey(row.bank, row.last4)
        val keep = _uiState.value.snapshot?.takeIf { it.cardKey == key }
        _uiState.value = CardCompanionUiState(cardKey = key, loading = true, snapshot = keep)
        viewModelScope.launch {
            val snapshot = try {
                withContext(Dispatchers.IO) { workflow.load(row.bank, row.last4, row.toFacts()) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: Exception) {
                null
            }
            if (_uiState.value.cardKey != key) return@launch
            _uiState.value = CardCompanionUiState(cardKey = key, snapshot = snapshot ?: keep, failed = snapshot == null)
        }
    }

    /** Accepts Arabic or Latin digits; returns false when the text is not a positive amount. */
    fun setLimit(text: String): Boolean {
        val row = lastRow ?: return false
        val value = parseAmount(text) ?: return false
        if (value.signum() <= 0) return false
        mutate { workflow.setLimit(row.bank, row.last4, value) }
        return true
    }

    fun setCloseDay(day: Int) {
        val row = lastRow ?: return
        mutate { workflow.setCloseDay(row.bank, row.last4, day) }
    }

    fun renamePlan(planId: String, name: String) {
        val row = lastRow ?: return
        if (name.isBlank()) return
        mutate { workflow.renamePlan(row.bank, row.last4, planId, name) }
    }

    fun dismissPlan(planId: String) {
        val row = lastRow ?: return
        mutate { workflow.dismissPlan(row.bank, row.last4, planId) }
    }

    private fun mutate(change: () -> Unit) {
        val row = lastRow ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { change() } }
            load(row)
        }
    }

    private fun CreditCardDashboardRow.toFacts() = CardCompanionWorkflow.SmsFacts(
        available = snapshot?.availableBalance,
        negativeAvailable = snapshot?.negativeAvailableBalance,
        availableAt = snapshot?.availableUpdatedAt,
        statementDueDate = snapshot?.dueDate,
    )

    companion object {
        fun parseAmount(text: String): BigDecimal? {
            val latin = text.trim().map { c ->
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    '٫' -> '.'
                    '٬', ',' -> ' '
                    else -> c
                }
            }.joinToString("").replace(" ", "")
            if (!Regex("[0-9]{1,10}(\\.[0-9]{1,2})?").matches(latin)) return null
            return BigDecimal(latin).setScale(2)
        }

        fun isSab(bank: Bank): Boolean = bank.id == CardCompanionWorkflow.SAB_BANK_ID
    }
}

fun cardCompanionViewModelFactory(workflow: CardCompanionWorkflow): ViewModelProvider.Factory = viewModelFactory {
    initializer { CardCompanionViewModel(workflow) }
}
