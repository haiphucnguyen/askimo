/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import io.askimo.core.session.ChatMessage

/**
 * Utility for counting tokens in text and messages.
 * Uses the tiktoken library for accurate token counting.
 */
object TokenCounter {
    private val registry = Encodings.newDefaultEncodingRegistry()

    // Use CL100K_BASE encoding (used by GPT-3.5-turbo and GPT-4)
    private val encoding: Encoding = registry.getEncoding(EncodingType.CL100K_BASE)

    /**
     * Count exact tokens in a text string.
     *
     * @param text The text to count tokens for
     * @return The number of tokens
     */
    fun countTokens(text: String): Int = try {
        encoding.countTokens(text)
    } catch (e: Exception) {
        // Fallback to rough estimation if tokenizer fails
        (text.length / 4).coerceAtLeast(1)
    }

    /**
     * Count total tokens used in a list of chat messages.
     * Includes both message content and formatting overhead.
     *
     * @param messages The list of chat messages
     * @return The total number of tokens
     */
    fun countChatTokens(messages: List<ChatMessage>): Int {
        var totalTokens = 0

        messages.forEach { message ->
            try {
                // Count tokens in the message content
                totalTokens += encoding.countTokens(message.content)

                // Add overhead for message structure
                // OpenAI format includes: role, separators, and formatting tokens
                totalTokens += 4 // Approximate overhead per message
            } catch (e: Exception) {
                // Fallback estimation
                totalTokens += (message.content.length / 4).coerceAtLeast(1) + 4
            }
        }

        return totalTokens
    }

    /**
     * Format token count for human-readable display.
     *
     * @param tokens The number of tokens
     * @return Formatted string (e.g., "1.2K tokens", "500 tokens")
     */
    fun formatTokenCount(tokens: Int): String = when {
        tokens < 1_000 -> "$tokens tokens"
        tokens < 1_000_000 -> {
            val rounded = (tokens / 100.0).toInt() / 10.0
            "${rounded}K tokens"
        }
        else -> {
            val rounded = (tokens / 100_000.0).toInt() / 10.0
            "${rounded}M tokens"
        }
    }

    /**
     * Get detailed token information for a chat session.
     *
     * @param messages The messages in the chat session
     * @return TokenInfo containing counts and formatted display
     */
    data class TokenInfo(
        val totalTokens: Int,
        val messageCount: Int,
        val formattedCount: String,
    )

    fun getTokenInfo(messages: List<ChatMessage>): TokenInfo {
        val totalTokens = countChatTokens(messages)
        return TokenInfo(
            totalTokens = totalTokens,
            messageCount = messages.size,
            formattedCount = formatTokenCount(totalTokens),
        )
    }
}
