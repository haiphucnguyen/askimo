/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.agent.tool.ReturnBehavior
import dev.langchain4j.mcp.McpToolExecutor
import dev.langchain4j.service.tool.DefaultToolExecutor
import dev.langchain4j.service.tool.ToolProvider
import dev.langchain4j.service.tool.ToolProviderRequest
import dev.langchain4j.service.tool.ToolProviderResult
import io.askimo.core.intent.DetectUserIntentCommand
import io.askimo.core.intent.ToolRegistry
import io.askimo.core.intent.ToolSource
import io.askimo.core.logging.logger
import io.askimo.core.mcp.ProjectMcpInstanceService
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.get

/**
 * ThreadLocal storage for passing project context from ChatClient to ToolProvider.
 * This allows ToolProviderImpl to access the current project ID without explicit parameter passing.
 */
object ProjectContext {
    private val projectIdThreadLocal = ThreadLocal<String?>()

    /**
     * Set the project ID for the current thread.
     * Should be called before invoking the AI model.
     */
    fun setProjectId(projectId: String?) {
        projectIdThreadLocal.set(projectId)
    }

    /**
     * Get the project ID for the current thread.
     */
    fun getProjectId(): String? = projectIdThreadLocal.get()

    /**
     * Clear the project ID from the current thread.
     * Should always be called in a finally block to prevent memory leaks.
     */
    fun clear() {
        projectIdThreadLocal.remove()
    }
}

/**
 * Implementation of ToolProvider that dynamically provides tools based on project context.
 * Uses ThreadLocal to access the current project ID and retrieve MCP tools.
 */
class ToolProviderImpl(
    private val projectMcpInstanceService: ProjectMcpInstanceService,
) : ToolProvider {

    private val log = logger<ToolProviderImpl>()

    override fun provideTools(request: ToolProviderRequest?): ToolProviderResult? {
        if (request != null) {
            log.debug("Providing tools for request: {}", request)

            // Get project ID from ThreadLocal
            val projectId = ProjectContext.getProjectId()

            // Analyze user message to determine which tools should be made available
            val availableTools = if (projectId != null) {
                runBlocking {
                    val mcpTools = projectMcpInstanceService.getProjectTools(projectId)
                    ToolRegistry.getIntentBased() + mcpTools
                }
            } else {
                ToolRegistry.getIntentBased()
            }

            val userIntent = DetectUserIntentCommand.execute(
                request.userMessage().singleText() ?: "",
                availableTools = availableTools,
            )
            if (userIntent.tools.isNotEmpty()) {
                val builder = ToolProviderResult.builder()
                userIntent.tools.forEach { tool ->
                    if (tool.source == ToolSource.ASKIMO_BUILTIN) {
                        val className = tool.specification.metadata()["className"]
                        val methodName = tool.specification.metadata()["methodName"]
                        if (className != null && methodName != null) {
                            try {
                                // Load the class and get the Kotlin object instance
                                val clazz = Class.forName(className as String)
                                val kotlinClass = clazz.kotlin
                                val objectInstance = kotlinClass.objectInstance
                                    ?: throw IllegalStateException("Class '$className' is not a Kotlin object")

                                // Get the method - we need to find it by name since we don't have parameter types
                                // We can pass the parameter types as metadata in the future if needed for more accuracy
                                val toolMethod = clazz.methods.find { it.name == methodName }
                                    ?: throw NoSuchMethodException("Method '$methodName' not found in class '$className'")

                                builder.add(
                                    tool.specification,
                                    DefaultToolExecutor.builder()
                                        .`object`(objectInstance)
                                        .methodToInvoke(toolMethod)
                                        .originalMethod(toolMethod)
                                        .wrapToolArgumentsExceptions(true)
                                        .propagateToolExecutionExceptions(true)
                                        .build(),
                                )
                            } catch (e: ClassNotFoundException) {
                                log.error("Class '{}' not found", className, e)
                            } catch (e: NoSuchMethodException) {
                                log.error("Method '{}' not found in class '{}'", methodName, className, e)
                            } catch (e: Exception) {
                                log.error("Error creating tool provider for {}.{}", className, methodName, e)
                            }
                        } else {
                            log.warn("Missing className or methodName metadata for askimo tool: {}. Please check the tool again since all askimo must have both these attributes", tool.specification.name())
                        }
                    } else {
                        if (projectId != null) { // mcp tools should only be added if we have a valid project context, but we check again just in case
                            val mcpClient = projectMcpInstanceService.getMcpClientForTool(projectId, tool.specification.name())
                            if (mcpClient != null) {
                                builder.add(tool.specification, McpToolExecutor(mcpClient), ReturnBehavior.TO_LLM)
                            } else {
                                log.error("Could not find mcp client for tool {} in project {}. Perhaps the caching issue?", tool.specification.name(), projectId)
                            }
                        }
                    }
                }
                return builder.build()
            }
        }

        return null
    }
}
