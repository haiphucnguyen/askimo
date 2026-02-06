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
import io.askimo.core.mcp.ProjectMcpInstance
import io.askimo.core.mcp.ProjectMcpInstanceData
import io.askimo.core.util.AskimoHome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private object ProjectMcpInstancesConfigObject
private val log = logger<ProjectMcpInstancesConfigObject>()

object ProjectMcpInstancesConfig {

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
    fun load(projectId: String): List<ProjectMcpInstance> {
        val path = getConfigPath(projectId)

        if (!path.exists()) {
            return emptyList()
        }

        return try {
            val content = Files.readString(path)
            val wrapper: InstancesWrapper = mapper.readValue(content, InstancesWrapper::class.java)
            wrapper.instances.map { it.toDomain() }
        } catch (e: Exception) {
            log.displayError("Failed to load MCP instances for project $projectId", e)
            emptyList()
        }
    }

    /**
     * Save all MCP instances for a project
     */
    fun save(projectId: String, instances: List<ProjectMcpInstance>) {
        val path = getConfigPath(projectId)

        try {
            path.parent.createDirectories()
            val data = instances.map { ProjectMcpInstanceData.from(it) }
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
    fun add(instance: ProjectMcpInstance) {
        val instances = load(instance.projectId).toMutableList()
        instances.removeIf { it.id == instance.id }
        instances.add(instance)
        save(instance.projectId, instances)
    }

    /**
     * Remove a specific instance
     */
    fun remove(projectId: String, instanceId: String) {
        val instances = load(projectId).filterNot { it.id == instanceId }
        save(projectId, instances)
    }

    /**
     * Get a specific instance
     */
    fun get(projectId: String, instanceId: String): ProjectMcpInstance? = load(projectId).find { it.id == instanceId }

    /**
     * Update an existing instance
     */
    fun update(instance: ProjectMcpInstance) {
        add(instance)
    }

    /**
     * Delete all instances for a project (when project is deleted)
     */
    fun deleteAll(projectId: String) {
        val path = getConfigPath(projectId)
        if (path.exists()) {
            try {
                Files.delete(path)
                log.debug("Deleted all MCP instances for project $projectId")
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
    val instances: List<ProjectMcpInstanceData>,
)
