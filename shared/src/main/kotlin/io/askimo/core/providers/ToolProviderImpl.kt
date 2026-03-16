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
import io.askimo.core.intent.ToolConfig
import io.askimo.core.intent.ToolRegistry
import io.askimo.core.intent.ToolSource
import io.askimo.core.logging.logger
import io.askimo.core.mcp.GlobalMcpInstanceService
import io.askimo.core.mcp.ProjectMcpInstanceService
import kotlinx.coroutines.runBlocking

/**
 * ThreadLocal storage for passing project context from ChatClient to ToolProvider.
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
     * Clear all thread-local state. Must be called in a finally block.
     */
    fun clear() {
        projectIdThreadLocal.remove()
    }
}

/**
 * Implementation of ToolProvider that dynamically provides tools based on project context.
 * Uses ThreadLocal to access the current project ID and retrieve MCP tools.
 *
 * Tool detection runs two layers in sequence:
 *  1. Keyword classifier  (IntentDetectionChain)      — fast & deterministic
 *  2. Vector similarity   (ToolVectorIndex / JVector)  — semantic, catches misses
 */
class ToolProviderImpl(
    private val projectMcpInstanceService: ProjectMcpInstanceService,
    private val globalMcpInstanceService: GlobalMcpInstanceService,
) : ToolProvider {

    private val log = logger<ToolProviderImpl>()

    override fun provideTools(request: ToolProviderRequest?): ToolProviderResult? {
        if (request == null) return null

        log.debug("Providing tools for request: {}", request)

        val projectId = ProjectContext.getProjectId()

        // Project-scoped tools — only when inside a project
        val projectTools: List<ToolConfig> = if (projectId != null) {
            runBlocking { projectMcpInstanceService.getProjectTools(projectId) }
        } else {
            emptyList()
        }

        // Global tools — always available in every chat
        val globalTools: List<ToolConfig> = runBlocking { globalMcpInstanceService.getGlobalTools() }

        // Merge: project tools take precedence over global tools with same name
        val projectToolNames = projectTools.map { it.specification.name() }.toSet()
        val mcpTools = projectTools + globalTools.filter { it.specification.name() !in projectToolNames }

        // Prefer project vector index when in project context, fall back to global
        val toolVectorIndex = if (projectId != null) {
            projectMcpInstanceService.getToolVectorIndex(projectId)
                ?: globalMcpInstanceService.getToolVectorIndex()
        } else {
            globalMcpInstanceService.getToolVectorIndex()
        }

        val userIntent = DetectUserIntentCommand.execute(
            userMessage = request.userMessage().singleText() ?: "",
            availableTools = ToolRegistry.getIntentBased(),
            mcpTools = mcpTools,
            toolVectorIndex = toolVectorIndex,
        )

        if (userIntent.tools.isEmpty()) return null

        val builder = ToolProviderResult.builder()

        userIntent.tools.forEach { tool ->
            if (tool.source == ToolSource.ASKIMO_BUILTIN) {
                val className = tool.specification.metadata()["className"]
                val methodName = tool.specification.metadata()["methodName"]
                if (className != null && methodName != null) {
                    try {
                        val clazz = Class.forName(className as String)
                        val kotlinClass = clazz.kotlin
                        val objectInstance = kotlinClass.objectInstance
                            ?: throw IllegalStateException("Class '$className' is not a Kotlin object")
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
                    log.warn(
                        "Missing className or methodName metadata for askimo tool: {}. " +
                            "Please check the tool again since all askimo must have both these attributes",
                        tool.specification.name(),
                    )
                }
            } else {
                val toolName = tool.specification.name()
                // Try project client first, fall back to global
                val mcpClient = if (projectId != null) {
                    projectMcpInstanceService.getMcpClientForTool(projectId, toolName)
                        ?: globalMcpInstanceService.getMcpClientForTool(toolName)
                } else {
                    globalMcpInstanceService.getMcpClientForTool(toolName)
                }

                if (mcpClient != null) {
                    builder.add(tool.specification, McpToolExecutor(mcpClient), ReturnBehavior.TO_LLM)
                } else {
                    log.error(
                        "Could not find MCP client for tool '{}' (projectId={}, global fallback attempted)",
                        toolName,
                        projectId,
                    )
                }
            }
        }

        return builder.build()
    }
}
