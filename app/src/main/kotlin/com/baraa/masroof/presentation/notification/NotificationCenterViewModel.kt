package com.baraa.masroof.presentation.notification

import androidx.lifecycle.ViewModel
import com.baraa.masroof.application.logging.AppLogCategories
import com.baraa.masroof.application.logging.AppLogService
import com.baraa.masroof.application.notification.NotificationAction
import com.baraa.masroof.application.notification.NotificationCenterMetricsWorkflow
import com.baraa.masroof.application.notification.NotificationCenterService
import com.baraa.masroof.application.notification.NotificationCenterSnapshot
import com.baraa.masroof.application.notification.NotificationItem
import com.baraa.masroof.presentation.common.launchLogged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NotificationCenterViewModel(
    private val notificationCenterMetricsWorkflow: NotificationCenterMetricsWorkflow,
    private val notificationCenterService: NotificationCenterService,
    private val permissionStateProvider: () -> Boolean,
    private val appLogService: AppLogService? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationCenterUiState())
    val uiState: StateFlow<NotificationCenterUiState> = _uiState.asStateFlow()

    private var lastExternalState: NotificationCenterExternalState = NotificationCenterExternalState()

    fun refresh(external: NotificationCenterExternalState = lastExternalState) {
        lastExternalState = external
        launchLogged(appLogService, AppLogCategories.SETTINGS, "Notification center refresh", onError = { _uiState.update { it.copy(loading = false) } }) {
            _uiState.update { it.copy(loading = true) }
            val snapshot = NotificationCenterSnapshot(
                smsPermissionGranted = permissionStateProvider(),
                reviewRequiredCount = notificationCenterMetricsWorkflow.requiredReviewCount(),
                unregisteredCardCount = notificationCenterMetricsWorkflow.unregisteredCardCount(),
                unregisteredAccountCount = notificationCenterMetricsWorkflow.unregisteredAccountCount(),
                excludedForeignCurrencyCount = external.excludedForeignCurrencyCount,
                periodLabel = external.periodLabel,
                rescanStatusName = external.rescanStatusName,
                updateAvailableVersion = external.updateAvailableVersion,
                updateReadyVersion = external.updateReadyVersion,
            )
            val result = notificationCenterService.build(snapshot)
            _uiState.update {
                it.copy(
                    loading = false,
                    items = result.items,
                    unreadCount = result.unreadCount,
                )
            }
        }
    }

    fun onNotificationOpened(item: NotificationItem): NotificationAction {
        notificationCenterService.markRead(item.id)
        refresh(lastExternalState)
        return item.action
    }
}
