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
    private val sessionService: ChatSessionService = ChatSessionService(),
) {
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
                val result = withContext(Dispatchers.IO) {
                    sessionService.getAllSessionsSorted().take(MAX_SIDEBAR_SESSIONS)
                }
                recentSessions = result
                totalSessionCount = withContext(Dispatchers.IO) {
                    sessionService.getAllSessionsSorted().size
                }
            } catch (e: Exception) {
                // Silently fail for sidebar loading
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
     * Delete a session and refresh the list.
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
     * Rename a session's title and refresh the list.
     */
    fun renameSession(sessionId: String, newTitle: String) {
        scope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    sessionService.renameTitle(sessionId, newTitle)
                }
                if (updated) {
                    refresh()
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
}
