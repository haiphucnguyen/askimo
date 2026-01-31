/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.addons.mcp.mongo

import io.askimo.addons.mcp.ConfigField
import io.askimo.addons.mcp.ConfigFieldType
import io.askimo.addons.mcp.McpConnector
import io.askimo.addons.mcp.McpConnectorProvider

/**
 * ServiceLoader provider for MongoDB MCP connector.
 *
 * This class defines WHAT the MongoDB connector is and what config it needs,
 * but NOT the actual connection details. Each project provides its own config
 * when creating a connector instance.
 *
 * Registered via META-INF/services to enable automatic discovery by Java's ServiceLoader.
 */
class MongoMcpConnectorProvider : McpConnectorProvider {

    override val id = "io.askimo.addons.mcp.mongo"

    override val name = "Mongo"

    override val version = "1.2.12"

    override val description = "Query and manage Mongo databases using natural language"

    override val homepage = "https://github.com/mongodb-js/mongodb-mcp-server"

    override val configSchema = mapOf(
        "mongo.uri" to ConfigField(
            type = ConfigFieldType.TEXT,
            label = "MongoDB Connection URI",
            description = "Format: mongodb://username:password@hostname:port/database or mongodb+srv://...",
            required = true,
            secret = true,
        ),
        "mongo.database" to ConfigField(
            type = ConfigFieldType.TEXT,
            label = "Database Name",
            description = "Default database to use",
            required = false,
        ),
    )

    override fun createConnector(config: Map<String, String>): McpConnector = MongoMcpConnector(config)
}
