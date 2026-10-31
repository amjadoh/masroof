package com.baraa.masroof.presentation.onboarding

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.onboarding.HistoricalImportGateway
import com.baraa.masroof.application.onboarding.OnboardingPreferencesRepository

fun onboardingViewModelFactory(
    container: AppContainer,
    onboardingPreferencesRepository: OnboardingPreferencesRepository,
    permissionStateProvider: () -> Boolean,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        OnboardingViewModel(
            onboardingPrefs = onboardingPreferencesRepository,
            historicalImportGateway = HistoricalImportGateway { request ->
                container.bankSmsImportWorkflow.import(request.bankIds, request.from, request.through, request.onProgress)
            },
            messagePatternsWorkflow = container.messagePatternsWorkflow,
            onboardingOwnershipWorkflow = container.onboardingOwnershipWorkflow,
            refreshReviewQueue = { container.refreshReviewQueue() },
            reconcileOwnershipChange = { change ->
                container.reviewWorkflowService.reconcileOwnershipChange(change)
            },
            databaseBackupService = container.databaseBackupService,
            permissionStateProvider = permissionStateProvider,
            appLogService = container.appLogService,
        )
    }
}
