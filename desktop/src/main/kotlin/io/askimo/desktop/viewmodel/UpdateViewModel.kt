/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.logging.logger
import io.askimo.desktop.service.ReleaseInfo
import io.askimo.desktop.service.UpdateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing update check operations.
 */
class UpdateViewModel(
    private val scope: CoroutineScope,
    private val updateService: UpdateService = UpdateService(),
) {
    private val log = logger<UpdateViewModel>()

    var isChecking by mutableStateOf(false)
        private set

    var releaseInfo by mutableStateOf<ReleaseInfo?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var showUpdateDialog by mutableStateOf(false)
        private set

    /**
     * Check for updates in the background.
     * If silent is true, NO dialog is shown - only notification badge appears.
     * User must manually click "Help → Check for Updates" to see details.
     */
    fun checkForUpdates(silent: Boolean = false) {
        if (isChecking) return

        isChecking = true
        errorMessage = null
        releaseInfo = null

        scope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    updateService.checkForUpdates()
                }

                releaseInfo = info

                if (info != null) {
                    if (info.isNewVersion) {
                        if (!silent) {
                            showUpdateDialog = true
                        }
                    } else if (!silent) {
                        showUpdateDialog = true
                    }
                } else {
                    if (!silent) {
                        errorMessage = "Failed to check for updates"
                        showUpdateDialog = true
                    }
                }
            } catch (e: Exception) {
                log.error("Error checking for updates", e)
                if (!silent) {
                    errorMessage = "Failed to check for updates: ${e.message}"
                }
            } finally {
                isChecking = false
            }
        }
    }

    fun dismissUpdateDialog() {
        showUpdateDialog = false
    }

    /**
     * Show update dialog for existing release info without re-checking.
     * Used when user clicks Details button in notification.
     */
    fun showUpdateDialogForExistingRelease() {
        if (releaseInfo != null && releaseInfo!!.isNewVersion) {
            showUpdateDialog = true
        }
    }

    fun openDownloadPage() {
        releaseInfo?.let { info ->
            try {
                val desktop = java.awt.Desktop.getDesktop()
                desktop.browse(java.net.URI(info.downloadUrl))
            } catch (e: Exception) {
                log.error("Failed to open download page", e)
                errorMessage = "Failed to open browser: ${e.message}"
            }
        }
    }

    fun getCurrentVersion(): String = updateService.getCurrentVersion()
}
