/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import io.askimo.core.db.sqliteDatetime
import org.jetbrains.exposed.sql.Table
import java.time.LocalDateTime

/**
 * Represents an MCP connector configuration for a specific project.
 *
 * Each project can have multiple MCP connectors (MongoDB, PostgreSQL, etc.)
 * with their own configuration. This allows projects to access different
 * data sources through the MCP protocol.
 *
 * Example:
 * - Project "Analytics Dashboard" might have:
 *   - Connector 1: "Production MongoDB" (mongo.uri=mongodb://prod-server/analytics)
 *   - Connector 2: "Reporting PostgreSQL" (postgres.uri=postgresql://reporting-db)
 * - Project "Dev Environment" might have:
 *   - Connector 1: "Local MongoDB" (mongo.uri=mongodb://localhost/dev)
 */
data class ProjectConnector(
    val id: String,
    val projectId: String,
    val connectorProviderId: String, // e.g., "io.askimo.addons.mcp.mongo"
    val name: String, // User-friendly name for this connector instance
    val config: Map<String, String>, // Connector-specific config (URI, database, etc.)
    val enabled: Boolean = true,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/**
 * Exposed table definition for project MCP connectors.
 * Co-located with domain class for easier maintenance and foreign key references.
 */
object ProjectConnectorsTable : Table("project_connectors") {
    val id = varchar("id", 36)
    val projectId = varchar("project_id", 36).references(ProjectsTable.id)
    val connectorProviderId = varchar("connector_provider_id", 255)
    val name = varchar("name", 255)
    val config = text("config") // JSON
    val enabled = bool("enabled").default(true)
    val createdAt = sqliteDatetime("created_at")
    val updatedAt = sqliteDatetime("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, projectId)
        index(isUnique = false, connectorProviderId)
    }
}
