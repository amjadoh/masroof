package com.baraa.masroof.presentation.patterns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.patterns.BankMessageAccountSetup
import com.baraa.masroof.application.patterns.MessagePatternsWorkflow
import com.baraa.masroof.presentation.common.*
import com.baraa.masroof.presentation.common.MasroofText as Text
import com.baraa.masroof.ui.theme.MasroofSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A saved, explicit account choice; opening or dismissing this sheet changes nothing. */
@Composable
fun BankMessageAccountSetupCard(workflow: MessagePatternsWorkflow, bankId: String, bankName: String,
    generation: Long, onChanged: () -> Unit) {
    if (bankId !in setOf("BANK_SNB", "BANK_BARQ", "BANK_EMIRATES_NBD", "BANK_SAB", "BANK_ALINMA")) return
    var setup by remember(bankId) { mutableStateOf<BankMessageAccountSetup?>(null) }
    var open by remember(bankId) { mutableStateOf(false) }
    var selected by remember(bankId) { mutableStateOf<String?>(null) }
    var currency by remember(bankId) { mutableStateOf("SAR") }
    var personalLoan by remember(bankId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(workflow, bankId, generation) {
        try { setup = workflow.bankAccountSetup(bankId) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
    }
    MasroofCard {
        MasroofGuidanceLabel(stringResource(R.string.bank_message_account_title, bankName),
            stringResource(R.string.bank_message_account_hint), style = MaterialTheme.typography.titleMedium)
        setup?.let { current ->
            current.accounts.singleOrNull { it.id == current.selectedAccountId }?.let {
                Text(stringResource(R.string.bank_message_account_selected, it.last4))
            }
        }
        MasroofOutlinedButton(onClick = {
            setup?.let { current -> selected = current.selectedAccountId; currency = current.currency; personalLoan = current.personalLoan }
            failed = false; open = true
        }, modifier = Modifier.fillMaxWidth().testTag("bank-message-setup-$bankId"), enabled = setup != null && !busy) {
            Text(stringResource(R.string.bank_message_account_choose))
        }
        if (failed && !open) Text(stringResource(R.string.bank_message_account_failed), color = MaterialTheme.colorScheme.error)
    }
    if (open) MasroofBottomSheet(stringResource(R.string.bank_message_account_title, bankName),
        onDismiss = { open = false }, onApply = {
            if (!busy && selected != null) {
                busy = true; failed = false
                scope.launch {
                    try {
                        workflow.configureBankAccount(generation, bankId, requireNotNull(selected), currency, personalLoan)
                        open = false; onChanged()
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { failed = true; onChanged() }
                    finally { busy = false }
                }
            }
        }, applyLabel = stringResource(R.string.bank_message_account_save), busy = busy,
        canApply = selected != null && setup?.accounts?.any { it.id == selected } == true) {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
            Text(stringResource(R.string.bank_message_account_explanation))
            val options = setup?.accounts.orEmpty()
            if (options.isEmpty()) Text(stringResource(R.string.bank_message_account_empty))
            MasroofSheetChoices(options.map { it.id }, setOfNotNull(selected), { id ->
                val account = options.single { it.id == id }
                listOfNotNull(account.name, "****${account.last4}").joinToString(" ")
            }, { if (!busy) selected = it }, { "bank-message-account-$it" })
            Text(stringResource(R.string.bank_message_account_currency), style = MaterialTheme.typography.titleSmall)
            MasroofSheetChoices(listOf("SAR", "USD", "EUR", "GBP"), setOf(currency), { it }, { if (!busy) currency = it }, { "bank-message-currency-$it" })
            if (bankId in setOf("BANK_EMIRATES_NBD", "BANK_SNB")) {
                Text(stringResource(R.string.bank_message_account_loan))
                MasroofSheetChoices(listOf(false, true), setOf(personalLoan),
                    { stringResource(if (it) R.string.bank_message_account_loan_yes else R.string.bank_message_account_loan_no) },
                    { if (!busy) personalLoan = it }, { "bank-message-personal-loan-$it" })
            }
            if (failed) Text(stringResource(R.string.bank_message_account_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}
