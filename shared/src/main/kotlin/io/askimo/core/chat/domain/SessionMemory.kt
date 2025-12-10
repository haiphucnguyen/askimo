/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import java.time.LocalDateTime

/**
 * Domain model for session memory persistence.
 * Stores the serialized state of TokenAwareSummarizingMemory for a chat session.
 *
 * @property sessionId Unique identifier for the chat session
 * @property memorySummary JSON serialized ConversationSummary (nullable)
 * @property memoryMessages JSON serialized List<ChatMessage> from LangChain4j
 * @property lastUpdated Timestamp of last memory update
 * @property createdAt Timestamp when memory was first created
 */
data class SessionMemory(
    val sessionId: String,
    val memorySummary: String?,
    val memoryMessages: String,
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
