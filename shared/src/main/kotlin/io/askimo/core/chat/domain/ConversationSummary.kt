/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import java.time.LocalDateTime

data class ConversationSummary(
    val sessionId: String,
    val keyFacts: Map<String, String>,
    val mainTopics: List<String>,
    val recentContext: String,
    val lastSummarizedMessageId: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
