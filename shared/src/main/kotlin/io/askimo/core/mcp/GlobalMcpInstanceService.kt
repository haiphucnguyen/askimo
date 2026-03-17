/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import dev.langchain4j.mcp.client.DefaultMcpClient
import io.askimo.core.intent.ToolConfig
import io.askimo.core.intent.ToolVectorIndex
import io.askimo.core.logging.logger
import io.askimo.core.mcp.config.GlobalMcpInstancesConfig
import io.askimo.core.mcp.config.McpServersConfig

private val log = logger<GlobalMcpInstanceService>()

/**
 * Global MCP instances are available in Universal Chat (not tied to a specific project).
 *
 * Implementation note:
 * We reuse [ProjectMcpInstanceService] by providing [GlobalMcpInstancesConfig]
 * and a synthetic project id ([GLOBAL_MCP_SCOPE_ID]).
 */
class GlobalMcpInstanceService(
    serversConfig: McpServersConfig = McpServersConfig,
    mcpClientFactory: McpClientFactory = McpClientFactory(),
) {

    private val delegate = ProjectMcpInstanceService(
        instancesConfig = GlobalMcpInstancesConfig,
        serversConfig = serversConfig,
        mcpClientFactory = mcpClientFactory,
    )

    fun getInstances(): List<McpInstance> = delegate.getInstances(GLOBAL_MCP_SCOPE_ID)

    fun getInstance(instanceId: String): McpInstance? = delegate.getInstance(GLOBAL_MCP_SCOPE_ID, instanceId)

    fun createInstance(
        serverId: String,
        name: String,
        parameterValues: Map<String, String>,
    ): Result<McpInstance> = delegate.createInstance(
        projectId = GLOBAL_MCP_SCOPE_ID,
        serverId = serverId,
        name = name,
        parameterValues = parameterValues,
    )

    fun updateInstance(
        instanceId: String,
        name: String? = null,
        parameterValues: Map<String, String>? = null,
        enabled: Boolean? = null,
    ): Result<McpInstance> = delegate.updateInstance(
        projectId = GLOBAL_MCP_SCOPE_ID,
        instanceId = instanceId,
        name = name,
        parameterValues = parameterValues,
        enabled = enabled,
    )

    fun deleteInstance(instanceId: String): Result<Unit> {
        return try {
            // Get the instance before deletion to retrieve serverId
            val instance = delegate.getInstance(GLOBAL_MCP_SCOPE_ID, instanceId)
                ?: return Result.failure(IllegalArgumentException("Instance not found: $instanceId"))

            // Delete the instance (from mcp-instances.yml)
            val result = delegate.deleteInstance(GLOBAL_MCP_SCOPE_ID, instanceId)

            if (result.isSuccess) {
                // Also remove the associated server definition with "global" tag from mcp-servers.yml
                val serverDef = McpServersConfig.get(instance.serverId)
                if (serverDef != null && serverDef.tags.contains("global")) {
                    McpServersConfig.remove(instance.serverId)
                    log.debug("Removed global server definition '${instance.serverId}' for instance '${instance.name}'")
                }
            }

            result
        } catch (e: Exception) {
            log.warn("Failed to delete global MCP instance: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch global tools (cached) like project tools.
     *
     * Note: For MVP, we call through [ProjectMcpInstanceService.getProjectTools] directly.
     */
    suspend fun getGlobalTools(): List<ToolConfig> = delegate.getProjectTools(GLOBAL_MCP_SCOPE_ID)

    fun getToolVectorIndex(): ToolVectorIndex? = delegate.getToolVectorIndex(GLOBAL_MCP_SCOPE_ID)

    suspend fun listTools(instanceId: String): List<ToolConfig> = delegate.listTools(GLOBAL_MCP_SCOPE_ID, instanceId)

    fun getMcpClientForTool(toolName: String): DefaultMcpClient? = delegate.getMcpClientForTool(GLOBAL_MCP_SCOPE_ID, toolName)

    fun invalidateCache() = delegate.invalidateProjectToolsCache(GLOBAL_MCP_SCOPE_ID)
}
