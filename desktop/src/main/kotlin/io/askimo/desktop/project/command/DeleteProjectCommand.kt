/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.project.command

import io.askimo.core.chat.repository.ProjectRepository
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectDeletedEvent
import io.askimo.core.event.system.ShellErrorEvent
import io.askimo.core.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeleteProjectCommand(
    private val projectRepository: ProjectRepository,
    private val scope: CoroutineScope,
) {

    private val log = logger<DeleteProjectCommand>()

    fun execute(projectId: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    projectRepository.deleteProject(projectId)
                }

                EventBus.post(
                    ProjectDeletedEvent(
                        projectId = projectId,
                    ),
                )
            } catch (e: Exception) {
                log.error("Failed to delete session from project", e)
                EventBus.post(
                    ShellErrorEvent(
                        sourceEvent = ProjectDeletedEvent::class,
                        originalMessage = e.message,
                    ),
                )
            }
        }
    }
}
