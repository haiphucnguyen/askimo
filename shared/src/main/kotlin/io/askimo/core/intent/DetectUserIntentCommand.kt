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
/**
 * Command for detecting user's intent using Chain of Responsibility pattern.
 */
object DetectUserIntentCommand {
    private val log = logger<DetectUserIntentCommand>()

    // Chain of Responsibility for intent detection
    private val detectionChain = IntentDetectionChain()

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
        // Combine built-in and MCP tools, filter only those with INTENT_BASED flag
        val allTools = (availableTools + mcpTools).filter {
            (it.strategy and ToolStrategy.INTENT_BASED) != 0
        }

        // Use Chain of Responsibility to detect all matching categories
        val matchedCategories = detectionChain.detectAll(userMessage)

        // Map categories to actual tools
        val matchedTools = matchedCategories.mapNotNull { category ->
            // For EXECUTE category, we want all matching tools (including MCP tools)
            if (category == ToolCategory.EXECUTE) {
                allTools.filter { it.category == category }
            } else {
                listOfNotNull(allTools.find { it.category == category })
            }
        }.flatten()

        // Calculate confidence
        val confidence = if (matchedTools.isNotEmpty()) 85 else 0

        val reasoning = if (matchedTools.isNotEmpty()) {
            "Detected intent from user keywords: ${matchedTools.joinToString { it.category.name }}"
        } else {
            "No specific tool intent detected"
        }

        log.debug("Intent detection: message='$userMessage', matched=${matchedTools.size} tools, confidence=$confidence")

        return IntentDetectionResult(
            stage = IntentStage.USER_INPUT,
            tools = matchedTools,
            confidence = confidence,
            reasoning = reasoning,
        )
    }
}
