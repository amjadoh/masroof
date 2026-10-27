package com.baraa.masroof.presentation.onboarding

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.onboarding.HistoricalImportGateway
import com.baraa.masroof.application.onboarding.OnboardingPreferencesRepository
import com.baraa.masroof.application.onboarding.toHistoricalImportResult

fun onboardingViewModelFactory(
    container: AppContainer,
    onboardingPreferencesRepository: OnboardingPreferencesRepository,
    permissionStateProvider: () -> Boolean,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        OnboardingViewModel(
            onboardingPrefs = onboardingPreferencesRepository,
            historicalImportGateway = HistoricalImportGateway { after ->
                container.historicalSmsScanner.scan(after).toHistoricalImportResult()
            },
            onboardingOwnershipWorkflow = container.onboardingOwnershipWorkflow,
            discoverFromStoredEvents = { container.discoverFromStoredEvents() },
            refreshReviewQueue = { container.refreshReviewQueue() },
            reconcileOwnershipChange = { change ->
                container.reviewWorkflowService.reconcileOwnershipChange(change)
            },
            databaseBackupService = container.databaseBackupService,
            permissionStateProvider = permissionStateProvider,
        )
    }
}
