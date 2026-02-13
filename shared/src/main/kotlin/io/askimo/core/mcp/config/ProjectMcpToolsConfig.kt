/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.util.AskimoHome
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private object ProjectMcpToolsConfigObject
private val log = logger<ProjectMcpToolsConfigObject>()

/**
 * Configuration for persisting user-customized tool classifications.
 * Stores user overrides for MCP tool categories and strategies.
 *
 * Flow:
 * 1. First load: Auto-infer category/strategy from tool metadata
 * 2. Save to YAML with autoInferred=true
 * 3. User can customize via UI
 * 4. Custom values saved with autoInferred=false
 * 5. Next load: Use persisted values (user custom or auto-inferred)
 */
object ProjectMcpToolsConfig {

    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private const val CONFIG_FILE_NAME = "mcp-tools-config.yml"

    private fun getConfigPath(projectId: String): Path = AskimoHome.base()
        .resolve("projects")
        .resolve(projectId)
        .resolve(CONFIG_FILE_NAME)

    /**
     * Load all tool configurations for a project.
     * Returns map of "instanceId:toolName" -> ToolConfigData
     */
    fun load(projectId: String): Map<String, ToolConfigData> {
        val path = getConfigPath(projectId)

        if (!path.exists()) {
            return emptyMap()
        }

        return try {
            val content = Files.readString(path)
            val wrapper: ToolsConfigWrapper = mapper.readValue(content, ToolsConfigWrapper::class.java)
            // Use composite key: "instanceId:toolName" to avoid conflicts
            wrapper.tools.associateBy { "${it.instanceId}:${it.toolName}" }
        } catch (e: Exception) {
            log.displayError("Failed to load MCP tool configs for project $projectId", e)
            emptyMap()
        }
    }

    /**
     * Save all tool configurations for a project
     */
    fun save(projectId: String, configs: Map<String, ToolConfigData>) {
        val path = getConfigPath(projectId)

        try {
            // Ensure directory exists
            path.parent.createDirectories()

            // Write YAML
            val wrapper = ToolsConfigWrapper(tools = configs.values.toList())
            val yaml = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper)
            Files.writeString(path, yaml)

            log.debug("Saved ${configs.size} tool configs for project $projectId")
        } catch (e: Exception) {
            log.displayError("Failed to save MCP tool configs for project $projectId", e)
        }
    }

    /**
     * Get configuration for a specific tool from a specific instance
     */
    fun get(projectId: String, instanceId: String, toolName: String): ToolConfigData? {
        val key = "$instanceId:$toolName"
        return load(projectId)[key]
    }

    /**
     * Get all tools for a specific instance (maintains hierarchy).
     * This makes the server-tool relationship explicit.
     */
    fun getByInstance(projectId: String, instanceId: String): Map<String, ToolConfigData> = load(projectId).filter { (_, config) ->
        config.instanceId == instanceId
    }

    /**
     * Get tool names for a specific instance (convenience method).
     */
    fun getToolNamesForInstance(projectId: String, instanceId: String): List<String> = getByInstance(projectId, instanceId).values.map { it.toolName }

    /**
     * Update or create configuration for a tool
     */
    fun update(projectId: String, config: ToolConfigData) {
        val configs = load(projectId).toMutableMap()
        val key = "${config.instanceId}:${config.toolName}"
        configs[key] = config.copy(updatedAt = LocalDateTime.now())
        save(projectId, configs)
    }

    /**
     * Batch update tools for an instance (atomic operation).
     * Useful when adding a new MCP instance with multiple tools.
     */
    fun updateInstanceTools(projectId: String, instanceId: String, tools: List<ToolConfigData>) {
        val configs = load(projectId).toMutableMap()

        tools.forEach { config ->
            require(config.instanceId == instanceId) {
                "Tool instanceId mismatch: expected $instanceId, got ${config.instanceId}"
            }
            val key = "${config.instanceId}:${config.toolName}"
            configs[key] = config
        }

        save(projectId, configs)
        log.debug("Updated ${tools.size} tools for instance $instanceId in project $projectId")
    }

    /**
     * Delete configuration for a specific tool
     */
    fun delete(projectId: String, instanceId: String, toolName: String) {
        val configs = load(projectId).toMutableMap()
        val key = "$instanceId:$toolName"
        configs.remove(key)
        save(projectId, configs)
    }

    /**
     * Delete all tools for a specific instance (cascade delete).
     * Called when an MCP instance is deleted.
     */
    fun deleteByInstance(projectId: String, instanceId: String) {
        val configs = load(projectId).toMutableMap()
        val keysToRemove = configs.keys.filter { it.startsWith("$instanceId:") }
        keysToRemove.forEach { configs.remove(it) }
        save(projectId, configs)
        log.debug("Deleted ${keysToRemove.size} tools for instance $instanceId in project $projectId")
    }

    /**
     * Delete all tool configs for a project (called when project is deleted).
     */
    fun deleteAll(projectId: String) {
        val path = getConfigPath(projectId)
        if (path.exists()) {
            try {
                Files.delete(path)
                log.debug("Deleted all tool configs for project $projectId")
            } catch (e: Exception) {
                log.displayError("Failed to delete tool configs for project $projectId", e)
            }
        }
    }

    /**
     * Get statistics about tools in a project.
     * Useful for debugging and monitoring.
     */
    fun getStats(projectId: String): ToolConfigStats {
        val configs = load(projectId)
        val byInstance = configs.values.groupBy { it.instanceId }

        return ToolConfigStats(
            totalTools = configs.size,
            userCustomized = configs.values.count { !it.autoInferred },
            autoInferred = configs.values.count { it.autoInferred },
            instanceCount = byInstance.size,
            toolsByInstance = byInstance.mapValues { it.value.size },
        )
    }
}

/**
 * Data class for persisting tool configuration.
 * Stores user's customization of auto-inferred tool classifications.
 */
data class ToolConfigData(
    val toolName: String, // e.g., "search_github"
    val instanceId: String, // Which MCP instance this tool comes from
    val category: String, // User-overridden category (or auto-inferred)
    val strategy: Int, // User-overridden strategy (or auto-inferred)
    val autoInferred: Boolean = true, // Was this auto-inferred or user-customized?
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * Wrapper for YAML serialization
 */
private data class ToolsConfigWrapper(
    val tools: List<ToolConfigData>,
)

/**
 * Statistics about tool configurations in a project.
 * Useful for debugging and monitoring.
 */
data class ToolConfigStats(
    val totalTools: Int,
    val userCustomized: Int,
    val autoInferred: Int,
    val instanceCount: Int,
    val toolsByInstance: Map<String, Int>,
)
