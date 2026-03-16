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
import io.askimo.core.mcp.GLOBAL_MCP_SCOPE_ID
import io.askimo.core.mcp.McpInstance
import io.askimo.core.mcp.McpInstanceData
import io.askimo.core.security.SecureKeyManager
import io.askimo.core.util.AskimoHome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private object GlobalMcpInstancesConfigObject
private val log = logger<GlobalMcpInstancesConfigObject>()

/**
 * Global MCP instances (available to Universal Chat).
 *
 * Stored at: ~/.askimo/<profile>/mcp-instances.yml
 *
 * We reuse [McpInstance] to avoid duplicating connector creation and
 * secret handling. The [McpInstance.projectId] is always normalised to
 * [GLOBAL_MCP_SCOPE_ID].
 */
object GlobalMcpInstancesConfig : McpInstancesConfig {

    private val mapper: ObjectMapper =
        ObjectMapper(YAMLFactory())
            .registerModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private const val CONFIG_FILE_NAME = "mcp-instances.yml"

    private fun getConfigPath(): Path = AskimoHome.base().resolve(CONFIG_FILE_NAME)

    override fun load(projectId: String): List<McpInstance> {
        // projectId is ignored; global scope only
        val path = getConfigPath()
        if (!path.exists()) return emptyList()

        return try {
            val content = Files.readString(path)
            val wrapper = mapper.readValue(content, InstancesWrapper::class.java)
            wrapper.instances.map { it.toDomain() }
                .map { it.copy(projectId = GLOBAL_MCP_SCOPE_ID) }
        } catch (e: Exception) {
            log.displayError("Failed to load global MCP instances", e)
            emptyList()
        }
    }

    override fun save(projectId: String, instances: List<McpInstance>) {
        // projectId is ignored
        val path = getConfigPath()

        try {
            path.parent.createDirectories()

            val data = instances.map { instance ->
                val normalized = instance.copy(projectId = GLOBAL_MCP_SCOPE_ID)
                val definition = McpServersConfig.get(normalized.serverId)
                McpInstanceData.from(normalized, definition)
            }

            val wrapper = InstancesWrapper(data)
            val yaml = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper)
            Files.writeString(path, yaml)
            log.debug("Saved ${instances.size} global MCP instances")
        } catch (e: Exception) {
            log.displayError("Failed to save global MCP instances", e)
        }
    }

    override fun add(instance: McpInstance) {
        val normalized = instance.copy(projectId = GLOBAL_MCP_SCOPE_ID)
        val instances = load(GLOBAL_MCP_SCOPE_ID).toMutableList()
        instances.removeIf { it.id == normalized.id }
        instances.add(normalized)
        save(GLOBAL_MCP_SCOPE_ID, instances)
    }

    override fun get(projectId: String, instanceId: String): McpInstance? = load(GLOBAL_MCP_SCOPE_ID).find { it.id == instanceId }

    override fun remove(projectId: String, instanceId: String) {
        val path = getConfigPath()

        // Clean up secrets for this instance first
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
                log.displayError("Failed to clean up secrets for global instance $instanceId", e)
            }
        }

        save(GLOBAL_MCP_SCOPE_ID, load(GLOBAL_MCP_SCOPE_ID).filterNot { it.id == instanceId })
    }

    override fun deleteAll(projectId: String) {
        val path = getConfigPath()
        if (path.exists()) {
            try {
                val content = Files.readString(path)
                val wrapper = mapper.readValue(content, InstancesWrapper::class.java)
                wrapper.instances.forEach { instance ->
                    instance.secretParameterKeys.forEach { key ->
                        SecureKeyManager.removeSecretKey(McpInstanceData.secretKeyId(instance.id, key))
                    }
                }
                Files.delete(path)
                log.debug("Deleted all global MCP instances and secrets")
            } catch (e: Exception) {
                log.displayError("Failed to delete global MCP instances", e)
            }
        }
    }

    // Convenience helpers
    fun load(): List<McpInstance> = load(GLOBAL_MCP_SCOPE_ID)
    fun save(instances: List<McpInstance>) = save(GLOBAL_MCP_SCOPE_ID, instances)
    fun get(instanceId: String): McpInstance? = get(GLOBAL_MCP_SCOPE_ID, instanceId)
    fun remove(instanceId: String) = remove(GLOBAL_MCP_SCOPE_ID, instanceId)

    private data class InstancesWrapper(
        val instances: List<McpInstanceData>,
    )
}
