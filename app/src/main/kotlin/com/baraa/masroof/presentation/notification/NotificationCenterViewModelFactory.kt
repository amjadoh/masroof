package com.baraa.masroof.presentation.notification

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baraa.masroof.application.AppContainer

fun notificationCenterViewModelFactory(
    container: AppContainer,
    permissionStateProvider: () -> Boolean,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        NotificationCenterViewModel(
            notificationCenterMetricsWorkflow = container.notificationCenterMetricsWorkflow,
            notificationCenterService = container.notificationCenterService,
            permissionStateProvider = permissionStateProvider,
        )
    }
}
