/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp.config

import io.askimo.core.mcp.ProjectMcpInstance

/**
 * Abstraction over MCP instance persistence.
 *
 * - Project scope: instances stored under ~/.askimo/<profile>/projects/<projectId>/mcp-instances.yml
 * - Global scope: instances stored under ~/.askimo/<profile>/mcp-instances.yml via synthetic project id.
 */
interface McpInstancesConfig {
    fun load(projectId: String): List<ProjectMcpInstance>
    fun save(projectId: String, instances: List<ProjectMcpInstance>)

    fun add(instance: ProjectMcpInstance)
    fun remove(projectId: String, instanceId: String)

    fun get(projectId: String, instanceId: String): ProjectMcpInstance?

    fun update(instance: ProjectMcpInstance) = add(instance)

    fun deleteAll(projectId: String)
}
