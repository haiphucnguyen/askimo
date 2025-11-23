/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import java.time.LocalDateTime

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val directiveId: String? = null,
    val folderId: String? = null,
    val isStarred: Boolean = false,
    val sortOrder: Int = 0,
)

data class ChatFolder(
    val id: String,
    val name: String,
    val parentFolderId: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: LocalDateTime,
)

data class ConversationSummary(
    val sessionId: String,
    val keyFacts: Map<String, String>,
    val mainTopics: List<String>,
    val recentContext: String,
    val lastSummarizedMessageId: String,
    val createdAt: LocalDateTime,
)
