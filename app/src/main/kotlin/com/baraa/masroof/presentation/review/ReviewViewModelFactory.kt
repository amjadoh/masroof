package com.baraa.masroof.presentation.review

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.review.ReviewDetailLoader

fun reviewViewModelFactory(
    container: AppContainer,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val loader = ReviewDetailLoader(
            reviewWorkflowService = container.reviewWorkflowService,
            rawSmsRepository = container.rawSmsRepository,
            effectiveParsedEventProvider = container.effectiveParsedEventProvider,
            provenanceWorkflow = container.interpretationProvenanceWorkflow,
        )
        ReviewViewModel(
            messagePatternsWorkflow = container.messagePatternsWorkflow,
            reviewWorkflowService = container.reviewWorkflowService,
            detailLoader = loader,
            reviewOwnershipWorkflow = container.reviewOwnershipWorkflow,
            transactionRestoreService = container.transactionRestoreService,
            // reparseStored already reconciles and refreshes reviews for this one message.
            reparseStoredSms = { rawSmsId ->
                container.rawSmsRepository.getById(rawSmsId)?.let { container.processRawSmsUseCase.reparseStored(it) }
            },
            selectableBanks = container.explicitBankSelectionWorkflow.selectableBanks(),
            selectBankAndReparse = { rawSmsId, bankId ->
                container.explicitBankSelectionWorkflow.selectAndReparse(rawSmsId, bankId)
            },
            appLocaleRepository = container.appLocaleRepository,
            maintenanceCompletions = container.maintenanceCompletionSignal.completions,
            appLogService = container.appLogService,
        )
    }
}
