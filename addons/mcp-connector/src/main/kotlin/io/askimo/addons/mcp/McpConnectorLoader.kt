/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.addons.mcp

import java.util.ServiceLoader

/**
 * Discovers and loads MCP connectors using Java's ServiceLoader mechanism.
 *
 * This allows for dynamic discovery of all MCP connector providers
 * on the classpath without hardcoding them.
 *
 * Usage:
 * ```kotlin
 * // Load all available providers
 * val providers = McpConnectorLoader.loadProviders()
 *
 * // Get provider metadata
 * providers.forEach { provider ->
 *     println("${provider.name}: ${provider.description}")
 * }
 *
 * // Find specific provider by ID
 * val provider = McpConnectorLoader.findProvider("io.askimo.addons.postgres")
 *
 * // Create connector instance with config
 * val connector = provider?.createConnector(mapOf(
 *     "postgres.uri" to "postgresql://localhost:5432/mydb"
 * ))
 * ```
 */
object McpConnectorLoader {

    /**
     * Load all available MCP connector providers from the classpath using ServiceLoader.
     */
    fun loadProviders(): List<McpConnectorProvider> = ServiceLoader.load(McpConnectorProvider::class.java).toList()

    /**
     * Find a provider by ID.
     *
     * @param id The provider ID (e.g., "io.askimo.addons.postgres")
     * @return The provider instance, or null if not found
     */
    fun findProvider(id: String): McpConnectorProvider? = loadProviders().firstOrNull { it.id == id }

    /**
     * Get a map of provider ID to provider instance.
     */
    fun getProviderMap(): Map<String, McpConnectorProvider> = loadProviders().associateBy { it.id }

    /**
     * Create a connector instance with the given configuration.
     *
     * @param providerId The provider ID
     * @param config Configuration values
     * @return Configured connector instance
     */
    fun createConnector(providerId: String, config: Map<String, String>): McpConnector {
        val provider = findProvider(providerId)
            ?: throw IllegalArgumentException("Unknown provider: $providerId")
        return provider.createConnector(config)
    }
}
