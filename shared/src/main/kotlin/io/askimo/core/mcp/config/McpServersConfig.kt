/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.mcp.McpServerDefinition
import io.askimo.core.mcp.StdioConfig
import io.askimo.core.mcp.TemplateResolver
import io.askimo.core.mcp.TransportType
import io.askimo.core.mcp.ValidationResult
import io.askimo.core.util.AskimoHome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private object McpServersConfigObject
private val log = logger<McpServersConfigObject>()

/**
 * MCP Servers configuration that persists to YAML file.
 * Similar to AppConfig pattern, but for MCP server definitions.
 */
object McpServersConfig {

    @Volatile
    private var cached: List<McpServerDefinition>? = null

    private val mapper: ObjectMapper =
        ObjectMapper(YAMLFactory())
            .registerModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private const val MCP_CONFIG_FILE = "mcp-servers.yml"

    /**
     * Get all registered MCP server definitions
     */
    fun getAll(): List<McpServerDefinition> = cached ?: loadOrCreateDefaults()

    /**
     * Get a specific MCP server definition by ID
     */
    fun get(id: String): McpServerDefinition? = getAll().find { it.id == id }

    /**
     * Add a new MCP server definition
     */
    fun add(definition: McpServerDefinition) {
        synchronized(this) {
            val current = getAll().toMutableList()

            // Remove if already exists (update)
            current.removeIf { it.id == definition.id }

            // Add new definition
            current.add(definition)

            // Update cache and persist
            cached = current
            persist(current)
        }
    }

    /**
     * Remove an MCP server definition
     */
    fun remove(id: String) {
        synchronized(this) {
            val current = getAll().toMutableList()
            if (current.removeIf { it.id == id }) {
                cached = current
                persist(current)
            }
        }
    }

    /**
     * Update an existing MCP server definition
     */
    fun update(definition: McpServerDefinition) {
        add(definition) // Add method handles updates
    }

    /**
     * Reset to built-in defaults
     */
    fun resetToDefaults() {
        synchronized(this) {
            val defaults = getBuiltInDefaults()
            cached = defaults
            persist(defaults)
        }
    }

    /**
     * Reload from disk
     */
    fun reload() {
        synchronized(this) {
            cached = null
            loadOrCreateDefaults()
        }
    }

    /**
     * Search server definitions by name or description
     */
    fun search(query: String): List<McpServerDefinition> {
        val lowerQuery = query.lowercase()
        return getAll().filter {
            it.name.lowercase().contains(lowerQuery) ||
                it.description.lowercase().contains(lowerQuery) ||
                it.tags.any { tag -> tag.lowercase().contains(lowerQuery) }
        }
    }

    /**
     * Validate a server definition
     */
    fun validate(definition: McpServerDefinition): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate ID
        if (definition.id.isBlank()) {
            errors.add("Server definition ID cannot be blank")
        }

        // Validate name
        if (definition.name.isBlank()) {
            errors.add("Server definition name cannot be blank")
        }

        // Validate transport-specific config
        if (definition.transportType == TransportType.STDIO) {
            if (definition.stdioConfig == null) {
                errors.add("STDIO transport requires stdioConfig")
            } else {
                if (definition.stdioConfig.commandTemplate.isEmpty()) {
                    errors.add("STDIO commandTemplate cannot be empty")
                }
                // Validate command templates
                definition.stdioConfig.commandTemplate.forEach { template ->
                    val result = TemplateResolver.validate(template)
                    if (!result.isValid) {
                        errors.addAll(result.errors.map { "Command template error: $it" })
                    }
                }
            }
        }

        // Just basic validation - MCP server will validate the actual parameters
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
        )
    }

    /**
     * Get count of registered definitions
     */
    fun count(): Int = getAll().size

    private fun loadOrCreateDefaults(): List<McpServerDefinition> {
        synchronized(this) {
            val path = resolveConfigPath()

            return if (path != null && path.exists()) {
                try {
                    val content = Files.readString(path)
                    val wrapper: McpServersWrapper = mapper.readValue(content)
                    cached = wrapper.servers
                    log.debug("Loaded {} MCP server definitions from {}", wrapper.servers.size, path)
                    wrapper.servers
                } catch (e: Exception) {
                    log.displayError("Failed to load MCP servers config, using defaults", e)
                    val defaults = getBuiltInDefaults()
                    cached = defaults
                    defaults
                }
            } else {
                // First run: create config with defaults
                val defaults = getBuiltInDefaults()
                if (path != null) {
                    path.parent?.createDirectories()
                    persist(defaults)
                    log.debug("Created default MCP servers config at {}", path)
                }
                cached = defaults
                defaults
            }
        }
    }

    private fun persist(definitions: List<McpServerDefinition>) {
        val path = resolveConfigPath() ?: return

        try {
            path.parent?.createDirectories()
            val wrapper = McpServersWrapper(definitions)
            val yaml = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper)
            Files.writeString(path, yaml)
            log.debug("Persisted {} MCP server definitions to {}", definitions.size, path)
        } catch (e: Exception) {
            log.displayError("Failed to persist MCP servers config", e)
        }
    }

    private fun resolveConfigPath(): Path? = try {
        AskimoHome.base().resolve(MCP_CONFIG_FILE)
    } catch (e: Exception) {
        log.displayError("Failed to resolve MCP servers config path", e)
        null
    }

    /**
     * Built-in default MCP server definitions
     */
    private fun getBuiltInDefaults(): List<McpServerDefinition> = listOf(
        McpServerDefinition(
            id = "mongodb-mcp-server",
            name = "MongoDB MCP Server",
            description = "Connect to MongoDB databases using the official MCP server",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf(
                    "docker",
                    "run",
                    "--rm",
                    "-e",
                    "MDB_MCP_READ_ONLY=true",
                    "-e",
                    "MDB_MCP_CONNECTION_STRING",
                    "-i",
                    "mongodb/mongodb-mcp-server:latest",
                ),
                envTemplate = mapOf(
                    "MDB_MCP_CONNECTION_STRING" to "{{mongoConnectionStr}}",
                ),
            ),
            tags = listOf("database", "mongodb", "nosql", "official"),
        ),

        // Filesystem MCP Server
        McpServerDefinition(
            id = "filesystem-mcp-server",
            name = "Filesystem MCP Server",
            description = "Access and search local files and directories using the official Anthropic MCP server",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf(
                    "npx",
                    "-y",
                    "@modelcontextprotocol/server-filesystem",
                    "{{rootPath}}",
                ),
            ),
            tags = listOf("filesystem", "local", "files", "official", "anthropic"),
        ),
    )
}

/**
 * Wrapper for YAML serialization
 */
private data class McpServersWrapper(
    val servers: List<McpServerDefinition>,
)
