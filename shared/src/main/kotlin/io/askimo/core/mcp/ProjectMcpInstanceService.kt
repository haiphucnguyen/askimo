/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.mcp.client.DefaultMcpClient
import io.askimo.core.logging.logger
import io.askimo.core.mcp.config.McpServersConfig
import io.askimo.core.mcp.config.ProjectMcpInstancesConfig
import java.time.LocalDateTime
import java.util.UUID

private val log = logger<ProjectMcpInstanceService>()

/**
 * Service for managing project-specific MCP server instances.
 * Handles loading, persisting, and lifecycle operations for MCP instances.
 */
class ProjectMcpInstanceService(
    private val instancesConfig: ProjectMcpInstancesConfig = ProjectMcpInstancesConfig,
    private val serversConfig: McpServersConfig = McpServersConfig,
) {
    /**
     * Get all MCP instances for a project
     */
    fun getInstances(projectId: String): List<ProjectMcpInstance> = instancesConfig.load(projectId)

    /**
     * Get a specific instance by ID
     */
    fun getInstance(projectId: String, instanceId: String): ProjectMcpInstance? = instancesConfig.get(projectId, instanceId)

    /**
     * Create a new MCP instance for a project
     */
    fun createInstance(
        projectId: String,
        serverId: String,
        name: String,
        parameterValues: Map<String, String>,
    ): Result<ProjectMcpInstance> {
        return try {
            // Validate server definition exists
            val definition = serversConfig.get(serverId)
                ?: return Result.failure(IllegalArgumentException("MCP server definition not found: $serverId"))

            // Create instance
            val instance = ProjectMcpInstance(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                serverId = serverId,
                name = name,
                parameterValues = parameterValues,
                enabled = true,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

            // Validate parameters by attempting to create connector
            instance.toConnector(definition)

            // Save
            instancesConfig.add(instance)

            log.debug("Created MCP instance '${instance.name}' (${instance.id}) for project $projectId")
            Result.success(instance)
        } catch (e: Exception) {
            log.warn("Failed to create MCP instance: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update an existing instance
     */
    fun updateInstance(
        projectId: String,
        instanceId: String,
        name: String? = null,
        parameterValues: Map<String, String>? = null,
        enabled: Boolean? = null,
    ): Result<ProjectMcpInstance> {
        return try {
            val existing = getInstance(projectId, instanceId)
                ?: return Result.failure(IllegalArgumentException("Instance not found: $instanceId"))

            val updated = existing.copy(
                name = name ?: existing.name,
                parameterValues = parameterValues ?: existing.parameterValues,
                enabled = enabled ?: existing.enabled,
                updatedAt = LocalDateTime.now(),
            )

            // Validate if parameters changed
            if (parameterValues != null) {
                val definition = serversConfig.get(updated.serverId)
                    ?: return Result.failure(IllegalStateException("Server definition not found: ${updated.serverId}"))
                updated.toConnector(definition) // Validate
            }

            instancesConfig.update(updated)
            log.debug("Updated MCP instance '${updated.name}' (${updated.id})")
            Result.success(updated)
        } catch (e: Exception) {
            log.warn("Failed to update MCP instance: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete an instance
     */
    fun deleteInstance(projectId: String, instanceId: String): Result<Unit> {
        return try {
            val instance = getInstance(projectId, instanceId)
                ?: return Result.failure(IllegalArgumentException("Instance not found: $instanceId"))

            instancesConfig.remove(projectId, instanceId)
            log.debug("Deleted MCP instance '${instance.name}' (${instance.id})")
            Result.success(Unit)
        } catch (e: Exception) {
            log.warn("Failed to delete MCP instance: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Enable/disable an instance
     */
    fun setInstanceEnabled(projectId: String, instanceId: String, enabled: Boolean): Result<ProjectMcpInstance> = updateInstance(projectId, instanceId, enabled = enabled)

    /**
     * Get all instances using a specific server definition
     */
    fun getInstancesByServer(projectId: String, serverId: String): List<ProjectMcpInstance> = getInstances(projectId).filter { it.serverId == serverId }

    /**
     * Get all enabled instances for a project
     */
    fun getEnabledInstances(projectId: String): List<ProjectMcpInstance> = getInstances(projectId).filter { it.enabled }

    /**
     * Create an MCP client for a specific instance.
     *
     * @param instance The MCP instance to create a client for
     * @param clientKey Unique key for the client
     * @return DefaultMcpClient or null if creation fails
     */
    private suspend fun createMcpClient(
        instance: ProjectMcpInstance,
        clientKey: String,
    ): DefaultMcpClient? {
        return try {
            val definition = serversConfig.get(instance.serverId)
            if (definition == null) {
                log.warn("Server definition not found for instance '${instance.name}': ${instance.serverId}")
                return null
            }

            log.debug("Creating connector for instance '${instance.name}' (${instance.serverId})")
            val connector = instance.toConnector(definition)

            // Validate connector configuration
            log.debug("Validating connector '${instance.name}'")
            val validationResult = connector.validate()
            if (!validationResult.isValid) {
                log.warn("Connector '${instance.name}' validation failed: ${validationResult.errors.joinToString(", ")}")
                return null
            }

            // Create transport
            log.debug("Creating transport for connector '${instance.name}'")
            val transport = try {
                connector.createTransport()
            } catch (e: Exception) {
                log.error("Failed to create transport for '${instance.name}': ${e.javaClass.simpleName}: ${e.message}", e)
                return null
            }

            log.debug("Creating MCP client for instance '${instance.name}'")
            val client = DefaultMcpClient.builder()
                .key(clientKey)
                .transport(transport)
                .build()

            log.debug("Successfully created MCP client for '${instance.name}'")
            client
        } catch (e: Exception) {
            log.error("Failed to create MCP client for '${instance.name}': ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    /**
     * Get MCP tool provider with all active connectors for a project.
     *
     * This creates MCP clients from all enabled connectors and builds a tool provider
     * that can be used to provide MCP tools to the AI model.
     *
     * @param projectId The project ID
     * @return Result containing the McpToolProvider, or null if no connectors are available
     */
    suspend fun getToolProvider(projectId: String): Result<McpToolProvider?> {
        return try {
            val instances = getEnabledInstances(projectId)

            if (instances.isEmpty()) {
                log.debug("No active MCP connectors for project $projectId, returning null")
                return Result.success(null)
            }

            // Build MCP clients from instances using helper method
            val mcpClients = instances.mapNotNull { instance ->
                createMcpClient(instance, "${projectId}_${instance.name}")
            }

            if (mcpClients.isEmpty()) {
                log.warn("All MCP connectors failed to create clients for project $projectId, returning null")
                return Result.success(null)
            }

            // Build tool provider
            val toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClients)
                .build()

            log.debug("Created MCP tool provider with ${mcpClients.size} clients for project $projectId")
            Result.success(toolProvider)
        } catch (e: Exception) {
            log.error("Failed to create MCP tool provider for project $projectId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * List available tools for a specific MCP server instance.
     *
     * @param projectId The project ID
     * @param instanceId The instance ID to get tools for
     * @return Result containing list of ToolSpecification, or error
     */
    suspend fun listTools(projectId: String, instanceId: String): Result<List<ToolSpecification>> {
        return try {
            val instance = getInstance(projectId, instanceId)
                ?: return Result.failure(IllegalArgumentException("Instance not found: $instanceId"))

            // Use helper method to create MCP client
            val mcpClient = createMcpClient(instance, "tools_list_${projectId}_${instance.id}")
                ?: return Result.failure(IllegalStateException("Failed to create MCP client for instance ${instance.name}"))

            // List tools from MCP client
            log.debug("Fetching tools from MCP client for '${instance.name}'")
            val tools = mcpClient.listTools()

            log.debug("Successfully fetched ${tools.size} tools for instance '${instance.name}'")
            Result.success(tools)
        } catch (e: Exception) {
            log.error("Failed to list tools for instance $instanceId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Validate instance configuration without saving
     */
    fun validateInstance(
        serverId: String,
        parameterValues: Map<String, String>,
    ): Result<Unit> {
        return try {
            val definition = serversConfig.get(serverId)
                ?: return Result.failure(IllegalArgumentException("Server not found: $serverId"))

            val tempInstance = ProjectMcpInstance(
                id = "temp",
                projectId = "temp",
                serverId = serverId,
                name = "temp",
                parameterValues = parameterValues,
                enabled = true,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

            tempInstance.toConnector(definition)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Test connection for an instance
     */
    fun testConnection(projectId: String, instanceId: String): Result<Unit> {
        return try {
            val instance = getInstance(projectId, instanceId)
                ?: return Result.failure(IllegalArgumentException("Instance not found: $instanceId"))

            val definition = serversConfig.get(instance.serverId)
                ?: return Result.failure(IllegalStateException("Server definition not found: ${instance.serverId}"))

            val connector = instance.toConnector(definition)
            val testResult = connector.validate()

            if (testResult.isValid) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(testResult.errors.joinToString(", ")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete all instances for a project (called when project is deleted)
     */
    fun deleteAllInstances(projectId: String) {
        instancesConfig.deleteAll(projectId)
        log.debug("Deleted all MCP instances for project $projectId")
    }

    /**
     * Get statistics about MCP instances for a project
     */
    fun getInstanceStats(projectId: String): InstanceStats {
        val instances = getInstances(projectId)
        val byServer = instances.groupBy { it.serverId }

        return InstanceStats(
            total = instances.size,
            enabled = instances.count { it.enabled },
            disabled = instances.count { !it.enabled },
            byServer = byServer.mapValues { it.value.size },
        )
    }
}

/**
 * Statistics about MCP instances for a project
 */
data class InstanceStats(
    val total: Int,
    val enabled: Int,
    val disabled: Int,
    val byServer: Map<String, Int>,
)
