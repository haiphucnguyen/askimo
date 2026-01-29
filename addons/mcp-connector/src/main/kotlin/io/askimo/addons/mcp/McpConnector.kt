/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.addons.mcp

import dev.langchain4j.mcp.client.transport.McpTransport

/**
 * Base class for MCP connector instances.
 *
 * Each instance is configured with specific connection details.
 * Multiple instances can be created from the same provider with different configs.
 *
 * Example:
 * ```kotlin
 * class PostgresMcpConnector(private val config: Map<String, String>) : McpConnector() {
 *     override suspend fun createTransport(): McpTransport {
 *         val uri = config["postgres.uri"] ?: error("Missing postgres.uri")
 *         return StdioMcpTransport.builder()
 *             .command(listOf("npx", "-y", "@modelcontextprotocol/server-postgres", uri))
 *             .build()
 *     }
 * }
 * ```
 */
abstract class McpConnector {
    /**
     * Create the MCP transport for this specific connector instance.
     *
     * @return Configured McpTransport instance
     * @throws IllegalArgumentException if required config is missing
     * @throws Exception if transport creation fails
     */
    abstract suspend fun createTransport(): McpTransport
}
