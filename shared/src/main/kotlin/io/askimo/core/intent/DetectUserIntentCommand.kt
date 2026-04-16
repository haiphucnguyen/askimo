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
        val keywordTools = keywordCategories.flatMap { category ->
            allTools.filter { it.category == category }
        }

        // ── Layer 2: vector similarity ────────────────────────────────────
        val keywordToolNames = keywordTools.map { it.specification.name() }.toSet()
        val vectorMatches = toolVectorIndex?.search(userMessage) ?: emptyList()
        val vectorOnlyTools = vectorMatches
            .filter { (tool, _) ->
                tool.specification.name() !in keywordToolNames &&
                    (tool.strategy and ToolStrategy.INTENT_BASED) != 0
            }
            .map { (tool, _) -> tool }

        // ── Layer 3: MCP fallback ─────────────────────────────────────────
        // If neither keyword nor vector detection produced any matches, but MCP tools are
        // available, include all of them as candidates and let the LLM decide which (if any)
        // to invoke. This mirrors how ChatGPT/Claude behave: they always send the full tool
        // list to the model and rely on the model's function-calling logic.
        val fallbackMcpTools = if (keywordTools.isEmpty() && vectorOnlyTools.isEmpty() && mcpTools.isNotEmpty()) {
            log.debug("No intent signal detected — falling back to all {} MCP tools", mcpTools.size)
            mcpTools.filter { (it.strategy and ToolStrategy.INTENT_BASED) != 0 }
        } else {
            emptyList()
        }

        val allMatched = (keywordTools + vectorOnlyTools + fallbackMcpTools)
            .distinctBy { it.specification.name() }

        // ── Confidence ────────────────────────────────────────────────────
        val topVectorScore = vectorMatches
            .filter { (tool, _) -> tool.specification.name() !in keywordToolNames }
            .maxOfOrNull { (_, score) -> score } ?: 0.0

        val confidence = when {
            keywordTools.isNotEmpty() && vectorOnlyTools.isNotEmpty() -> 90

            // both layers agree
            keywordTools.isNotEmpty() -> 85

            topVectorScore >= 0.80 -> 70

            // strong semantic signal
            vectorOnlyTools.isNotEmpty() -> 55

            // weak-to-moderate semantic signal
            fallbackMcpTools.isNotEmpty() -> 30

            else -> 0
        }

        val reasoning = buildString {
            if (allMatched.isEmpty()) {
                append("No specific tool intent detected")
            } else if (fallbackMcpTools.isNotEmpty() && keywordTools.isEmpty() && vectorOnlyTools.isEmpty()) {
                append("No intent signal detected — including all ${fallbackMcpTools.size} MCP tool(s) as fallback candidates")
            } else {
                append("Detected intent from user keywords: ")
                append(allMatched.joinToString { it.category.name })
                if (vectorOnlyTools.isNotEmpty()) {
                    append(" (${vectorOnlyTools.size} via vector search)")
                }
            }
        }

        log.debug(
            "Intent detection: keyword={}, vector={}, fallback={}, total={}, confidence={}",
            keywordTools.size,
            vectorOnlyTools.size,
            fallbackMcpTools.size,
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
