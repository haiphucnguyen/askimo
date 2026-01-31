/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.addons.mcp.mongo

import dev.langchain4j.mcp.client.transport.McpTransport
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport
import io.askimo.addons.mcp.McpConnector

/**
 * MongoDB MCP connector instance with specific configuration.
 *
 * Multiple instances can exist with different configs (different databases, collections, etc.).
 * This allows multiple projects to connect to different MongoDB servers.
 */
class MongoMcpConnector(
    private val config: Map<String, String>,
) : McpConnector() {

    override suspend fun createTransport(): McpTransport {
        val uri = config["mongo.uri"]
            ?: throw IllegalArgumentException("Missing required config: mongo.uri")

        val database = config["mongo.database"]

        // Create stdio transport for the MongoDB MCP server
        // https://github.com/mongodb-js/mongodb-mcp-server
        val command = buildList {
            add("npx")
            add("-y")
            add("mongodb-mcp-server@latest")
            add("--readOnly")
            add(uri)
            database?.let { add(it) }
        }

        return StdioMcpTransport.builder()
            .command(command)
            .build()
    }
}
