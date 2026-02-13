/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.intent

import io.askimo.core.logging.logger

/**
 * Command for detecting user's intent from their input message.
 * Determines which tools with INTENT_BASED flag should be attached to the AI request.
 *
 * Uses pattern matching on user input to detect keywords indicating
 * specific tool requirements (e.g., "search", "read file", "create chart").
 *
 * Note: This handles Stage 1 of the 2-stage flow. Tools with FOLLOW_UP_BASED flag
 * are handled by DetectAiResponseIntentCommand (Stage 2).
 * Tools with BOTH flags are available in both stages.
 */
object DetectUserIntentCommand {
    private val log = logger<DetectUserIntentCommand>()

    /**
     * Execute the command to detect user intent.
     *
     * @param userMessage The user's input message
     * @param availableTools Tools to check for intent (should have INTENT_BASED flag set)
     * @param mcpTools Optional MCP tools to include in detection (project-specific)
     * @return Intent detection result with matched tools
     */
    fun execute(
        userMessage: String,
        availableTools: List<ToolConfig>,
        mcpTools: List<ToolConfig> = emptyList(),
    ): IntentDetectionResult {
        val lowerMessage = userMessage.lowercase()
        val matchedTools = mutableListOf<ToolConfig>()

        // Combine built-in and MCP tools, filter only those with INTENT_BASED flag
        val allTools = (availableTools + mcpTools).filter {
            (it.strategy and ToolStrategy.INTENT_BASED) != 0
        }

        // Pattern 1: Visualization intent
        if (detectVisualizationKeywords(lowerMessage)) {
            allTools.find { it.category == ToolCategory.VISUALIZE }
                ?.let { matchedTools.add(it) }
        }

        // Pattern 2: File operations
        if (detectFileOperationKeywords(lowerMessage)) {
            allTools.find { it.category == ToolCategory.FILE_WRITE }
                ?.let { matchedTools.add(it) }
        }

        // Pattern 3: Command execution (includes MCP tools)
        if (detectExecutionKeywords(lowerMessage)) {
            allTools.filter { it.category == ToolCategory.EXECUTE }
                .forEach { matchedTools.add(it) }
        }

        val result = IntentDetectionResult(
            stage = IntentStage.USER_INPUT,
            tools = matchedTools,
            confidence = if (matchedTools.isNotEmpty()) 85 else 0,
            reasoning = if (matchedTools.isNotEmpty()) {
                "Detected intent from user keywords: ${matchedTools.joinToString { it.category.name }}"
            } else {
                "No specific tool intent detected"
            },
        )

        if (matchedTools.isNotEmpty()) {
            log.debug(
                "Detected user intent: {} tools matched with confidence {}",
                matchedTools.size,
                result.confidence,
            )
        }

        return result
    }

    private fun detectVisualizationKeywords(text: String): Boolean {
        val keywords = listOf(
            "chart", "graph", "plot", "visualize", "visualization",
            "diagram", "show me", "display", "draw", "create a chart",
            "generate chart", "make a graph",
        )
        return keywords.any { text.contains(it) }
    }

    private fun detectFileOperationKeywords(text: String): Boolean {
        val keywords = listOf(
            "create file", "write file", "save to file", "save as",
            "generate file", "make a file", "write to disk",
            "save this", "write this",
        )
        return keywords.any { text.contains(it) }
    }

    private fun detectExecutionKeywords(text: String): Boolean {
        val keywords = listOf(
            "run", "execute", "install", "build", "compile", "test",
            "run the", "execute this", "install package",
        )
        return keywords.any { text.contains(it) }
    }
}
