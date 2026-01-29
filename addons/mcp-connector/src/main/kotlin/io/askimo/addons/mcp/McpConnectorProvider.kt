/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.addons.mcp

/**
 * Service provider interface for MCP connectors.
 *
 * This defines WHAT the connector is (metadata, capabilities, config schema),
 * but NOT the actual connection details. Connection details are provided
 * per-project when creating a connector instance.
 *
 * Multiple projects can use the same provider with different configurations.
 *
 * Example:
 * ```
 * class PostgresMcpConnectorProvider : McpConnectorProvider {
 *     override val id = "io.askimo.addons.postgres"
 *     override val name = "PostgreSQL"
 *     override val configSchema = mapOf(...)
 *
 *     override fun createConnector(config: Map<String, String>): McpConnector {
 *         return PostgresMcpConnector(config)
 *     }
 * }
 * ```
 *
 * Implementations should be registered via Java's ServiceLoader mechanism
 * by creating: META-INF/services/io.askimo.addons.mcp.McpConnectorProvider
 */
interface McpConnectorProvider {
    /**
     * Unique identifier for this connector type.
     */
    val id: String

    /**
     * Display name for this connector.
     */
    val name: String

    /**
     * Connector version.
     */
    val version: String

    /**
     * Description of what this connector does.
     */
    val description: String

    /**
     * Homepage or documentation URL.
     */
    val homepage: String?
        get() = null

    /**
     * Configuration schema - defines what config fields are needed
     * when creating a connection to this type of server.
     */
    val configSchema: Map<String, ConfigField>

    /**
     * Create a connector instance with the given configuration.
     * Multiple projects can create different instances with different configs.
     *
     * @param config Configuration values (e.g., postgres URI, schema, etc.)
     * @return A configured connector instance
     */
    fun createConnector(config: Map<String, String>): McpConnector
}
