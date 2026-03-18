/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp.config

import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.mcp.McpInstance
import io.askimo.core.mcp.McpInstanceData
import io.askimo.core.security.SecureKeyManager
import io.askimo.core.util.AskimoHome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private object ProjectMcpInstancesConfigObject
private val log = logger<ProjectMcpInstancesConfigObject>()

/**
 * Persistent configuration store for MCP instances scoped to a project.
 *
 * Each project's instances are serialised as YAML and stored at:
 * `<AskimoHome>/projects/<projectId>/mcp-instances.yml`
 *
 * Secrets (parameters whose type is [io.askimo.core.mcp.ParameterType.SECRET]) are
 * **not** written to the YAML file. Instead they are stored separately via
 * [SecureKeyManager] and referenced by a deterministic key produced by
 * [McpInstanceData.secretKeyId]. Secret cleanup is handled automatically by
 * [remove] and [deleteAll] before the corresponding YAML entries are removed.
 *
 *
 * @see McpInstancesConfig
 * @see McpInstanceData
 * @see SecureKeyManager
 */
object ProjectMcpInstancesConfig : McpInstancesConfig {

    private const val CONFIG_FILE_NAME = "mcp-instances.yml"

    private fun getConfigPath(projectId: String): Path = AskimoHome.base()
        .resolve("projects")
        .resolve(projectId)
        .resolve(CONFIG_FILE_NAME)

    override fun load(projectId: String): List<McpInstance> {
        val path = getConfigPath(projectId)

        if (!path.exists()) {
            return emptyList()
        }

        return try {
            val content = Files.readString(path)
            val wrapper = mcpObjectMapper.readValue(content, InstancesWrapper::class.java)
            wrapper.instances.map { it.toDomain() }
        } catch (e: Exception) {
            log.displayError("Failed to load MCP instances for project $projectId", e)
            emptyList()
        }
    }

    override fun save(projectId: String, instances: List<McpInstance>) {
        val path = getConfigPath(projectId)

        try {
            path.parent.createDirectories()
            val data = instances.map { instance ->
                val definition = McpServersConfig.get(instance.serverId)
                McpInstanceData.from(instance, definition)
            }
            val wrapper = InstancesWrapper(data)
            val yaml = mcpObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper)
            Files.writeString(path, yaml)
            log.debug("Saved ${instances.size} MCP instances for project $projectId")
        } catch (e: Exception) {
            log.displayError("Failed to save MCP instances for project $projectId", e)
        }
    }

    override fun add(instance: McpInstance) {
        val projectId = instance.projectId ?: return
        val instances = load(projectId).toMutableList()
        instances.removeIf { it.id == instance.id }
        instances.add(instance)
        save(projectId, instances)
    }

    override fun remove(projectId: String, instanceId: String) {
        // Load the raw data (not domain) so we can read secretParameterKeys before deletion
        val path = getConfigPath(projectId)
        if (path.exists()) {
            try {
                val content = Files.readString(path)
                val wrapper = mcpObjectMapper.readValue(content, InstancesWrapper::class.java)
                wrapper.instances
                    .find { it.id == instanceId }
                    ?.secretParameterKeys
                    ?.forEach { key ->
                        SecureKeyManager.removeSecretKey(McpInstanceData.secretKeyId(instanceId, key))
                    }
            } catch (e: Exception) {
                log.displayError("Failed to clean up secrets for instance $instanceId", e)
            }
        }
        val instances = load(projectId).filterNot { it.id == instanceId }
        save(projectId, instances)
    }

    /**
     * Returns the [McpInstance] with [instanceId] for [projectId], or `null` if no
     * such instance exists or the configuration file cannot be read.
     *
     * @param projectId  The project to search within.
     * @param instanceId The unique identifier of the instance to retrieve.
     * @return The matching [McpInstance], or `null` if not found.
     */
    override fun get(projectId: String, instanceId: String): McpInstance? = load(projectId).find { it.id == instanceId }

    /**
     * Updates an existing instance by delegating to [add], which performs an
     * upsert — replacing the existing entry with the same [McpInstance.id] if found,
     * or appending it if not.
     *
     * @param instance The instance containing updated values to persist.
     */
    override fun update(instance: McpInstance) {
        add(instance)
    }

    /**
     * Deletes all MCP instances for [projectId] and erases every associated secret
     * from [SecureKeyManager], then removes the YAML configuration file from disk.
     *
     * This is intended to be called when a project itself is deleted. After this
     * method returns, [load] will return an empty list for [projectId] (the file no
     * longer exists).
     *
     * @param projectId The project whose entire MCP configuration should be removed.
     */
    override fun deleteAll(projectId: String) {
        val path = getConfigPath(projectId)
        if (path.exists()) {
            try {
                // Clean up secrets before deleting the file
                val content = Files.readString(path)
                val wrapper = mcpObjectMapper.readValue(content, InstancesWrapper::class.java)
                wrapper.instances.forEach { instance ->
                    instance.secretParameterKeys.forEach { key ->
                        SecureKeyManager.removeSecretKey(McpInstanceData.secretKeyId(instance.id, key))
                    }
                }
                Files.delete(path)
                log.debug("Deleted all MCP instances and secrets for project $projectId")
            } catch (e: Exception) {
                log.displayError("Failed to delete MCP instances for project $projectId", e)
            }
        }
    }
}
