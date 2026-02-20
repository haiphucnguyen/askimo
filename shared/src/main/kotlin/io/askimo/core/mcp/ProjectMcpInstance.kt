/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import io.askimo.core.logging.logger
import io.askimo.core.mcp.connectors.StdioMcpConnector
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * A project-specific instance of an MCP server.
 * Contains actual values for the server's parameters.
 *
 * Example:
 * - Project "Analytics Dashboard":
 *   - Instance: serverId="mongodb-mcp-server", name="Production MongoDB"
 *               parameterValues={mongoUri: "mongodb://prod/analytics", readOnly: "true"}
 *
 * - Project "Dev Environment":
 *   - Instance: serverId="mongodb-mcp-server", name="Local MongoDB"
 *               parameterValues={mongoUri: "mongodb://localhost/dev", readOnly: "false"}
 */
data class ProjectMcpInstance(
    val id: String,
    val projectId: String,
    val serverId: String, // References McpServerDefinition.id
    val name: String,
    val parameterValues: Map<String, String>, // User-provided values
    val enabled: Boolean = true,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {

    private val log = logger<ProjectMcpInstance>()

    /**
     * Creates an MCP connector from this instance + its definition.
     * Resolves all template placeholders with actual parameter values.
     */
    fun toConnector(definition: McpServerDefinition): McpConnector {
        val resolver = TemplateResolver(parameterValues)
        return createStdioConnector(definition, resolver)
    }

    private fun createStdioConnector(
        definition: McpServerDefinition,
        resolver: TemplateResolver,
    ): StdioMcpConnector {
        val stdioConfig = definition.stdioConfig
            ?: throw IllegalStateException("STDIO config missing for ${definition.id}")

        val resolvedCommand = resolver.resolveList(stdioConfig.commandTemplate)
        val resolvedEnv = resolver.resolveMap(stdioConfig.envTemplate)
        val resolvedWorkingDir = stdioConfig.workingDirectory?.let { resolver.resolve(it) }
        log.debug(
            "Create the MCP connector with command: {}, env: {}, workingDir: {}",
            resolvedCommand,
            resolvedEnv,
            resolvedWorkingDir,
        )

        return StdioMcpConnector(
            StdioMcpTransportConfig(
                id = id,
                name = name,
                description = "Instance of ${definition.name}",
                command = resolvedCommand,
                env = resolvedEnv,
            ),
        )
    }
}

/**
 * Serializable version of ProjectMcpInstance for JSON storage
 */
@Serializable
data class ProjectMcpInstanceData(
    val id: String,
    val projectId: String,
    val serverId: String,
    val name: String,
    val parameterValues: Map<String, String>,
    val enabled: Boolean = true,
    // ISO-8601 format
    val createdAt: String,
    // ISO-8601 format
    val updatedAt: String,
) {
    fun toDomain(): ProjectMcpInstance = ProjectMcpInstance(
        id = id,
        projectId = projectId,
        serverId = serverId,
        name = name,
        parameterValues = parameterValues,
        enabled = enabled,
        createdAt = LocalDateTime.parse(createdAt),
        updatedAt = LocalDateTime.parse(updatedAt),
    )

    companion object {
        fun from(instance: ProjectMcpInstance): ProjectMcpInstanceData = ProjectMcpInstanceData(
            id = instance.id,
            projectId = instance.projectId,
            serverId = instance.serverId,
            name = instance.name,
            parameterValues = instance.parameterValues,
            enabled = instance.enabled,
            createdAt = instance.createdAt.toString(),
            updatedAt = instance.updatedAt.toString(),
        )
    }
}
