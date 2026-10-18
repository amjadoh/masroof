package com.baraa.masroof.lab.beta.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.lab.beta.R
import com.baraa.masroof.lab.beta.application.*
import com.baraa.masroof.lab.finance.value.BankId
import com.baraa.masroof.ui.components.MasroofCard
import com.baraa.masroof.ui.theme.MasroofSpacing

@Composable
internal fun BetaOnboardingPanel(step: BetaOnboardingStep, banks: List<BetaBankChoice>, busy: Boolean, failed: Boolean,
    onChooseBank: (BankId) -> Unit, onNext: () -> Unit, onHistory: () -> Unit, onDiscovery: () -> Unit,
    onManage: () -> Unit, onApproval: () -> Unit, onFinancialReview: () -> Unit) {
    Column(Modifier.testTag("onboarding-${step.name}"), verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap)) {
        Text(stringResource(R.string.beta_onboarding_progress, step.ordinal + 1, 4), style = MaterialTheme.typography.labelLarge)
        val title = when (step) {
            BetaOnboardingStep.BANK -> R.string.beta_onboarding_bank
            BetaOnboardingStep.IMPORT -> R.string.beta_onboarding_import
            BetaOnboardingStep.ASSETS -> R.string.beta_onboarding_assets
            else -> R.string.beta_onboarding_review
        }
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        MasroofCard {
            Text(stringResource(when (step) {
                BetaOnboardingStep.BANK -> R.string.beta_onboarding_bank_help
                BetaOnboardingStep.IMPORT -> R.string.beta_onboarding_import_help
                BetaOnboardingStep.ASSETS -> R.string.beta_onboarding_assets_help
                else -> R.string.beta_onboarding_review_help
            }))
        }
        when (step) {
            BetaOnboardingStep.BANK -> banks.forEach { bank ->
                Button(enabled = !busy, onClick = { onChooseBank(bank.bankId) }, modifier = Modifier.fillMaxWidth().testTag("onboarding-bank-${bank.bankId.value}")) {
                    Text(stringResource(bankName(bank.bankId)))
                }
            }
            BetaOnboardingStep.IMPORT -> OutlinedButton(enabled = !busy, onClick = onHistory, modifier = Modifier.fillMaxWidth().testTag("onboarding-import")) {
                Text(stringResource(R.string.beta_history_title))
            }
            BetaOnboardingStep.ASSETS -> {
                OutlinedButton(enabled = !busy, onClick = onDiscovery, modifier = Modifier.fillMaxWidth().testTag("onboarding-discovery")) { Text(stringResource(R.string.beta_discovery_title)) }
                OutlinedButton(enabled = !busy, onClick = onManage, modifier = Modifier.fillMaxWidth().testTag("onboarding-manage")) { Text(stringResource(R.string.beta_manage_title)) }
            }
            BetaOnboardingStep.REVIEW -> {
                OutlinedButton(enabled = !busy, onClick = onHistory, modifier = Modifier.fillMaxWidth().testTag("onboarding-labels")) { Text(stringResource(R.string.beta_label_title)) }
                OutlinedButton(enabled = !busy, onClick = onApproval, modifier = Modifier.fillMaxWidth().testTag("onboarding-approval")) { Text(stringResource(R.string.beta_approval_title)) }
                OutlinedButton(enabled = !busy, onClick = onFinancialReview, modifier = Modifier.fillMaxWidth().testTag("onboarding-review")) { Text(stringResource(R.string.beta_review_title)) }
            }
            BetaOnboardingStep.COMPLETE -> Unit
        }
        if (step != BetaOnboardingStep.BANK) Button(enabled = !busy, onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("onboarding-next")) {
            Text(stringResource(if (step == BetaOnboardingStep.REVIEW) R.string.beta_onboarding_finish else R.string.beta_onboarding_next))
        }
        if (failed) Text(stringResource(R.string.beta_action_failed), color = MaterialTheme.colorScheme.error)
    }
}
