/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.addons.mcp.mongo

import dev.langchain4j.mcp.client.transport.McpTransport
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport
import io.askimo.addons.mcp.McpConnector

/**
 * PostgreSQL MCP connector instance with specific configuration.
 *
 * Multiple instances can exist with different configs (different databases, schemas, etc.).
 * This allows multiple projects to connect to different PostgreSQL servers.
 */
class MongoMcpConnector(
    private val config: Map<String, String>,
) : McpConnector() {

    override suspend fun createTransport(): McpTransport {
        val uri = config["postgres.uri"]
            ?: throw IllegalArgumentException("Missing required config: postgres.uri")

        val schema = config["postgres.schema"] ?: "public"

        // Create stdio transport for the official PostgreSQL MCP server
        return StdioMcpTransport.builder()
            .command(listOf("npx", "-y", "@modelcontextprotocol/server-postgres", uri))
            .environment(mapOf("PGSCHEMA" to schema))
            .build()
    }
}
