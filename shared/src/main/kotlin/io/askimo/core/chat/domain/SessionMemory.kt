/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
 */
package io.askimo.core.chat.domain

import java.time.Instant

/**
 * Domain model for session memory persistence.
 * Stores the serialized state of TokenAwareSummarizingMemory for a chat session.
 *
 * @property sessionId Unique identifier for the chat session
 * @property memorySummary JSON serialized SessionConversationSummary (nullable)
 * @property memoryMessages JSON serialized List<ChatMessage> from LangChain4j
 * @property lastUpdated Timestamp of last memory update
 * @property createdAt Timestamp when memory was first created
 */
data class SessionMemory(
    val sessionId: String,
    val memorySummary: String?,
    val memoryMessages: String,
    val lastUpdated: Instant = Instant.now(),
    val createdAt: Instant = Instant.now(),
)
