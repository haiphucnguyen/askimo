/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp.config

import io.askimo.core.mcp.McpInstance

/**
 * Abstraction over MCP instance persistence.
 *
 * - Project scope: instances stored under ~/.askimo/<profile>/projects/<projectId>/mcp-instances.yml
 * - Global scope: instances stored under ~/.askimo/<profile>/mcp-instances.yml via synthetic project id.
 */
interface McpInstancesConfig {
    fun load(projectId: String): List<McpInstance>
    fun save(projectId: String, instances: List<McpInstance>)

    fun add(instance: McpInstance)
    fun remove(projectId: String, instanceId: String)

    fun get(projectId: String, instanceId: String): McpInstance?

    fun update(instance: McpInstance) = add(instance)

    fun deleteAll(projectId: String)
}
