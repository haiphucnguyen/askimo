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
import io.askimo.core.mcp.Parameter
import io.askimo.core.mcp.ParameterLocation
import io.askimo.core.mcp.ParameterType
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

    private val configFileName = "mcp-servers.yml"

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

        // Validate parameters
        val paramKeys = mutableSetOf<String>()
        definition.parameters.forEach { param ->
            if (param.key.isBlank()) {
                errors.add("Parameter key cannot be blank")
            }
            if (param.key in paramKeys) {
                errors.add("Duplicate parameter key: ${param.key}")
            }
            paramKeys.add(param.key)

            if (param.label.isBlank()) {
                errors.add("Parameter ${param.key} label cannot be blank")
            }

            // Validate default value against pattern if provided
            if (param.defaultValue != null && param.validationPattern != null) {
                try {
                    val pattern = Regex(param.validationPattern)
                    if (!pattern.matches(param.defaultValue)) {
                        errors.add("Parameter ${param.key} default value does not match validation pattern")
                    }
                } catch (e: Exception) {
                    errors.add("Invalid validation pattern for parameter ${param.key}: ${e.message}")
                }
            }
        }

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
        AskimoHome.base().resolve(configFileName)
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
                    "npx",
                    "-y",
                    "mongodb-mcp-server@latest",
                    "{{?readOnly:--readOnly}}",
                ),
                envTemplate = mapOf(
                    "MONGODB_VARS" to "{{mongoEnv}}",
                ),
            ),
            parameters = listOf(
                Parameter(
                    key = "mongoEnv",
                    label = "MongoDB Environment Variables",
                    type = ParameterType.STRING,
                    required = true,
                    placeholder = "MDB_MCP_CONNECTION_STRING=mongodb://localhost:27017/myDatabase\nor\nMDB_MCP_API_CLIENT_ID=clientId,MDB_MCP_API_CLIENT_SECRET=secret",
                    description = "Environment variables for MongoDB connection. Use 'KEY=value' or 'KEY1=value1,KEY2=value2' for multiple vars",
                    location = ParameterLocation.ENVIRONMENT,
                    allowMultiple = true,
                ),
                Parameter(
                    key = "readOnly",
                    label = "Read Only Mode",
                    type = ParameterType.BOOLEAN,
                    required = false,
                    defaultValue = "true",
                    description = "Only allow read operations (recommended for safety)",
                    location = ParameterLocation.COMMAND,
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
            parameters = listOf(
                Parameter(
                    key = "rootPath",
                    label = "Root Path",
                    type = ParameterType.PATH,
                    required = true,
                    placeholder = "/path/to/directory",
                    description = "Root directory for filesystem access (must be an absolute path)",
                    location = ParameterLocation.COMMAND,
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
