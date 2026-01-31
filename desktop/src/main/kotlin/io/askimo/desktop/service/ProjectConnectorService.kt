/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import io.askimo.addons.mcp.McpConnector
import io.askimo.addons.mcp.McpConnectorLoader
import io.askimo.core.chat.repository.ProjectConnectorRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Service for managing and instantiating MCP connectors for projects.
 *
 * This bridges the gap between:
 * - McpConnectorProvider (defines WHAT a connector is)
 * - ProjectConnector (stores per-project configuration)
 * - McpConnector (the actual runtime connector instance)
 *
 * This service is registered in the Koin DI container and can be injected:
 * ```kotlin
 * class MyViewModel(
 *     private val connectorService: ProjectConnectorService
 * ) {
 *     suspend fun loadConnectors(projectId: String) {
 *         val connectors = connectorService.getProjectConnectors(projectId)
 *         // Use connectors
 *     }
 * }
 * ```
 */
class ProjectConnectorService(
    private val repository: ProjectConnectorRepository = DatabaseManager.getInstance().getProjectConnectorRepository(),
) {
    private val log = logger<ProjectConnectorService>()

    /**
     * Get all enabled MCP connector instances for a project.
     * Each instance is created with its project-specific configuration.
     *
     * @param projectId The project ID
     * @return List of instantiated MCP connectors ready to use
     */
    suspend fun getProjectConnectors(projectId: String): List<McpConnector> = coroutineScope {
        val projectConnectors = repository.findEnabledByProjectId(projectId)

        if (projectConnectors.isEmpty()) {
            log.debug("No enabled connectors found for project $projectId")
            return@coroutineScope emptyList()
        }

        // Load all available providers
        val providers = McpConnectorLoader.loadProviders()
        val providerMap = providers.associateBy { it.id }

        // Create connector instances in parallel
        projectConnectors.map { projectConnector ->
            async {
                try {
                    val provider = providerMap[projectConnector.connectorProviderId]
                        ?: throw IllegalStateException(
                            "Provider not found: ${projectConnector.connectorProviderId}. " +
                                "Available providers: ${providerMap.keys}",
                        )

                    log.debug(
                        "Creating connector '${projectConnector.name}' for project $projectId " +
                            "using provider ${provider.name}",
                    )

                    provider.createConnector(projectConnector.config)
                } catch (e: Exception) {
                    log.error(
                        "Failed to create connector '${projectConnector.name}' for project $projectId",
                        e,
                    )
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Get a specific connector instance by ID.
     *
     * @param connectorId The connector ID
     * @return The instantiated connector or null if not found or disabled
     */
    fun getConnectorById(connectorId: String): McpConnector? {
        val projectConnector = repository.findById(connectorId)
            ?: return null

        if (!projectConnector.enabled) {
            log.debug("Connector $connectorId is disabled")
            return null
        }

        val providers = McpConnectorLoader.loadProviders()
        val provider = providers.find { it.id == projectConnector.connectorProviderId }
            ?: run {
                log.error("Provider not found: ${projectConnector.connectorProviderId}")
                return null
            }

        return try {
            provider.createConnector(projectConnector.config)
        } catch (e: Exception) {
            log.error("Failed to create connector $connectorId", e)
            null
        }
    }

    /**
     * Validate connector configuration.
     * Checks that all required fields are present and valid.
     *
     * @param connectorProviderId The provider ID
     * @param config The configuration to validate
     * @return List of validation errors (empty if valid)
     */
    fun validateConfig(connectorProviderId: String, config: Map<String, String>): List<String> {
        val providers = McpConnectorLoader.loadProviders()
        val provider = providers.find { it.id == connectorProviderId }
            ?: return listOf("Provider not found: $connectorProviderId")

        val errors = mutableListOf<String>()

        provider.configSchema.forEach { (key, field) ->
            if (field.required && config[key].isNullOrBlank()) {
                errors.add("Required field missing: ${field.label} ($key)")
            }
        }

        return errors
    }
}
