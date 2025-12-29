/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

/**
 * Thrown when the available context window is too small to fit a reasonable AI response.
 *
 * This is a non-transient error that indicates the user should:
 * - Switch to a model with a larger context window
 * - Clear conversation history
 * - Reduce the size of their input
 *
 * @property currentModel The current model identifier (e.g., "openai:gpt-3.5-turbo")
 * @property contextSize The total context window size in tokens
 * @property usedByMessages Tokens used by conversation messages and system prompts
 * @property availableForResponse Tokens available for the AI response
 * @property recommendedMinimum Recommended minimum tokens needed for quality responses
 */
class InsufficientContextException(
    val currentModel: String,
    val contextSize: Int,
    val usedByMessages: Int,
    val availableForResponse: Int,
    val recommendedMinimum: Int = 2048,
) : RuntimeException(
    buildMessage(currentModel, contextSize, usedByMessages, availableForResponse, recommendedMinimum),
) {
    companion object {
        private fun buildMessage(
            currentModel: String,
            contextSize: Int,
            usedByMessages: Int,
            availableForResponse: Int,
            recommendedMinimum: Int,
        ): String = """
            ⚠️  Insufficient context window space!

            Current model: $currentModel
            Context window: $contextSize tokens
            Used by messages: $usedByMessages tokens
            Available for response: $availableForResponse tokens
            Recommended minimum: $recommendedMinimum tokens

            The conversation history is too long for this model's context window.

            Recommended actions:
            1. Switch to a model with a larger context (e.g., GPT-4 with 128K, Claude 3.5 with 200K, or Gemini 1.5 Pro with 2M tokens)
            2. Clear conversation history to free up space
            3. Start a new conversation

            Models with larger context windows:
            - GPT-4 Turbo: 128,000 tokens
            - Claude 3.5 Sonnet: 200,000 tokens
            - Gemini 1.5 Pro: 2,000,000 tokens
        """.trimIndent()
    }
}
