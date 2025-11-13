/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.session.ChatSessionService
import io.askimo.core.session.PagedSessions
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
    var pagedSessions by mutableStateOf<PagedSessions?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var recentSessions by mutableStateOf<List<io.askimo.core.session.ChatSession>>(emptyList())
        private set

    var totalSessionCount by mutableStateOf(0)
        private set

    private val sessionsPerPage = 10

    init {
        loadSessions(1)
        loadRecentSessions()
    }

    /**
     * Load recent sessions for sidebar display (max 10).
     */
    fun loadRecentSessions() {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    sessionService.getAllSessionsSorted().take(10)
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
                errorMessage = "Error loading sessions: ${e.message}"
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
}
