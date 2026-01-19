/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.domain.Project
import io.askimo.core.db.DatabaseManager
import io.askimo.core.db.Pageable
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectsRefreshRequested
import io.askimo.core.i18n.LocalizationManager
import io.askimo.core.logging.logger
import io.askimo.desktop.util.ErrorHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var pagedProjects by mutableStateOf<Pageable<Project>?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    private val projectsPerPage = 10

    init {
        loadProjects()
        loadProjectsPaged(1)
        subscribeToProjectEvents()
    }

    /**
     * Subscribe to internal events to keep project list updated.
     */
    private fun subscribeToProjectEvents() {
        scope.launch {
            EventBus.internalEvents
                .filterIsInstance<ProjectsRefreshRequested>()
                .collect { event ->
                    log.debug("Projects refresh requested: ${event.reason ?: "no reason specified"}")
                    loadProjects()
                    refresh()
                }
        }
    }

    /**
     * Load all projects from the database.
     */
    fun loadProjects() {
        scope.launch {
            try {
                isLoading = true
                projects = withContext(Dispatchers.IO) {
                    projectRepository.getAllProjects()
                }
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
     * Load projects for a specific page.
     */
    fun loadProjectsPaged(page: Int = 1) {
        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    projectRepository.getProjectsPaged(page, projectsPerPage)
                }
                pagedProjects = result
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "loading projects",
                    LocalizationManager.getString("projects.error.loading"),
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
        loadProjectsPaged(pagedProjects?.currentPage ?: 1)
        loadProjects()
    }

    /**
     * Go to the next page.
     */
    fun nextPage() {
        pagedProjects?.let {
            if (it.hasNextPage) {
                loadProjectsPaged(it.currentPage + 1)
            }
        }
    }

    /**
     * Go to the previous page.
     */
    fun previousPage() {
        pagedProjects?.let {
            if (it.hasPreviousPage) {
                loadProjectsPaged(it.currentPage - 1)
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
     * Dismiss success message.
     */
    fun dismissSuccessMessage() {
        successMessage = null
    }

    /**
     * Delete a project by ID.
     */
    fun deleteProject(projectId: String) {
        scope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    projectRepository.deleteProject(projectId)
                }
                if (deleted) {
                    successMessage = LocalizationManager.getString("projects.delete.success")
                    refresh()
                } else {
                    errorMessage = LocalizationManager.getString("projects.error.not.found")
                }
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "deleting project",
                    LocalizationManager.getString("projects.error.deleting"),
                )
            }
        }
    }

    /**
     * Update an existing project.
     */
    fun updateProject(projectId: String, name: String, description: String?, knowledgeSources: List<KnowledgeSourceConfig>) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    projectRepository.updateProject(
                        projectId = projectId,
                        name = name,
                        description = description,
                        knowledgeSources = knowledgeSources,
                    )
                }
                log.debug("Updated project $projectId")
                refresh()
            } catch (e: Exception) {
                log.error("Failed to update project $projectId", e)
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "updating project",
                    LocalizationManager.getString("projects.error.updating"),
                )
            }
        }
    }
}
