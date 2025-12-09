/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.chat.service.PagedSessions
import io.askimo.core.i18n.LocalizationManager
import io.askimo.core.logging.logger
import io.askimo.desktop.util.ErrorHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing sessions view state and operations.
 */
class SessionsViewModel(
    private val scope: CoroutineScope,
    private val sessionService: ChatSessionService,
    private val sessionManager: SessionManager? = null,
    private val onCreateNewSession: (() -> String)? = null,
) {
    private val log = logger<SessionsViewModel>()
    companion object {
        const val MAX_SIDEBAR_SESSIONS = 50
    }

    var pagedSessions by mutableStateOf<PagedSessions?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var recentSessions by mutableStateOf<List<ChatSession>>(emptyList())
        private set

    var totalSessionCount by mutableStateOf(0)
        private set

    // Export dialog state
    var showExportDialog by mutableStateOf(false)
        private set

    var exportSessionId by mutableStateOf<String?>(null)
        private set

    var exportDefaultFilename by mutableStateOf("")
        private set

    // Rename dialog state
    var showRenameDialog by mutableStateOf(false)
        private set

    var renameSessionId by mutableStateOf<String?>(null)
        private set

    var renameCurrentTitle by mutableStateOf("")
        private set

    private val sessionsPerPage = 10

    init {
        loadSessions(1)
        loadRecentSessions()
    }

    /**
     * Load recent sessions for sidebar display (max defined by MAX_SIDEBAR_SESSIONS).
     */
    fun loadRecentSessions() {
        scope.launch {
            try {
                val allSessions = withContext(Dispatchers.IO) {
                    sessionService.getAllSessionsSorted()
                }
                recentSessions = allSessions.take(MAX_SIDEBAR_SESSIONS)
                totalSessionCount = allSessions.size
            } catch (e: Exception) {
                log.error("Failed to load recent sessions: ${e.message}", e)
            }
        }
    }

    /**
     * Load sessions for a specific page.
     */
    fun loadSessions(page: Int = 1) {
        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    sessionService.getSessionsPaged(page, sessionsPerPage)
                }
                pagedSessions = result
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "loading sessions",
                    LocalizationManager.getString("sessions.error.loading"),
                )
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Reload the current page.
     */
    fun refresh() {
        loadSessions(pagedSessions?.currentPage ?: 1)
        loadRecentSessions()
    }

    /**
     * Go to the next page.
     */
    fun nextPage() {
        pagedSessions?.let {
            if (it.hasNextPage) {
                loadSessions(it.currentPage + 1)
            }
        }
    }

    /**
     * Go to the previous page.
     */
    fun previousPage() {
        pagedSessions?.let {
            if (it.hasPreviousPage) {
                loadSessions(it.currentPage - 1)
            }
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        errorMessage = null
    }

    /**
     * Delete a session with full cleanup including session manager state and automatic session switching.
     *
     * This method:
     * 1. Closes the session in SessionManager (stops any active streaming)
     * 2. Deletes the session from the database
     * 3. If the deleted session was active, switches to another session or creates a new one
     * 4. Refreshes the session list
     */
    fun deleteSessionWithCleanup(sessionId: String) {
        scope.launch {
            try {
                // 1. Clean up ViewModel and stop any active streaming
                sessionManager?.closeSession(sessionId)

                // 2. Delete from database
                val deleted = withContext(Dispatchers.IO) {
                    sessionService.deleteSession(sessionId)
                }

                if (deleted) {
                    // 3. If deleted active session, switch to another or create new
                    if (sessionManager?.activeSessionId == null) {
                        // Refresh to get updated list first
                        val updatedSessions = withContext(Dispatchers.IO) {
                            sessionService.getAllSessionsSorted().take(MAX_SIDEBAR_SESSIONS)
                        }

                        if (updatedSessions.isNotEmpty()) {
                            // Switch to first remaining session
                            sessionManager?.switchToSession(updatedSessions.first().id)
                        } else {
                            // Create new empty session
                            val newSessionId = onCreateNewSession?.invoke()
                            if (newSessionId != null) {
                                sessionManager?.switchToSession(newSessionId)
                            }
                        }
                    }

                    // 4. Refresh the session list
                    refresh()
                } else {
                    errorMessage = LocalizationManager.getString("sessions.error.not.found")
                }
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "deleting session",
                    LocalizationManager.getString("sessions.error.deleting"),
                )
            }
        }
    }

    /**
     * Simple delete a session and refresh the list (without session manager cleanup).
     * Used when session manager is not available or cleanup is not needed.
     */
    fun deleteSession(sessionId: String) {
        scope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    sessionService.deleteSession(sessionId)
                }
                if (deleted) {
                    refresh()
                } else {
                    errorMessage = LocalizationManager.getString("sessions.error.not.found")
                }
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "deleting session",
                    LocalizationManager.getString("sessions.error.deleting"),
                )
            }
        }
    }

    /**
     * Update the starred status of a session and refresh the list.
     */
    fun updateSessionStarred(sessionId: String, isStarred: Boolean) {
        scope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    sessionService.updateSessionStarred(sessionId, isStarred)
                }
                if (updated) {
                    refresh()
                } else {
                    errorMessage = LocalizationManager.getString("sessions.error.not.found")
                }
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "updating session",
                    LocalizationManager.getString("sessions.error.updating"),
                )
            }
        }
    }

    /**
     * Show the rename dialog for a session.
     */
    fun showRenameDialog(sessionId: String) {
        scope.launch {
            try {
                // Find the session to get the current title
                val session = withContext(Dispatchers.IO) {
                    sessionService.getSessionById(sessionId)
                } ?: run {
                    errorMessage = LocalizationManager.getString("sessions.error.not.found")
                    return@launch
                }

                // Set state to show dialog
                renameSessionId = sessionId
                renameCurrentTitle = session.title ?: ""
                showRenameDialog = true
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "preparing rename",
                    "Failed to prepare rename: ${e.message}",
                )
            }
        }
    }

    /**
     * Dismiss the rename dialog.
     */
    fun dismissRenameDialog() {
        showRenameDialog = false
        renameSessionId = null
        renameCurrentTitle = ""
    }

    /**
     * Execute the actual rename after user confirms new title.
     */
    fun executeRename(newTitle: String) {
        val sessionId = renameSessionId ?: return

        scope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    sessionService.renameTitle(sessionId, newTitle)
                }
                if (updated) {
                    refresh()
                    dismissRenameDialog()
                } else {
                    errorMessage = LocalizationManager.getString("sessions.error.rename.failed")
                }
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "renaming session",
                    LocalizationManager.getString("sessions.error.renaming"),
                )
            }
        }
    }

    /**
     * Export a session to markdown format.
     * Shows a styled file save dialog to choose export location.
     */
    fun exportSession(sessionId: String) {
        scope.launch {
            try {
                // Find the session to get the title for default filename
                val session = withContext(Dispatchers.IO) {
                    sessionService.getSessionById(sessionId)
                } ?: run {
                    errorMessage = LocalizationManager.getString("sessions.error.not.found")
                    return@launch
                }

                // Sanitize title for filename
                val defaultFilename = (session.title ?: "session").replace(Regex("[^a-zA-Z0-9-_\\s]"), "_") + ".md"

                // Set state to show dialog
                exportSessionId = sessionId
                exportDefaultFilename = defaultFilename
                showExportDialog = true
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "preparing export",
                    "Failed to prepare export: ${e.message}",
                )
            }
        }
    }

    /**
     * Dismiss the export dialog.
     */
    fun dismissExportDialog() {
        showExportDialog = false
        exportSessionId = null
        exportDefaultFilename = ""
    }

    /**
     * Execute the actual export after user confirms file location.
     */
    fun executeExport(fullPath: String) {
        val sessionId = exportSessionId ?: return

        scope.launch {
            try {
                // Export in background
                val result = withContext(Dispatchers.IO) {
                    val exporterService = io.askimo.core.chat.service.ChatSessionExporterService()
                    exporterService.exportToMarkdown(sessionId, fullPath)
                }

                result.onFailure { error ->
                    errorMessage = ErrorHandler.getUserFriendlyError(
                        error,
                        "exporting session",
                        "Failed to export session: ${error.message}",
                    )
                }

                // Close dialog on success
                dismissExportDialog()
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "exporting session",
                    "Failed to export session: ${e.message}",
                )
            }
        }
    }
}
