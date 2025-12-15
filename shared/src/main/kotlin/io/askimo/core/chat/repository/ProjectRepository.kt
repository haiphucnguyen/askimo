/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatSessionsTable
import io.askimo.core.chat.domain.Project
import io.askimo.core.chat.domain.ProjectsTable
import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/**
 * Extension function to map an Exposed ResultRow to a Project object.
 */
private fun ResultRow.toProject(): Project = Project(
    id = this[ProjectsTable.id],
    name = this[ProjectsTable.name],
    description = this[ProjectsTable.description],
    indexedPaths = this[ProjectsTable.indexedPaths],
    createdAt = this[ProjectsTable.createdAt],
    updatedAt = this[ProjectsTable.updatedAt],
)

/**
 * Repository for managing projects.
 * Projects group chat sessions and provide RAG context through indexed files.
 */
class ProjectRepository internal constructor(
    databaseManager: DatabaseManager = DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {
    private val log = logger<ProjectRepository>()

    /**
     * Create a new project.
     * @param project The project to create (id will be auto-generated if blank)
     * @return The created project with generated id
     */
    fun createProject(project: Project): Project {
        val projectWithInjectedFields = project.copy(
            id = project.id.ifBlank { UUID.randomUUID().toString() },
        )

        transaction(database) {
            ProjectsTable.insert {
                it[id] = projectWithInjectedFields.id
                it[name] = projectWithInjectedFields.name
                it[description] = projectWithInjectedFields.description
                it[indexedPaths] = projectWithInjectedFields.indexedPaths
                it[createdAt] = projectWithInjectedFields.createdAt
                it[updatedAt] = projectWithInjectedFields.updatedAt
            }
        }

        log.debug("Created project ${projectWithInjectedFields.id} with name '${projectWithInjectedFields.name}'")
        return projectWithInjectedFields
    }

    /**
     * Get all projects ordered by updated time (most recent first).
     * @return List of all projects
     */
    fun getAllProjects(): List<Project> = transaction(database) {
        ProjectsTable
            .selectAll()
            .orderBy(ProjectsTable.updatedAt to SortOrder.DESC)
            .map { it.toProject() }
    }

    /**
     * Get a project by id.
     * @param projectId The project id
     * @return The project, or null if not found
     */
    fun getProject(projectId: String): Project? = transaction(database) {
        ProjectsTable
            .selectAll()
            .where { ProjectsTable.id eq projectId }
            .singleOrNull()
            ?.toProject()
    }

    /**
     * Find a project by session ID.
     * Joins the sessions table to find which project a session belongs to.
     *
     * @param sessionId The chat session id
     * @return The project that the session belongs to, or null if session has no project or not found
     */
    fun findProjectBySessionId(sessionId: String): Project? = transaction(database) {
        ProjectsTable
            .join(ChatSessionsTable, JoinType.INNER, ProjectsTable.id, ChatSessionsTable.projectId)
            .select(ProjectsTable.columns)
            .where { ChatSessionsTable.id eq sessionId }
            .singleOrNull()
            ?.toProject()
    }


    /**
     * Update a project's information.
     * Updates name, description, and indexed paths. Also updates the updatedAt timestamp.
     *
     * @param projectId The project id
     * @param name The new name
     * @param description The new description (nullable)
     * @param indexedPaths The new indexed paths (JSON string)
     * @return true if updated successfully
     */
    fun updateProject(
        projectId: String,
        name: String,
        description: String?,
        indexedPaths: String,
    ): Boolean = transaction(database) {
        val updated = ProjectsTable.update({ ProjectsTable.id eq projectId }) {
            it[ProjectsTable.name] = name
            it[ProjectsTable.description] = description
            it[ProjectsTable.indexedPaths] = indexedPaths
            it[updatedAt] = LocalDateTime.now()
        } > 0

        if (updated) {
            log.debug("Updated project $projectId")
        }
        updated
    }

    /**
     * Delete a project and all its associated sessions.
     *
     * @param projectId The project id to delete
     * @return true if deleted successfully
     */
    fun deleteProject(projectId: String): Boolean {
        log.debug("Deleting project $projectId")
        val deleted = transaction(database) {
            ProjectsTable.deleteWhere { ProjectsTable.id eq projectId } > 0
        }
        if (deleted) {
            log.debug("Successfully deleted project $projectId")
        }
        return deleted
    }

    /**
     * Update the updatedAt timestamp of a project.
     * This is typically called when a session in the project is updated.
     *
     * @param projectId The project id
     * @return true if updated successfully
     */
    fun touchProject(projectId: String): Boolean = transaction(database) {
        ProjectsTable.update({ ProjectsTable.id eq projectId }) {
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }
}
