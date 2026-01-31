/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ProjectConnector
import io.askimo.core.chat.domain.ProjectConnectorsTable
import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/**
 * Extension function to map an Exposed ResultRow to a ProjectConnector object.
 */
private fun ResultRow.toProjectConnector(): ProjectConnector {
    val configJson = this[ProjectConnectorsTable.config]
    val config = if (configJson.isBlank()) {
        emptyMap()
    } else {
        Json.decodeFromString<Map<String, String>>(configJson)
    }

    return ProjectConnector(
        id = this[ProjectConnectorsTable.id],
        projectId = this[ProjectConnectorsTable.projectId],
        connectorProviderId = this[ProjectConnectorsTable.connectorProviderId],
        name = this[ProjectConnectorsTable.name],
        config = config,
        enabled = this[ProjectConnectorsTable.enabled],
        createdAt = this[ProjectConnectorsTable.createdAt],
        updatedAt = this[ProjectConnectorsTable.updatedAt],
    )
}

/**
 * Repository for managing project MCP connectors.
 * Enables projects to have multiple MCP connectors with different configurations.
 */
class ProjectConnectorRepository internal constructor(
    databaseManager: DatabaseManager = DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {
    private val log = logger<ProjectConnectorRepository>()

    /**
     * Create a new project connector.
     * @param connector The connector to create (id will be auto-generated if blank)
     * @return The created connector with generated id
     */
    fun create(connector: ProjectConnector): ProjectConnector {
        val connectorWithId = connector.copy(
            id = connector.id.ifBlank { UUID.randomUUID().toString() },
        )

        transaction(database) {
            ProjectConnectorsTable.insert {
                it[id] = connectorWithId.id
                it[projectId] = connectorWithId.projectId
                it[connectorProviderId] = connectorWithId.connectorProviderId
                it[name] = connectorWithId.name
                it[config] = Json.encodeToString(connectorWithId.config)
                it[enabled] = connectorWithId.enabled
                it[createdAt] = connectorWithId.createdAt
                it[updatedAt] = connectorWithId.updatedAt
            }
        }

        log.debug("Created connector ${connectorWithId.id} for project ${connectorWithId.projectId}")
        return connectorWithId
    }

    /**
     * Get all connectors for a project.
     * @param projectId The project id
     * @return List of connectors for the project
     */
    fun findByProjectId(projectId: String): List<ProjectConnector> = transaction(database) {
        ProjectConnectorsTable
            .selectAll()
            .where { ProjectConnectorsTable.projectId eq projectId }
            .map { it.toProjectConnector() }
    }

    /**
     * Get enabled connectors for a project.
     * @param projectId The project id
     * @return List of enabled connectors for the project
     */
    fun findEnabledByProjectId(projectId: String): List<ProjectConnector> = transaction(database) {
        ProjectConnectorsTable
            .selectAll()
            .where { (ProjectConnectorsTable.projectId eq projectId) and (ProjectConnectorsTable.enabled eq true) }
            .map { it.toProjectConnector() }
    }

    /**
     * Get a connector by id.
     * @param connectorId The connector id
     * @return The connector or null if not found
     */
    fun findById(connectorId: String): ProjectConnector? = transaction(database) {
        ProjectConnectorsTable
            .selectAll()
            .where { ProjectConnectorsTable.id eq connectorId }
            .map { it.toProjectConnector() }
            .firstOrNull()
    }

    /**
     * Update a connector.
     * @param connector The connector with updated values
     * @return True if updated, false if not found
     */
    fun update(connector: ProjectConnector): Boolean {
        val updated = transaction(database) {
            ProjectConnectorsTable.update({ ProjectConnectorsTable.id eq connector.id }) {
                it[projectId] = connector.projectId
                it[connectorProviderId] = connector.connectorProviderId
                it[name] = connector.name
                it[config] = Json.encodeToString(connector.config)
                it[enabled] = connector.enabled
                it[updatedAt] = LocalDateTime.now()
            }
        }

        if (updated > 0) {
            log.debug("Updated connector ${connector.id}")
        }
        return updated > 0
    }

    /**
     * Delete a connector.
     * @param connectorId The connector id
     * @return True if deleted, false if not found
     */
    fun delete(connectorId: String): Boolean {
        val deleted = transaction(database) {
            ProjectConnectorsTable.deleteWhere { id eq connectorId }
        }

        if (deleted > 0) {
            log.debug("Deleted connector $connectorId")
        }
        return deleted > 0
    }

    /**
     * Delete all connectors for a project.
     * @param projectId The project id
     * @return Number of connectors deleted
     */
    fun deleteByProjectId(projectId: String): Int {
        val deleted = transaction(database) {
            ProjectConnectorsTable.deleteWhere { ProjectConnectorsTable.projectId eq projectId }
        }

        if (deleted > 0) {
            log.debug("Deleted $deleted connectors for project $projectId")
        }
        return deleted
    }

    /**
     * Enable or disable a connector.
     * @param connectorId The connector id
     * @param enabled True to enable, false to disable
     * @return True if updated, false if not found
     */
    fun setEnabled(connectorId: String, enabled: Boolean): Boolean {
        val updated = transaction(database) {
            ProjectConnectorsTable.update({ ProjectConnectorsTable.id eq connectorId }) {
                it[ProjectConnectorsTable.enabled] = enabled
                it[updatedAt] = LocalDateTime.now()
            }
        }

        if (updated > 0) {
            log.debug("Set connector $connectorId enabled=$enabled")
        }
        return updated > 0
    }
}
