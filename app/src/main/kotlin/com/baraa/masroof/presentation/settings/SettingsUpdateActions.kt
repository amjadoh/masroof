package com.baraa.masroof.presentation.settings

import com.baraa.masroof.application.logging.AppLogCategories
import com.baraa.masroof.application.logging.AppLogService
import com.baraa.masroof.application.update.ApkInstaller
import com.baraa.masroof.application.update.AppUpdateService
import com.baraa.masroof.application.update.PrivateRepoRequiresTokenException
import com.baraa.masroof.application.update.UpdateChannel
import com.baraa.masroof.application.update.UpdateCheckCoordinator
import com.baraa.masroof.application.update.UpdateCheckResult
import com.baraa.masroof.application.update.UpdateManifest
import com.baraa.masroof.presentation.common.launchLogged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** App update actions for [SettingsViewModel]. It shares the Settings state, so screens keep one state object. */
internal class SettingsUpdateActions(
    private val scope: CoroutineScope,
    private val settingsState: MutableStateFlow<SettingsUiState>,
    private val appUpdateService: AppUpdateService,
    private val updateCheckCoordinator: UpdateCheckCoordinator,
    private val appLogService: AppLogService,
    private val apkInstaller: ApkInstaller,
    private val canInstallPackages: () -> Boolean,
    private val onRequestInstallPermission: () -> Unit,
) {
    fun clearUpdateMessage() {
        settingsState.update { it.copy(updateMessage = null) }
    }

    fun saveGithubToken(token: String) {
        if (token.isBlank()) return
        val saved = appUpdateService.saveToken(token)
        settingsState.update {
            it.copy(
                githubTokenConfigured = appUpdateService.hasConfiguredToken(),
                updateMessage = if (saved) {
                    AppUpdateMessage.TOKEN_SAVED
                } else {
                    AppUpdateMessage.TOKEN_SAVE_FAILED
                },
            )
        }
    }

    fun clearGithubToken() {
        appUpdateService.clearDownloadCache()
        appUpdateService.clearToken()
        updateCheckCoordinator.clearPendingUpdate()
        settingsState.update {
            it.copy(
                githubTokenConfigured = false,
                updateState = AppUpdateUiState.Idle,
                updateMessage = null,
            )
        }
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        if (channel == appUpdateService.getUpdateChannel()) return
        appUpdateService.setUpdateChannel(channel)
        updateCheckCoordinator.clearPendingUpdate()
        settingsState.update {
            it.copy(
                updateChannel = channel,
                updateState = AppUpdateUiState.Idle,
                updateMessage = null,
            )
        }
        checkForUpdates(silent = true)
    }

    fun checkForUpdates(silent: Boolean = false) {
        if (settingsState.value.updateState is AppUpdateUiState.Checking ||
            settingsState.value.updateState is AppUpdateUiState.Downloading
        ) {
            return
        }
        scope.launchLogged(appLogService, AppLogCategories.UPDATE, "Settings checkForUpdates", onError = { error ->
            if (!silent) {
                val message =
                    when {
                        error is PrivateRepoRequiresTokenException -> AppUpdateMessage.TOKEN_REQUIRED
                        error.message?.contains("authentication failed", ignoreCase = true) == true ->
                            AppUpdateMessage.AUTH_FAILED
                        else -> AppUpdateMessage.CHECK_FAILED
                    }
                settingsState.update {
                    it.copy(updateState = AppUpdateUiState.Idle, updateMessage = message)
                }
                restorePendingUpdateState()
            } else {
                settingsState.update { current ->
                    if (current.updateState is AppUpdateUiState.Available ||
                        current.updateState is AppUpdateUiState.ReadyToInstall ||
                        current.updateState is AppUpdateUiState.UpToDate
                    ) {
                        current
                    } else {
                        current.copy(updateState = AppUpdateUiState.Idle)
                    }
                }
                restorePendingUpdateState()
            }
        }) {
            settingsState.update {
                it.copy(
                    updateState = AppUpdateUiState.Checking,
                    updateMessage = null,
                    githubTokenConfigured = appUpdateService.hasConfiguredToken(),
                )
            }
            val result =
                withContext(Dispatchers.IO) {
                    updateCheckCoordinator.checkForUpdate(
                        if (silent) "background" else "manual",
                    ).getOrThrow()
                }
            when (result) {
                UpdateCheckResult.UpToDate ->
                    settingsState.update {
                        it.copy(
                            updateState = AppUpdateUiState.UpToDate,
                            updateMessage = if (silent) null else AppUpdateMessage.UP_TO_DATE,
                        )
                    }

                is UpdateCheckResult.UpdateAvailable ->
                    applyAvailableUpdate(result.manifest, silent)
            }
        }
    }

    fun checkForUpdatesIfStale(silent: Boolean = true) {
        if (!updateCheckCoordinator.shouldCheckNow()) {
            restorePendingUpdateState()
            return
        }
        checkForUpdates(silent = silent)
    }

    fun downloadUpdate() {
        val state = settingsState.value.updateState
        val manifest =
            when (state) {
                is AppUpdateUiState.Available -> state.manifest
                is AppUpdateUiState.ReadyToInstall -> state.manifest
                else -> return
            }
        if (settingsState.value.updateState is AppUpdateUiState.Downloading) return

        scope.launchLogged(appLogService, AppLogCategories.UPDATE, "Settings downloadUpdate", onError = {
            settingsState.update {
                it.copy(
                    updateState = AppUpdateUiState.Available(manifest),
                    updateMessage = AppUpdateMessage.DOWNLOAD_FAILED,
                )
            }
        }) {
            settingsState.update {
                it.copy(
                    updateState = AppUpdateUiState.Downloading(manifest, 0L, 0L),
                    updateMessage = null,
                )
            }
            withContext(Dispatchers.IO) {
                appUpdateService
                    .downloadUpdate(
                        manifest = manifest,
                        onProgress = { bytesRead, totalBytes ->
                            settingsState.update {
                                it.copy(
                                    updateState = AppUpdateUiState.Downloading(
                                        manifest = manifest,
                                        bytesRead = bytesRead,
                                        totalBytes = totalBytes,
                                    ),
                                )
                            }
                        },
                    )
                    .getOrThrow()
            }
            settingsState.update {
                it.copy(
                    updateState = AppUpdateUiState.ReadyToInstall(manifest),
                    updateMessage = AppUpdateMessage.DOWNLOAD_SUCCESS,
                )
            }
        }
    }

    fun installPendingUpdate() {
        val state = settingsState.value.updateState
        if (state !is AppUpdateUiState.ReadyToInstall) return

        if (!canInstallPackages()) {
            settingsState.update { it.copy(updateMessage = AppUpdateMessage.INSTALL_PERMISSION_REQUIRED) }
            onRequestInstallPermission()
            return
        }

        val apkFile = appUpdateService.updateApkFile(state.manifest)
        scope.launchLogged(appLogService, AppLogCategories.UPDATE, "Settings installPendingUpdate", onError = { settingsState.update { it.copy(updateMessage = AppUpdateMessage.INSTALL_FAILED) } }) {
            withContext(Dispatchers.Main) {
                apkInstaller.install(apkFile).getOrThrow()
            }
            dismissUpdateUiWhileInstalling()
        }
    }

    fun retryInstallAfterPermissionGranted() {
        if (!canInstallPackages()) return
        installPendingUpdate()
    }

    fun restorePendingUpdateState() {
        if (settingsState.value.updateState is AppUpdateUiState.Checking) {
            return
        }
        if (settingsState.value.updateState is AppUpdateUiState.Downloading) {
            val downloading = settingsState.value.updateState as AppUpdateUiState.Downloading
            clearStaleUpdateUiIfInstalled(downloading.manifest)
            return
        }
        val manifest = updateCheckCoordinator.restorePendingUpdate()
        if (manifest == null) {
            clearStaleUpdateUiIfInstalled()
            return
        }
        applyAvailableUpdate(manifest, silent = true)
    }

    private fun dismissUpdateUiWhileInstalling() {
        settingsState.update { it.copy(updateState = AppUpdateUiState.Idle, updateMessage = null) }
    }

    private fun clearStaleUpdateUiIfInstalled(manifest: UpdateManifest? = null) {
        val resolvedManifest =
            manifest ?: when (val current = settingsState.value.updateState) {
                is AppUpdateUiState.Available -> current.manifest
                is AppUpdateUiState.ReadyToInstall -> current.manifest
                is AppUpdateUiState.Downloading -> current.manifest
                else -> return
            }
        if (!appUpdateService.isUpdateStillNeeded(resolvedManifest)) {
            appUpdateService.clearDownloadedApk(resolvedManifest)
            updateCheckCoordinator.clearPendingUpdate()
            settingsState.update { it.copy(updateState = AppUpdateUiState.Idle, updateMessage = null) }
        }
    }

    private fun applyAvailableUpdate(
        manifest: com.baraa.masroof.application.update.UpdateManifest,
        silent: Boolean,
    ) {
        val downloaded = appUpdateService.updateApkFile(manifest)
        val nextState =
            if (
                downloaded.exists() &&
                com.baraa.masroof.application.update.ApkIntegrityVerifier.matches(downloaded, manifest.sha256)
            ) {
                AppUpdateUiState.ReadyToInstall(manifest)
            } else {
                if (downloaded.exists()) downloaded.delete()
                AppUpdateUiState.Available(manifest)
            }
        settingsState.update {
            it.copy(
                updateState = nextState,
                updateMessage = if (silent) null else AppUpdateMessage.UPDATE_AVAILABLE,
            )
        }
    }
}
