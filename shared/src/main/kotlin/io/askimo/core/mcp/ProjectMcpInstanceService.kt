/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.mcp.client.DefaultMcpClient
import io.askimo.core.intent.ToolCategory
import io.askimo.core.intent.ToolConfig
import io.askimo.core.intent.ToolSource
import io.askimo.core.intent.ToolStrategy
import io.askimo.core.logging.logger
import io.askimo.core.mcp.config.McpServersConfig
import io.askimo.core.mcp.config.ProjectMcpInstancesConfig
import io.askimo.core.mcp.config.ProjectMcpToolsConfig
import io.askimo.core.mcp.config.ToolConfigData
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private val log = logger<ProjectMcpInstanceService>()

/**
 * Service for managing project-specific MCP server instances.
 * Handles loading, persisting, and lifecycle operations for MCP instances.
 *
 * This service is registered as a singleton in Koin for dependency injection.
 */
class ProjectMcpInstanceService(
    private val instancesConfig: ProjectMcpInstancesConfig = ProjectMcpInstancesConfig,
    private val serversConfig: McpServersConfig = McpServersConfig,
) {
    /**
     * Cache of project tools to avoid repeated fetching from MCP servers.
     * Cache keys: projectId
     * Caffeine provides automatic eviction when memory is low or tools are inactive.
     */
    private val projectToolsCache: Cache<String, List<ToolConfig>> = Caffeine.newBuilder()
        .maximumSize(50) // Cache tools for up to 50 projects
        .expireAfterWrite(10.minutes.toJavaDuration()) // Refresh every 10 minutes
        .removalListener<String, List<ToolConfig>> { projectId, tools, cause ->
            if (tools != null && projectId != null) {
                log.debug("Evicting project tools cache for project {} (cause: {}, {} tools)", projectId, cause, tools.size)
            }
        }
        .build()

    /**
     * Get all MCP instances for a project
     */
    fun getInstances(projectId: String): List<ProjectMcpInstance> = instancesConfig.load(projectId)

    /**
     * Get a specific instance by ID
     */
    fun getInstance(projectId: String, instanceId: String): ProjectMcpInstance? = instancesConfig.get(projectId, instanceId)

    /**
     * Create a new MCP instance for a project
     */
    fun createInstance(
        projectId: String,
        serverId: String,
        name: String,
        parameterValues: Map<String, String>,
    ): Result<ProjectMcpInstance> {
        return try {
            // Validate server definition exists
            val definition = serversConfig.get(serverId)
                ?: return Result.failure(IllegalArgumentException("MCP server definition not found: $serverId"))

            // Create instance
            val instance = ProjectMcpInstance(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                serverId = serverId,
                name = name,
                parameterValues = parameterValues,
                enabled = true,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

            // Validate parameters by attempting to create connector
            instance.toConnector(definition)

            // Save
            instancesConfig.add(instance)

            // Invalidate tools cache since instances changed
            invalidateProjectToolsCache(projectId)

            log.debug("Created MCP instance '${instance.name}' (${instance.id}) for project $projectId")
            Result.success(instance)
        } catch (e: Exception) {
            log.warn("Failed to create MCP instance: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update an existing instance
     */
    fun updateInstance(
        projectId: String,
        instanceId: String,
        name: String? = null,
        parameterValues: Map<String, String>? = null,
        enabled: Boolean? = null,
    ): Result<ProjectMcpInstance> {
        return try {
            val existing = getInstance(projectId, instanceId)
                ?: return Result.failure(IllegalArgumentException("Instance not found: $instanceId"))

            val updated = existing.copy(
                name = name ?: existing.name,
                parameterValues = parameterValues ?: existing.parameterValues,
                enabled = enabled ?: existing.enabled,
                updatedAt = LocalDateTime.now(),
            )

            // Validate if parameters changed
            if (parameterValues != null) {
                val definition = serversConfig.get(updated.serverId)
                    ?: return Result.failure(IllegalStateException("Server definition not found: ${updated.serverId}"))
                updated.toConnector(definition) // Validate
            }

            instancesConfig.update(updated)

            // Invalidate tools cache since instances changed
            invalidateProjectToolsCache(projectId)

            log.debug("Updated MCP instance '${updated.name}' (${updated.id})")
            Result.success(updated)
        } catch (e: Exception) {
            log.warn("Failed to update MCP instance: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete an instance and its associated tools
     */
    fun deleteInstance(projectId: String, instanceId: String): Result<Unit> {
        return try {
            val instance = getInstance(projectId, instanceId)
                ?: return Result.failure(IllegalArgumentException("Instance not found: $instanceId"))

            instancesConfig.remove(projectId, instanceId)
            // Also delete all tools associated with this instance
            ProjectMcpToolsConfig.deleteByInstance(projectId, instanceId)

            // Invalidate tools cache since instances changed
            invalidateProjectToolsCache(projectId)

            log.debug("Deleted MCP instance '${instance.name}' (${instance.id}) and its tools")
            Result.success(Unit)
        } catch (e: Exception) {
            log.warn("Failed to delete MCP instance: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Enable/disable an instance
     */
    fun setInstanceEnabled(projectId: String, instanceId: String, enabled: Boolean): Result<ProjectMcpInstance> = updateInstance(projectId, instanceId, enabled = enabled)

    /**
     * Get all enabled instances for a project
     */
    fun getEnabledInstances(projectId: String): List<ProjectMcpInstance> = getInstances(projectId).filter { it.enabled }

    /**
     * Create an MCP client for a specific instance.
     *
     * @param instance The MCP instance to create a client for
     * @param clientKey Unique key for the client
     * @return DefaultMcpClient or null if creation fails
     */
    suspend fun createMcpClient(
        instance: ProjectMcpInstance,
        clientKey: String,
    ): DefaultMcpClient? {
        return try {
            val definition = serversConfig.get(instance.serverId)
            if (definition == null) {
                log.warn("Server definition not found for instance '${instance.name}': ${instance.serverId}")
                return null
            }

            log.debug("Creating connector for instance '${instance.name}' (${instance.serverId})")
            val connector = instance.toConnector(definition)

            // Validate connector configuration
            log.debug("Validating connector '${instance.name}'")
            val validationResult = connector.validate()
            if (!validationResult.isValid) {
                log.warn("Connector '${instance.name}' validation failed: ${validationResult.errors.joinToString(", ")}")
                return null
            }

            // Create transport
            log.debug("Creating transport for connector '${instance.name}'")
            val transport = try {
                connector.createTransport()
            } catch (e: Exception) {
                log.error("Failed to create transport for '${instance.name}': ${e.javaClass.simpleName}: ${e.message}", e)
                return null
            }

            log.debug("Creating MCP client for instance '${instance.name}'")
            val client = DefaultMcpClient.builder()
                .key(clientKey)
                .transport(transport)
                .build()

            log.debug("Successfully created MCP client for '${instance.name}'")
            client
        } catch (e: Exception) {
            log.error("Failed to create MCP client for '${instance.name}': ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    /**
     * Infer tool category from tool specification metadata.
     * Uses heuristics based on tool name and description to classify functionality.
     *
     * @param toolSpec The tool specification to analyze
     * @return ToolCategory that best matches the tool's purpose
     */
    fun inferToolCategory(toolSpec: ToolSpecification): io.askimo.core.intent.ToolCategory {
        val name = toolSpec.name().lowercase()
        val description = toolSpec.description()?.lowercase() ?: ""

        // Database operations
        if (name.contains("database") || name.contains("db") || name.contains("sql") ||
            name.contains("query") || name.contains("postgres") || name.contains("mysql") ||
            name.contains("mongo") || name.contains("redis") ||
            description.contains("database") || description.contains("query") ||
            description.contains("table") || description.contains("schema")
        ) {
            return ToolCategory.DATABASE
        }

        // Network/API operations
        if (name.contains("http") || name.contains("api") || name.contains("webhook") ||
            name.contains("request") || name.contains("fetch") || name.contains("curl") ||
            description.contains("http") || description.contains("api") ||
            description.contains("request") || description.contains("endpoint")
        ) {
            return ToolCategory.NETWORK
        }

        // Visualization
        if (name.contains("chart") || name.contains("graph") || name.contains("plot") ||
            name.contains("visualiz") || name.contains("diagram") ||
            description.contains("chart") || description.contains("visualiz") ||
            description.contains("graph") || description.contains("plot")
        ) {
            return ToolCategory.VISUALIZE
        }

        // Search operations
        if (name.startsWith("search_") || name.startsWith("find_") || name.startsWith("query_") ||
            name.contains("lookup") ||
            description.contains("search") || description.contains("find") && !description.contains("file")
        ) {
            return ToolCategory.SEARCH
        }

        // File read operations
        if (name.contains("read_file") || name.contains("get_file") || name.contains("list_file") ||
            name.contains("show_file") || name.contains("cat_") ||
            (description.contains("read") && description.contains("file")) ||
            (description.contains("get") && description.contains("file"))
        ) {
            return ToolCategory.FILE_READ
        }

        // File write operations
        if (name.contains("write_file") || name.contains("create_file") || name.contains("delete_file") ||
            name.contains("save_file") || name.contains("remove_file") || name.contains("mkdir") ||
            (description.contains("write") && description.contains("file")) ||
            (description.contains("create") && description.contains("file")) ||
            (description.contains("delete") && description.contains("file"))
        ) {
            return ToolCategory.FILE_WRITE
        }

        // Data transformation
        if (name.contains("convert") || name.contains("transform") || name.contains("parse") ||
            name.contains("format") || name.contains("encode") || name.contains("decode") ||
            description.contains("convert") || description.contains("transform") ||
            description.contains("parse") || description.contains("format")
        ) {
            return ToolCategory.TRANSFORM
        }

        // Version control
        if (name.contains("git") || name.contains("commit") || name.contains("branch") ||
            name.contains("merge") || name.contains("pull_request") || name.contains("pr_") ||
            description.contains("git") || description.contains("version control") ||
            description.contains("repository") || description.contains("commit")
        ) {
            return ToolCategory.VERSION_CONTROL
        }

        // Communication
        if (name.contains("email") || name.contains("slack") || name.contains("notify") ||
            name.contains("message") || name.contains("send") || name.contains("post_") ||
            description.contains("email") || description.contains("slack") ||
            description.contains("notification") || description.contains("message")
        ) {
            return ToolCategory.COMMUNICATION
        }

        // Monitoring/Logging
        if (name.contains("log") || name.contains("monitor") || name.contains("track") ||
            name.contains("metric") || name.contains("alert") ||
            description.contains("log") || description.contains("monitor") ||
            description.contains("track") || description.contains("metric")
        ) {
            return ToolCategory.MONITORING
        }

        // Execute/Command operations
        if (name.contains("execute") || name.contains("run") || name.contains("command") ||
            name.contains("shell") || name.contains("script") || name.contains("install") ||
            description.contains("execute") || description.contains("run") ||
            description.contains("command") || description.contains("shell")
        ) {
            return ToolCategory.EXECUTE
        }

        // Default to OTHER for unclassified tools
        log.debug("Tool '${toolSpec.name()}' classified as OTHER (unrecognized category)")
        return ToolCategory.OTHER
    }

    /**
     * Infer execution strategy from tool specification metadata.
     * Uses heuristics based on tool name and description to determine safety.
     *
     * Strategy Philosophy:
     * - INTENT_BASED: Most operations including safe reads AND writes (default)
     *   Examples: read, write, save, create, update, edit, search, list
     * - FOLLOW_UP_BASED: Only truly DANGEROUS destructive operations
     *   Examples: drop database, delete all, truncate table, system shutdown
     *
     * This allows AI to handle common write operations (like "save summary to file")
     * while still protecting against catastrophic actions.
     *
     * @param toolSpec The tool specification to analyze
     * @return Strategy flag (INTENT_BASED for safe ops, FOLLOW_UP_BASED for dangerous ops)
     */
    fun inferToolStrategy(toolSpec: ToolSpecification): Int {
        val name = toolSpec.name().lowercase()
        val description = toolSpec.description()?.lowercase() ?: ""

        // === FOLLOW_UP_BASED: Only truly dangerous operations ===

        // 1. Database/Schema destruction (drop database, drop table, truncate)
        if ((name.contains("drop") || name.contains("truncate")) &&
            (
                name.contains("database") || name.contains("db") ||
                    name.contains("table") || name.contains("schema") ||
                    description.contains("drop database") || description.contains("drop table")
                )
        ) {
            log.debug("Tool '${toolSpec.name()}' classified as FOLLOW_UP_BASED (database destruction)")
            return ToolStrategy.FOLLOW_UP_BASED
        }

        // 2. Bulk/Mass deletion (delete all, remove all, clear all, wipe)
        if ((
                name.contains("delete") || name.contains("remove") ||
                    name.contains("clear") || name.contains("wipe")
                ) &&
            (
                name.contains("all") || name.contains("everything") ||
                    description.contains("delete all") || description.contains("remove all") ||
                    description.contains("clear all") || description.contains("wipe all") ||
                    description.contains("bulk delete") || description.contains("mass delete")
                )
        ) {
            log.debug("Tool '${toolSpec.name()}' classified as FOLLOW_UP_BASED (bulk deletion)")
            return ToolStrategy.FOLLOW_UP_BASED
        }

        // 3. System-level dangerous commands (shutdown, restart, format, irreversible)
        if (name.contains("shutdown") || name.contains("restart") ||
            name.contains("reboot") || name.contains("format") ||
            description.contains("shutdown") || description.contains("restart") ||
            description.contains("irreversible") || description.contains("cannot be undone") ||
            description.contains("permanent deletion")
        ) {
            log.debug("Tool '${toolSpec.name()}' classified as FOLLOW_UP_BASED (system danger)")
            return ToolStrategy.FOLLOW_UP_BASED
        }

        // 4. Security-critical permission changes (chmod 777, grant admin, security override)
        if ((name.contains("chmod") || name.contains("permission") || name.contains("grant")) &&
            (
                description.contains("777") || description.contains("full access") ||
                    description.contains("admin rights") || description.contains("root access") ||
                    description.contains("bypass security")
                )
        ) {
            log.debug("Tool '${toolSpec.name()}' classified as FOLLOW_UP_BASED (security risk)")
            return ToolStrategy.FOLLOW_UP_BASED
        }

        // === INTENT_BASED: Everything else (default - includes safe reads AND writes) ===

        // This includes:
        // ✅ Read operations: search, get, list, read, find, fetch, query
        // ✅ Safe write operations: write, save, create, update, modify, edit
        // ✅ Single-item delete: delete_file, remove_item (not bulk)
        // ✅ Configuration: set, configure
        // ✅ Installation: install, deploy (package-level, not system-level)

        log.debug("Tool '${toolSpec.name()}' classified as INTENT_BASED (safe operation)")
        return ToolStrategy.INTENT_BASED
    }

    /**
     * Get all available tools from all enabled MCP instances for a project.
     *
     * **Hybrid Mode:**
     * 1. Load user-customized tool configs from persistence
     * 2. For tools without user config, auto-infer category/strategy
     * 3. Auto-save newly inferred tools to allow future customization
     *
     * This enables:
     * - First run: Auto-classification (fast onboarding)
     * - User correction: Override incorrect classifications
     * - Performance: Cached classifications, no re-inference
     *
     * **Caching:**
     * - Tools are cached per project for 10 minutes to avoid repeated MCP server calls
     * - Cache is automatically invalidated when instances are updated
     * - Call invalidateProjectToolsCache() to manually clear cache
     *
     * @param projectId The project ID
     * @return List of ToolConfig with hybrid user/auto classifications
     */
    suspend fun getProjectTools(projectId: String): List<ToolConfig> {
        // Check cache first
        val cachedTools = projectToolsCache.getIfPresent(projectId)
        if (cachedTools != null) {
            log.debug("Returning cached tools for project {} ({} tools)", projectId, cachedTools.size)
            return cachedTools
        }

        log.debug("Cache miss for project {}, fetching tools from MCP servers", projectId)

        val instances = getEnabledInstances(projectId)

        if (instances.isEmpty()) {
            log.debug("No active MCP connectors for project $projectId, returning empty list")
            return emptyList()
        }

        // 1. Load persisted user configurations
        val userConfigs = ProjectMcpToolsConfig.load(projectId)
        val newlyInferredConfigs = mutableListOf<ToolConfigData>()

        val allTools = mutableListOf<ToolConfig>()

        // 2. Fetch tools from each instance
        instances.forEach { instance ->
            try {
                val mcpClient = createMcpClient(instance, "tools_${projectId}_${instance.id}")
                if (mcpClient != null) {
                    log.debug("Fetching tools from MCP client for '${instance.name}'")
                    val toolSpecs = mcpClient.listTools()

                    val toolConfigs = toolSpecs.map { toolSpec ->
                        val toolName = toolSpec.name()

                        // 3. Check if user has customized this tool (use composite key)
                        val compositeKey = "${instance.id}:$toolName"
                        val userConfig = userConfigs[compositeKey]

                        if (userConfig != null && !userConfig.autoInferred) {
                            // ✅ Use user's custom classification
                            log.debug("Using user-customized config for tool '$toolName': ${userConfig.category}, ${userConfig.strategy}")
                            ToolConfig(
                                specification = toolSpec,
                                category = ToolCategory.valueOf(userConfig.category),
                                strategy = userConfig.strategy,
                                source = ToolSource.MCP_EXTERNAL,
                            )
                        } else {
                            // ✅ Auto-infer classification
                            val inferredCategory = inferToolCategory(toolSpec)
                            val inferredStrategy = inferToolStrategy(toolSpec)

                            log.debug("Auto-inferred tool '{}': {}, {}", toolName, inferredCategory, inferredStrategy)

                            // Save for future user customization (only if not already persisted)
                            if (userConfig == null) {
                                newlyInferredConfigs.add(
                                    ToolConfigData(
                                        toolName = toolName,
                                        instanceId = instance.id,
                                        category = inferredCategory.name,
                                        strategy = inferredStrategy,
                                        autoInferred = true,
                                    ),
                                )
                            }

                            ToolConfig(
                                specification = toolSpec,
                                category = inferredCategory,
                                strategy = inferredStrategy,
                                source = ToolSource.MCP_EXTERNAL,
                            )
                        }
                    }

                    allTools.addAll(toolConfigs)
                    log.debug("Successfully fetched ${toolSpecs.size} tools from instance '${instance.name}'")
                } else {
                    log.warn("Failed to create MCP client for instance '${instance.name}'")
                }
            } catch (e: Exception) {
                log.error("Failed to fetch tools from instance '${instance.name}': ${e.message}", e)
            }
        }

        // 4. Persist newly inferred tools for future customization
        if (newlyInferredConfigs.isNotEmpty()) {
            val updatedConfigs = userConfigs.toMutableMap()
            newlyInferredConfigs.forEach { config ->
                val compositeKey = "${config.instanceId}:${config.toolName}"
                updatedConfigs[compositeKey] = config
            }
            ProjectMcpToolsConfig.save(projectId, updatedConfigs)
            log.debug("Persisted ${newlyInferredConfigs.size} auto-inferred tool configs for future customization")
        }

        log.debug(
            "Retrieved total of ${allTools.size} MCP tools " +
                "(${userConfigs.count { !it.value.autoInferred }} user-customized, " +
                "${newlyInferredConfigs.size} newly auto-inferred)",
        )

        // 5. Cache the results for future requests
        projectToolsCache.put(projectId, allTools)
        log.debug("Cached {} tools for project {}", allTools.size, projectId)

        return allTools
    }

    /**
     * Invalidate the tools cache for a specific project.
     * Call this when instances are added/removed/updated to force a refresh.
     *
     * @param projectId The project ID to invalidate cache for
     */
    fun invalidateProjectToolsCache(projectId: String) {
        projectToolsCache.invalidate(projectId)
        log.debug("Invalidated tools cache for project {}", projectId)
    }

    /**
     * Allow user to customize tool classification.
     * This overrides auto-inferred category/strategy.
     *
     * @param projectId The project ID
     * @param instanceId The MCP instance ID
     * @param toolName The tool name to customize
     * @param category User-selected category
     * @param strategy User-selected strategy
     * @return Result with updated ToolConfigData
     */
    fun customizeToolConfig(
        projectId: String,
        instanceId: String,
        toolName: String,
        category: ToolCategory,
        strategy: Int,
    ): Result<ToolConfigData> {
        return try {
            // Get existing config (might be auto-inferred or user-customized)
            val existing = ProjectMcpToolsConfig.get(projectId, instanceId, toolName)
                ?: return Result.failure(IllegalArgumentException("Tool not found: $toolName"))

            // Update with user's custom values
            val updated = existing.copy(
                category = category.name,
                strategy = strategy,
                autoInferred = false, // ✅ Mark as user-customized!
                updatedAt = LocalDateTime.now(),
            )

            ProjectMcpToolsConfig.update(projectId, updated)

            // Invalidate tools cache to pick up the customization
            invalidateProjectToolsCache(projectId)

            log.debug("User customized tool '$toolName': $category, $strategy")
            Result.success(updated)
        } catch (e: Exception) {
            log.warn("Failed to customize tool config: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * List available tools for a specific MCP server instance.
     *
     * @param projectId The project ID
     * @param instanceId The instance ID to get tools for
     * @return List of ToolSpecification
     * @throws IllegalArgumentException if instance is not found
     * @throws IllegalStateException if MCP client creation fails
     * @throws Exception if connection to MCP server fails (e.g., timeout)
     */
    suspend fun listTools(projectId: String, instanceId: String): List<ToolSpecification> {
        val instance = getInstance(projectId, instanceId)
            ?: throw IllegalArgumentException("Instance not found: $instanceId")

        // Use helper method to create MCP client
        val mcpClient = createMcpClient(instance, "tools_list_${projectId}_${instance.id}")
            ?: throw IllegalStateException("Failed to create MCP client for instance ${instance.name}")

        // List tools from MCP client
        log.debug("Fetching tools from MCP client for '${instance.name}'")
        val tools = mcpClient.listTools()

        log.debug("Successfully fetched ${tools.size} tools for instance '${instance.name}'")
        return tools
    }

    /**
     * Validate instance configuration without saving
     */
    fun validateInstance(
        serverId: String,
        parameterValues: Map<String, String>,
    ): Result<Unit> {
        return try {
            val definition = serversConfig.get(serverId)
                ?: return Result.failure(IllegalArgumentException("Server not found: $serverId"))

            val tempInstance = ProjectMcpInstance(
                id = "temp",
                projectId = "temp",
                serverId = serverId,
                name = "temp",
                parameterValues = parameterValues,
                enabled = true,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

            tempInstance.toConnector(definition)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Test connection for an instance
     */
    fun testConnection(projectId: String, instanceId: String): Result<Unit> {
        return try {
            val instance = getInstance(projectId, instanceId)
                ?: return Result.failure(IllegalArgumentException("Instance not found: $instanceId"))

            val definition = serversConfig.get(instance.serverId)
                ?: return Result.failure(IllegalStateException("Server definition not found: ${instance.serverId}"))

            val connector = instance.toConnector(definition)
            val testResult = connector.validate()

            if (testResult.isValid) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(testResult.errors.joinToString(", ")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete all instances for a project (called when project is deleted)
     */
    fun deleteAllInstances(projectId: String) {
        instancesConfig.deleteAll(projectId)
        ProjectMcpToolsConfig.deleteAll(projectId)
        log.debug("Deleted all MCP instances and tool configs for project $projectId")
    }

    /**
     * Batch save tools for a newly created instance (atomic operation).
     * Called after loading tools in AddMcpIntegrationDialog.
     */
    fun saveInstanceTools(
        projectId: String,
        instanceId: String,
        toolCategories: Map<String, ToolCategory>,
        toolStrategies: Map<String, Int>,
    ) {
        val allToolNames = (toolCategories.keys + toolStrategies.keys).toSet()

        val toolConfigs = allToolNames.map { toolName ->
            val category = toolCategories[toolName] ?: ToolCategory.OTHER
            val strategy = toolStrategies[toolName] ?: ToolStrategy.FOLLOW_UP_BASED

            ToolConfigData(
                toolName = toolName,
                instanceId = instanceId,
                category = category.name,
                strategy = strategy,
                autoInferred = false, // User reviewed these
                updatedAt = LocalDateTime.now(),
            )
        }

        ProjectMcpToolsConfig.updateInstanceTools(projectId, instanceId, toolConfigs)
        log.debug("Saved ${toolConfigs.size} tools for instance $instanceId in project $projectId")
    }
}
