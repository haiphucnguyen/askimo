/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.mcp.McpInstance
import io.askimo.core.mcp.McpInstanceData
import io.askimo.core.mcp.SecretDetector
import io.askimo.core.security.SecureKeyManager
import io.askimo.core.util.AskimoHome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private object ProjectMcpInstancesConfigObject
private val log = logger<ProjectMcpInstancesConfigObject>()

object ProjectMcpInstancesConfig : McpInstancesConfig {

    private val mapper: ObjectMapper =
        ObjectMapper(YAMLFactory())
            .registerModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private const val CONFIG_FILE_NAME = "mcp-instances.yml"

    private fun getConfigPath(projectId: String): Path = AskimoHome.base()
        .resolve("projects")
        .resolve(projectId)
        .resolve(CONFIG_FILE_NAME)

    /**
     * Load all MCP instances for a project
     */
    override fun load(projectId: String): List<McpInstance> {
        val path = getConfigPath(projectId)

        if (!path.exists()) {
            return emptyList()
        }

        return try {
            val content = Files.readString(path)
            val wrapper = mapper.readValue(content, InstancesWrapper::class.java)
            wrapper.instances.map { it.toDomain() }
        } catch (e: Exception) {
            log.displayError("Failed to load MCP instances for project $projectId", e)
            emptyList()
        }
    }

    /**
     * Save all MCP instances for a project.
     * Looks up each instance's [McpServerDefinition] so [SecretDetector] can use
     * the authoritative [ParameterType.SECRET] flag instead of relying solely on
     * naming conventions.
     */
    override fun save(projectId: String, instances: List<McpInstance>) {
        val path = getConfigPath(projectId)

        try {
            path.parent.createDirectories()
            val data = instances.map { instance ->
                val definition = McpServersConfig.get(instance.serverId)
                McpInstanceData.from(instance, definition)
            }
            val wrapper = InstancesWrapper(data)
            val yaml = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper)
            Files.writeString(path, yaml)
            log.debug("Saved ${instances.size} MCP instances for project $projectId")
        } catch (e: Exception) {
            log.displayError("Failed to save MCP instances for project $projectId", e)
        }
    }

    /**
     * Add or update a single instance
     */
    override fun add(instance: McpInstance) {
        val projectId = instance.projectId ?: return
        val instances = load(projectId).toMutableList()
        instances.removeIf { it.id == instance.id }
        instances.add(instance)
        save(projectId, instances)
    }

    /**
     * Remove a specific instance and clean up its secrets from [SecureKeyManager].
     */
    override fun remove(projectId: String, instanceId: String) {
        // Load the raw data (not domain) so we can read secretParameterKeys before deletion
        val path = getConfigPath(projectId)
        if (path.exists()) {
            try {
                val content = Files.readString(path)
                val wrapper = mapper.readValue(content, InstancesWrapper::class.java)
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
     * Get a specific instance
     */
    override fun get(projectId: String, instanceId: String): McpInstance? = load(projectId).find { it.id == instanceId }

    /**
     * Update an existing instance
     */
    override fun update(instance: McpInstance) {
        add(instance)
    }

    /**
     * Delete all instances for a project (when project is deleted).
     * Also removes all associated secrets from [SecureKeyManager].
     */
    override fun deleteAll(projectId: String) {
        val path = getConfigPath(projectId)
        if (path.exists()) {
            try {
                // Clean up secrets before deleting the file
                val content = Files.readString(path)
                val wrapper = mapper.readValue(content, InstancesWrapper::class.java)
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

/**
 * Wrapper for YAML serialization
 */
private data class InstancesWrapper(
    val instances: List<McpInstanceData>,
)
