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
 * Uses two complementary layers:
 *  1. Keyword classifier  — fast, deterministic, zero latency (IntentDetectionChain)
 *  2. Vector similarity   — semantic, catches intent the keyword chain misses (ToolVectorIndex)
 *
 * Results are unioned: keyword matches take priority, vector-only matches are appended.
 * Pinned tools (user-selected via @mention) bypass detection entirely.
 *
 * Note: Stage 1 of the 2-stage flow. Tools with FOLLOW_UP_BASED flag are handled
 * by DetectAiResponseIntentCommand (Stage 2). Tools with BOTH flags appear in both stages.
 */
object DetectUserIntentCommand {
    private val log = logger<DetectUserIntentCommand>()

    private val detectionChain = IntentDetectionChain()

    /**
     * Execute intent detection for the given user message.
     *
     * @param userMessage      The user's input message
     * @param availableTools   Built-in tools to consider (should have INTENT_BASED flag set)
     * @param mcpTools         Project-specific MCP tools
     * @param toolVectorIndex  Optional semantic vector index for MCP tools (built at tool-load time)
     * @return Intent detection result with matched tools
     */
    fun execute(
        userMessage: String,
        availableTools: List<ToolConfig>,
        mcpTools: List<ToolConfig> = emptyList(),
        toolVectorIndex: ToolVectorIndex? = null,
    ): IntentDetectionResult {
        val allTools = (availableTools + mcpTools).filter {
            (it.strategy and ToolStrategy.INTENT_BASED) != 0
        }

        // ── Layer 1: keyword classifier ───────────────────────────────────
        val keywordCategories = detectionChain.detectAll(userMessage)
        val keywordTools = keywordCategories.mapNotNull { category ->
            if (category == ToolCategory.EXECUTE) {
                allTools.filter { it.category == category }
            } else {
                listOfNotNull(allTools.find { it.category == category })
            }
        }.flatten()

        // ── Layer 2: vector similarity ────────────────────────────────────
        val keywordToolNames = keywordTools.map { it.specification.name() }.toSet()
        val vectorMatches = toolVectorIndex?.search(userMessage) ?: emptyList()
        // Only keep vector hits that the keyword layer didn't already find
        val vectorOnlyTools = vectorMatches
            .filter { (tool, _) -> tool.specification.name() !in keywordToolNames }
            .map { (tool, _) -> tool }

        val allMatched = (keywordTools + vectorOnlyTools)
            .distinctBy { it.specification.name() }

        // ── Confidence ────────────────────────────────────────────────────
        val confidence = when {
            keywordTools.isNotEmpty() -> 85
            vectorMatches.any { (_, score) -> score >= 0.80 } -> 70
            vectorOnlyTools.isNotEmpty() -> 55
            else -> 0
        }

        val reasoning = buildString {
            if (allMatched.isEmpty()) {
                append("No specific tool intent detected")
            } else {
                append("Detected intent from user keywords: ")
                append(allMatched.joinToString { it.category.name })
                if (vectorOnlyTools.isNotEmpty()) {
                    append(" (${vectorOnlyTools.size} via vector search)")
                }
            }
        }

        log.debug(
            "Intent detection: keyword={}, vector={}, total={}, confidence={}",
            keywordTools.size,
            vectorOnlyTools.size,
            allMatched.size,
            confidence,
        )

        return IntentDetectionResult(
            stage = IntentStage.USER_INPUT,
            tools = allMatched,
            confidence = confidence,
            reasoning = reasoning,
        )
    }
}
