/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.chat.domain.Project
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ViewModel for managing projects in the desktop application.
 */
class ProjectsViewModel(
    private val scope: CoroutineScope,
) {
    private val log = logger<ProjectsViewModel>()
    private val projectRepository = DatabaseManager.getInstance().getProjectRepository()

    var projects by mutableStateOf<List<Project>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    init {
        loadProjects()
    }

    /**
     * Load all projects from the database.
     */
    fun loadProjects() {
        scope.launch {
            try {
                isLoading = true
                projects = projectRepository.getAllProjects()
                log.debug("Loaded ${projects.size} projects")
            } catch (e: Exception) {
                log.error("Failed to load projects", e)
                projects = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Refresh the projects list.
     */
    fun refresh() {
        loadProjects()
    }

    /**
     * Delete a project by ID.
     */
    fun deleteProject(projectId: String) {
        scope.launch {
            try {
                projectRepository.deleteProject(projectId)
                log.debug("Deleted project $projectId")
                loadProjects() // Refresh the list
            } catch (e: Exception) {
                log.error("Failed to delete project $projectId", e)
            }
        }
    }
}
