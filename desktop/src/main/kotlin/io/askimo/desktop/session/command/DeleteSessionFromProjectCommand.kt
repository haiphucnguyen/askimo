/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.session.command

import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectSessionsRefreshEvent
import io.askimo.core.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Command to delete a session from a project.
 * Handles the deletion and posts events for UI refresh.
 */
class DeleteSessionFromProjectCommand(
    private val chatSessionRepository: ChatSessionRepository,
    private val scope: CoroutineScope,
) {
    private val log = logger<DeleteSessionFromProjectCommand>()

    fun execute(sessionId: String, projectId: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    chatSessionRepository.deleteSession(sessionId)
                }

                EventBus.post(
                    ProjectSessionsRefreshEvent(
                        projectId = projectId,
                        reason = "Session $sessionId deleted from project",
                    ),
                )
            } catch (e: Exception) {
                log.error("Failed to delete session from project", e)
                // TODO: Post error event to EventBus for centralized error handling
            }
        }
    }
}
